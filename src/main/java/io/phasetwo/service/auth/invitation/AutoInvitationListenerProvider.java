package io.phasetwo.service.auth.invitation;

import io.phasetwo.service.model.InvitationModel;
import io.phasetwo.service.model.OrganizationProvider;
import io.phasetwo.service.model.OrganizationRoleModel;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.*;

@JBossLog
public class AutoInvitationListenerProvider implements EventListenerProvider {
    KeycloakSession _session;
    public AutoInvitationListenerProvider(KeycloakSession keycloakSession){
        _session = keycloakSession;
    }
    @Override
    public void onEvent(Event event) {

        if(EventType.LOGIN.equals(event.getType())){
            RealmProvider realmProvider =  _session.getProvider(RealmProvider.class);
            UserProvider userProvider=  _session.getProvider(UserProvider.class);
            RealmModel realm = realmProvider.getRealm(event.getRealmId());
            UserModel user =  userProvider.getUserById(realm, event.getUserId());
            log.infof(
                    "Silent invitation add called for realm %s and user %s",
                    realm.getName(), user.getEmail());


            OrganizationProvider orgs = _session.getProvider(OrganizationProvider.class);

            long invitationCount = orgs.getUserInvitationsStream(realm, user).count();
            log.infof("Found %d invitations for user %s", invitationCount, user.getEmail());

            orgs.getUserInvitationsStream(realm, user)
                    .forEach(
                            i -> {
                                    // add membership
                                    log.infof("Adding user silently from invitation %s", i.getOrganization().getId());
                                    memberFromInvitation(i, user);
                                     i.getOrganization().revokeInvitation(i.getId());

                            });

            log.infof("Removing 'invitation-required-action' from the user...");
            // Remove leftover invitation required action if present
            user.getRequiredActionsStream()
                .filter(a -> a.equals("invitation-required-action"))
                .findFirst()
                .ifPresent(a -> {
                    log.infof("Removing invitation-required-action from user %s", user.getUsername());
                    user.removeRequiredAction(a);
                });

        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean b) {

    }

    @Override
    public void close() {

    }
    void memberFromInvitation(InvitationModel invitation, UserModel user) {
        // membership
        invitation.getOrganization().grantMembership(user);
        invitation.getRoles().stream()
                .forEach(
                        r -> {
                            OrganizationRoleModel role = invitation.getOrganization().getRoleByName(r);
                            if (role == null) {
                                log.debugf("No org role found for invitation role %s. Skipping...", r);
                            } else {
                                role.grantRole(user);
                            }
                        });

    }
}
