package io.phasetwo.service.auth;

import static io.phasetwo.service.Orgs.ORG_OWNER_CONFIG_KEY;

import com.google.auto.service.AutoService;
import com.google.common.net.InternetDomainName;
import io.phasetwo.service.model.OrganizationProvider;
import jakarta.ws.rs.core.Response;
import java.util.Locale;
import java.util.Map;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

/**
 * Lets an organization's identity provider speak only for its own verified e-mail domains.
 *
 * <p>An organization IdP is configured by that organization's administrator, and an IdP can put any
 * e-mail address into its tokens. Without this step a brokered identity asserting somebody else's
 * address would be created or linked as that person — and, through the address, would receive that
 * person's pending invitations in every organization. This step lets the login continue only when
 * the IdP is enabled, the address came from the IdP (it was not typed into the profile-review form)
 * and its domain is a verified domain of the organization that owns the IdP. IdPs no organization
 * owns pass untouched.
 *
 * <p>Use it in the first-broker-login flow of organization IdPs, REQUIRED, after "Review Profile" and
 * before "User creation or linking". Anywhere else, where no IdP login is being finished, it does
 * nothing.
 */
@JBossLog
@AutoService(AuthenticatorFactory.class)
public class OrgIdpDomainGuardAuthenticatorFactory extends BaseAuthenticatorFactory
    implements DefaultAuthenticator {

  public static final String PROVIDER_ID = "ext-auth-org-idp-domain-guard";

  static final String NOT_ALLOWED_MESSAGE =
      "Your organization's single sign-on can only be used with an e-mail address on a domain your"
          + " XOAP workspace has verified. Ask the workspace owner to verify your domain, or sign in"
          + " with your XOAP password.";

  static final String DISABLED_MESSAGE =
      "Your organization's single sign-on is switched off at the moment. Ask the workspace owner to"
          + " enable it, or sign in with your XOAP password.";

  static final String DISABLED_REASON = "the identity provider is disabled";

  private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
    AuthenticationExecutionModel.Requirement.REQUIRED,
    AuthenticationExecutionModel.Requirement.DISABLED
  };

  public OrgIdpDomainGuardAuthenticatorFactory() {
    super(PROVIDER_ID);
  }

  @Override
  public void authenticate(AuthenticationFlowContext context) {
    BrokeredIdentityContext brokerContext;
    try {
      brokerContext = PostOrgAuthFlow.getBrokeredIdentityContext(context);
    } catch (AuthenticationFlowException notABrokeredLogin) {
      // Only meaningful where an IdP login is being finished. Added to any other flow by mistake
      // (the browser flow, say), it must not break ordinary logins: it lets them through.
      log.warnf("%s is not in a broker login flow; it does nothing there", PROVIDER_ID);
      context.success();
      return;
    }
    IdentityProviderModel idp = brokerContext.getIdpConfig();
    boolean typedIn =
        Boolean.parseBoolean(
            context
                .getAuthenticationSession()
                .getAuthNote(AbstractIdpAuthenticator.UPDATE_PROFILE_EMAIL_CHANGED));

    String refusal =
        refusal(
            context.getSession().getProvider(OrganizationProvider.class),
            context.getRealm(),
            idp,
            brokerContext.getEmail(),
            typedIn);
    if (refusal == null) {
      context.success();
      return;
    }

    log.infof("Refusing brokered login through %s: %s", idp.getAlias(), refusal);
    context
        .getEvent()
        .detail(Details.IDENTITY_PROVIDER, idp.getAlias())
        .detail(Details.EMAIL, brokerContext.getEmail())
        .detail("reason", refusal)
        .error(Errors.ACCESS_DENIED);
    String message = DISABLED_REASON.equals(refusal) ? DISABLED_MESSAGE : NOT_ALLOWED_MESSAGE;
    Response challenge =
        context.form().setError(message).createErrorPage(Response.Status.FORBIDDEN);
    context.failureChallenge(AuthenticationFlowError.ACCESS_DENIED, challenge);
  }

  /**
   * Why the brokered identity may not continue, or {@code null} when it may.
   *
   * @param typedIn the address was entered in the profile-review form instead of coming from the IdP
   */
  static String refusal(
      OrganizationProvider orgs,
      RealmModel realm,
      IdentityProviderModel idp,
      String email,
      boolean typedIn) {
    Map<String, String> config = idp == null ? null : idp.getConfig();
    String orgId = config == null ? null : config.get(ORG_OWNER_CONFIG_KEY);
    if (orgId == null || orgId.isBlank()) return null;

    // A disabled IdP is still reachable through its broker login URL (only kc_idp_hint and Home IdP
    // Discovery skip disabled providers), and Keycloak's own refusal of a disabled IdP in the
    // first-broker-login steps is overwritten when the step then reports success (Review Profile with
    // the profile page off, Create User If Unique). A connection the workspace switched off must not
    // create or link accounts.
    if (!idp.isEnabled()) return DISABLED_REASON;

    if (typedIn) return "the e-mail address was typed in, not provided by the identity provider";

    String domain = domainOf(email);
    if (domain == null) return "the identity provider did not provide an e-mail address";

    boolean verifiedForThisOrganization;
    try {
      // The organization lookup normalises what it is given (other dot characters, a trailing dot),
      // while the account would keep the address exactly as the IdP sent it. Only a domain that
      // already is its own canonical form may match, so what was checked is what gets stored. Then
      // the exact, verified-only match Home IdP Discovery routes by, so the answer cannot drift from
      // where the realm sends people.
      verifiedForThisOrganization =
          domain.equals(InternetDomainName.from(domain).toString())
              && orgs.getOrganizationsStreamForDomain(realm, domain, true)
                  .anyMatch(org -> orgId.equals(org.getId()));
    } catch (IllegalArgumentException notADomain) {
      verifiedForThisOrganization = false;
    }

    return verifiedForThisOrganization
        ? null
        : String.format(
            "the e-mail domain %s is not a verified domain of organization %s", domain, orgId);
  }

  static String domainOf(String email) {
    if (email == null) return null;
    String trimmed = email.trim();
    int at = trimmed.lastIndexOf('@');
    if (at <= 0 || at == trimmed.length() - 1) return null;
    return trimmed.substring(at + 1).toLowerCase(Locale.ROOT);
  }

  @Override
  public void action(AuthenticationFlowContext context) {}

  @Override
  public boolean isUserSetupAllowed() {
    return false;
  }

  @Override
  public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
    return REQUIREMENT_CHOICES;
  }

  @Override
  public Authenticator create(KeycloakSession session) {
    return this;
  }

  @Override
  public String getDisplayType() {
    return "Organization IdP Domain Guard";
  }

  @Override
  public String getReferenceCategory() {
    return "Broker";
  }

  @Override
  public String getHelpText() {
    return "Continues a first broker login through an organization-owned IdP only when the IdP is"
        + " enabled, the e-mail came from the IdP and its domain is verified by that organization."
        + " Place after Review Profile, before user creation or linking.";
  }
}
