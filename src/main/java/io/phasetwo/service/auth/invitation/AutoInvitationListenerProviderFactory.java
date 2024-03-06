package io.phasetwo.service.auth.invitation;

import com.google.auto.service.AutoService;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

@AutoService(EventListenerProviderFactory.class)

public class AutoInvitationListenerProviderFactory implements EventListenerProviderFactory {

    @Override
    public AutoInvitationListenerProvider create(KeycloakSession keycloakSession) {
        return new AutoInvitationListenerProvider(keycloakSession);
    }

    @Override
    public void init(Config.Scope scope) {
        // NOOP
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
        // NOOP
    }

    @Override
    public void close() {
        // NOOP
    }

    @Override
    public String getId() {
        return "invitation_auto_add";
    }

}

