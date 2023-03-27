package io.phasetwo.service.model;

import io.phasetwo.service.model.jpa.entity.OrganizationGroupEntity;
import java.util.stream.Stream;
import org.keycloak.models.UserModel;

public interface OrganizationGroupModel extends WithAttributesModel {

  String getId();

  String getName();

  String getDescription();

  void setDescription(String description);

  Stream<UserModel> getUserMappingsStream();

  Stream<OrganizationGroupModel> getSubGroupsStream();

  Stream<OrganizationRoleModel> getRoleMappingsStream();

  String getParentId();

  OrganizationGroupModel getParent();

  void setParent(OrganizationGroupModel group);

  void addChild(OrganizationGroupModel subGroup);

  void removeChild(OrganizationGroupModel subGroup);

  void joinGroup(UserModel user);

  void leaveGroup(UserModel user);

  boolean isMember(UserModel user);

  void removeGroup();

  OrganizationGroupEntity getEntity();
}
