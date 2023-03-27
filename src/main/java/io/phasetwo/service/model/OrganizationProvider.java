package io.phasetwo.service.model;

import java.util.stream.Stream;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.Provider;

public interface OrganizationProvider extends Provider {

  OrganizationModel createOrganization(
      RealmModel realm, String name, UserModel createdBy, boolean admin);

  OrganizationModel getOrganizationById(RealmModel realm, String id);

  Stream<OrganizationModel> getOrganizationsStream(
      RealmModel realm, Integer firstResult, Integer maxResults);

  default Stream<OrganizationModel> getOrganizationsStream(RealmModel realm) {
    return getOrganizationsStream(realm, null, null);
  }

  Stream<OrganizationModel> getOrganizationsStreamForDomain(
      RealmModel realm, String domain, boolean verified);

  Stream<OrganizationModel> searchForOrganizationByNameStream(
      RealmModel realm, String search, Integer firstResult, Integer maxResults);

  Stream<OrganizationModel> getUserOrganizationsStream(RealmModel realm, UserModel user);

  boolean removeOrganization(RealmModel realm, String id);

  void removeOrganizations(RealmModel realm);

  Stream<InvitationModel> getUserInvitationsStream(RealmModel realm, UserModel user);

  Stream<OrganizationRoleModel> getRolesStream();

  default OrganizationRoleModel getRoleByName(String name) {
    return getRolesStream()
        .filter(r -> name.equals(r.getName()))
        .collect(MoreCollectors.toOptional())
        .orElse(null);
  }

  void removeRole(String name);

  OrganizationRoleModel addRole(String name);

  Stream<OrganizationGroupModel> getGroupsStream();

  default OrganizationGroupModel getGroupById(String groupId) {
    return getGroupsStream().filter(r -> groupId.equals(r.getId())).findFirst().orElse(null);
  }

  void removeGroup(String groupId);

  void moveGroup(OrganizationGroupModel child, OrganizationGroupModel parent);

  OrganizationGroupModel addGroup(String groupName, OrganizationGroupModel parent);

}
