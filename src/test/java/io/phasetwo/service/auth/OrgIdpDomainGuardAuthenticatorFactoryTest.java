package io.phasetwo.service.auth;

import static io.phasetwo.service.Orgs.ORG_OWNER_CONFIG_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.net.InternetDomainName;
import io.phasetwo.service.model.OrganizationModel;
import io.phasetwo.service.model.OrganizationProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.events.EventBuilder;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.mockito.MockedStatic;

/**
 * An organization's IdP may only speak for that organization's verified domains — the rule that
 * keeps one workspace's tenant from signing in as, or claiming the invitations of, anybody else.
 */
class OrgIdpDomainGuardAuthenticatorFactoryTest {

  private static final String ORG = "7d3a2f1e-4b5c-4d6e-8f90-1a2b3c4d5e6f";
  private static final String OTHER_ORG = "aaaaaaaa-0000-0000-0000-000000000001";

  private final OrganizationProvider orgs = mock(OrganizationProvider.class);
  private final RealmModel realm = mock(RealmModel.class);

  // ── The rule ────────────────────────────────────────────────────────────

  @Test
  void an_idp_no_organization_owns_is_not_guarded() {
    assertThat(
        OrgIdpDomainGuardAuthenticatorFactory.refusal(
            orgs, realm, idp(null), "anyone@anywhere.example", false),
        nullValue());
    verifyNoInteractions(orgs);
  }

  @Test
  void an_address_on_a_domain_the_owning_organization_verified_passes() {
    verifiedBy("contoso.com", ORG);

    assertThat(refusal("john@contoso.com"), nullValue());
  }

  @Test
  void the_domain_is_compared_in_lowercase() {
    verifiedBy("contoso.com", ORG);

    assertThat(refusal(" John@Contoso.COM "), nullValue());
  }

  @Test
  void an_address_on_a_domain_only_another_organization_verified_is_refused() {
    verifiedBy("victim.example", OTHER_ORG);

    assertThat(refusal("ceo@victim.example"), containsString("not a verified domain"));
  }

  @Test
  void an_address_on_a_domain_nobody_verified_is_refused() {
    when(orgs.getOrganizationsStreamForDomain(realm, "contoso.com", true))
        .thenReturn(Stream.empty());

    assertThat(refusal("john@contoso.com"), containsString("not a verified domain"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "no-at-sign", "@contoso.com", "john@"})
  void a_missing_or_malformed_address_is_refused(String email) {
    assertThat(refusal(email), containsString("did not provide an e-mail address"));
  }

  @Test
  void an_address_typed_into_the_review_form_is_refused_even_on_a_verified_domain() {
    verifiedBy("contoso.com", ORG);

    assertThat(
        OrgIdpDomainGuardAuthenticatorFactory.refusal(
            orgs, realm, idp(ORG), "ceo@contoso.com", true),
        containsString("typed in"));
  }

  @Test
  void a_domain_keycloak_cannot_parse_is_refused() {
    when(orgs.getOrganizationsStreamForDomain(realm, "not_a_domain%", true))
        .thenThrow(new IllegalArgumentException("not a valid domain name"));

    assertThat(refusal("x@not_a_domain%"), containsString("not a verified domain"));
  }

  @Test
  void a_switched_off_identity_provider_is_refused_before_anything_is_looked_up() {
    // Reachable through its broker login URL even when disabled; Keycloak's own refusal in the
    // first-broker-login steps is overwritten by their success. Nothing may be created or linked.
    IdentityProviderModel disabled = idp(ORG);
    disabled.setEnabled(false);

    assertThat(
        OrgIdpDomainGuardAuthenticatorFactory.refusal(
            orgs, realm, disabled, "john@contoso.com", false),
        containsString("disabled"));
    verifyNoInteractions(orgs);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"john@contoso.com.", "john@contoso。com", "john@contoso．com", "john@CONTOSO.COM."})
  void a_domain_that_only_matches_after_normalisation_is_refused(String email) {
    // The lookup would normalise a trailing dot or another dot character to "contoso.com" and match,
    // but the account would be stored with the address as sent. Checked and stored must be the same.
    verifiedByAfterNormalisation("contoso.com", ORG);

    assertThat(refusal(email), containsString("not a verified domain"));
  }

  // ── The flow step ───────────────────────────────────────────────────────

  @Test
  void a_login_on_a_verified_domain_continues() {
    verifiedBy("contoso.com", ORG);
    AuthenticationFlowContext context = context(false);

    try (MockedStatic<PostOrgAuthFlow> flow = mockStatic(PostOrgAuthFlow.class)) {
      flow.when(() -> PostOrgAuthFlow.getBrokeredIdentityContext(context))
          .thenReturn(broker("john@contoso.com"));

      new OrgIdpDomainGuardAuthenticatorFactory().authenticate(context);
    }

    verify(context).success();
    verify(context, never()).failureChallenge(any(), any());
  }

  @Test
  void a_login_for_somebody_elses_address_stops_on_an_error_page() {
    verifiedBy("victim.example", OTHER_ORG);
    AuthenticationFlowContext context = context(false);
    LoginFormsProvider form = context.form();

    try (MockedStatic<PostOrgAuthFlow> flow = mockStatic(PostOrgAuthFlow.class)) {
      flow.when(() -> PostOrgAuthFlow.getBrokeredIdentityContext(context))
          .thenReturn(broker("ceo@victim.example"));

      new OrgIdpDomainGuardAuthenticatorFactory().authenticate(context);
    }

    verify(context, never()).success();
    verify(form).setError(OrgIdpDomainGuardAuthenticatorFactory.NOT_ALLOWED_MESSAGE);
    verify(context).failureChallenge(eq(AuthenticationFlowError.ACCESS_DENIED), any());
  }

