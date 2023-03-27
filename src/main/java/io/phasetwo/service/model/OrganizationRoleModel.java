package io.phasetwo.service.model;

import java.util.stream.Stream;
import org.keycloak.models.UserModel;

public interface OrganizationRoleModel {

  String getId();

  String getName();

  void setName(String name);

  String getDescription();

  void setDescription(String description);

  Stream<UserModel> getUserMappingsStream();

  void grantRole(UserModel user);

  void revokeRole(UserModel user);

  void grantRole(OrganizationGroupModel group);

  void revokeRole(OrganizationGroupModel group);

  boolean hasRole(OrganizationGroupModel group);

  /** if user has direct or indirect (by group) association to role */
  boolean hasRole(UserModel user);

  /** if user has direct association to role */
  boolean hasDirectRole(UserModel user);
}