  @Test
  void a_login_whose_address_was_typed_in_stops_on_an_error_page() {
    verifiedBy("contoso.com", ORG);
    AuthenticationFlowContext context = context(true);

    try (MockedStatic<PostOrgAuthFlow> flow = mockStatic(PostOrgAuthFlow.class)) {
      flow.when(() -> PostOrgAuthFlow.getBrokeredIdentityContext(context))
          .thenReturn(broker("ceo@contoso.com"));

      new OrgIdpDomainGuardAuthenticatorFactory().authenticate(context);
    }

    verify(context, never()).success();
    verify(context).failureChallenge(eq(AuthenticationFlowError.ACCESS_DENIED), any());
  }

  @Test
  void a_login_through_a_switched_off_identity_provider_stops_with_its_own_message() {
    verifiedBy("contoso.com", ORG);
    AuthenticationFlowContext context = context(false);
    LoginFormsProvider form = context.form();
    BrokeredIdentityContext switchedOff = broker("john@contoso.com");
    switchedOff.getIdpConfig().setEnabled(false);

    try (MockedStatic<PostOrgAuthFlow> flow = mockStatic(PostOrgAuthFlow.class)) {
      flow.when(() -> PostOrgAuthFlow.getBrokeredIdentityContext(context)).thenReturn(switchedOff);

      new OrgIdpDomainGuardAuthenticatorFactory().authenticate(context);
    }

    verify(context, never()).success();
    verify(form).setError(OrgIdpDomainGuardAuthenticatorFactory.DISABLED_MESSAGE);
    verify(context).failureChallenge(eq(AuthenticationFlowError.ACCESS_DENIED), any());
  }

  @Test
  void added_to_a_flow_without_an_idp_login_it_lets_ordinary_logins_through() {
    AuthenticationFlowContext context = context(false);

    try (MockedStatic<PostOrgAuthFlow> flow = mockStatic(PostOrgAuthFlow.class)) {
      flow.when(() -> PostOrgAuthFlow.getBrokeredIdentityContext(context))
          .thenThrow(
              new AuthenticationFlowException(
                  "Not found serialized context in clientSession",
                  AuthenticationFlowError.IDENTITY_PROVIDER_ERROR));

      new OrgIdpDomainGuardAuthenticatorFactory().authenticate(context);
    }

    verify(context).success();
    verify(context, never()).failureChallenge(any(), any());
    verifyNoInteractions(orgs);
  }

  // ── Fixtures ────────────────────────────────────────────────────────────

  private String refusal(String email) {
    return OrgIdpDomainGuardAuthenticatorFactory.refusal(orgs, realm, idp(ORG), email, false);
  }

  private void verifiedBy(String domain, String orgId) {
    OrganizationModel org = mock(OrganizationModel.class);
    when(org.getId()).thenReturn(orgId);
    when(orgs.getOrganizationsStreamForDomain(realm, domain, true))
        .thenAnswer(invocation -> Stream.of(org));
  }

  /**
   * Answers like the JPA provider does: it runs what it is given through {@link InternetDomainName}
   * (lower case, look-alike dots to '.', trailing dot dropped) before matching the stored domain.
   */
  private void verifiedByAfterNormalisation(String domain, String orgId) {
    OrganizationModel org = mock(OrganizationModel.class);
    when(org.getId()).thenReturn(orgId);
    when(orgs.getOrganizationsStreamForDomain(
            eq(realm),
            argThat(
                (String given) -> {
                  try {
                    return given != null && InternetDomainName.from(given).toString().equals(domain);
                  } catch (IllegalArgumentException notADomain) {
                    return false;
                  }
                }),
            eq(true)))
        .thenAnswer(invocation -> Stream.of(org));
  }

  private static IdentityProviderModel idp(String owningOrgId) {
    IdentityProviderModel idp = new IdentityProviderModel();
    idp.setAlias("org-" + (owningOrgId == null ? "none" : owningOrgId));
    idp.setEnabled(true);
    Map<String, String> config = new HashMap<>();
    if (owningOrgId != null) config.put(ORG_OWNER_CONFIG_KEY, owningOrgId);
    idp.setConfig(config);
    return idp;
  }

  private static BrokeredIdentityContext broker(String email) {
    BrokeredIdentityContext broker = new BrokeredIdentityContext("entra-subject");
    broker.setIdpConfig(idp(ORG));
    broker.setEmail(email);
    return broker;
  }

  private AuthenticationFlowContext context(boolean emailTypedIn) {
    AuthenticationFlowContext context = mock(AuthenticationFlowContext.class);
    KeycloakSession session = mock(KeycloakSession.class);
    AuthenticationSessionModel authSession = mock(AuthenticationSessionModel.class);
    when(context.getSession()).thenReturn(session);
    when(session.getProvider(OrganizationProvider.class)).thenReturn(orgs);
    when(context.getRealm()).thenReturn(realm);
    when(context.getAuthenticationSession()).thenReturn(authSession);
    when(authSession.getAuthNote(AbstractIdpAuthenticator.UPDATE_PROFILE_EMAIL_CHANGED))
        .thenReturn(emailTypedIn ? "true" : null);
    EventBuilder event = mock(EventBuilder.class, RETURNS_SELF);
    when(context.getEvent()).thenReturn(event);
    LoginFormsProvider form = mock(LoginFormsProvider.class, RETURNS_SELF);
    when(context.form()).thenReturn(form);
    return context;
  }
}
