package io.phasetwo.service.model.jpa;

import com.google.common.collect.Streams;
import io.phasetwo.service.model.OrganizationGroupModel;
import io.phasetwo.service.model.OrganizationModel;
import io.phasetwo.service.model.OrganizationRoleModel;
import io.phasetwo.service.model.jpa.entity.GroupOrganizationRoleMappingEntity;
import io.phasetwo.service.model.jpa.entity.OrganizationGroupEntity;
import io.phasetwo.service.model.jpa.entity.OrganizationRoleEntity;
import io.phasetwo.service.model.jpa.entity.UserOrganizationRoleMappingEntity;

import java.util.Objects;
import java.util.stream.Stream;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.jpa.JpaModel;
import org.keycloak.models.utils.KeycloakModelUtils;

import static org.keycloak.utils.StreamsUtil.closing;

public class OrganizationRoleAdapter
        implements OrganizationRoleModel, JpaModel<OrganizationRoleEntity> {

  protected final KeycloakSession session;
  protected final OrganizationRoleEntity role;
  protected final OrganizationModel organization;
  protected final EntityManager em;
  protected final RealmModel realm;

  public OrganizationRoleAdapter(
          KeycloakSession session, RealmModel realm, EntityManager em, OrganizationRoleEntity role, OrganizationModel organization) {
    this.session = session;
    this.em = em;
    this.role = role;
    this.realm = realm;
    this.organization = organization;
  }

  @Override
  public OrganizationRoleEntity getEntity() {
    return role;
  }

  @Override
  public String getId() {
    return role.getId();
  }

  @Override
  public String getName() {
    return role.getName();
  }

  @Override
  public void setName(String name) {
    role.setName(name);
  }

  @Override
  public String getDescription() {
    return role.getDescription();
  }

  @Override
  public void setDescription(String description) {
    role.setDescription(description);
  }

  @Override
  public Stream<UserModel> getUserMappingsStream() {
    return role.getUserMappings().stream()
            .map(UserOrganizationRoleMappingEntity::getUserId)
            .map(uid -> session.users().getUserById(realm, uid));
  }

  @Override
  public void grantRole(UserModel user) {
    // todo must be a member
    revokeRole(user);
    UserOrganizationRoleMappingEntity m = new UserOrganizationRoleMappingEntity();
    m.setId(KeycloakModelUtils.generateId());
    m.setUserId(user.getId());
    m.setRole(role);
    em.persist(m);
    role.getUserMappings().add(m);
  }

  @Override
  public void revokeRole(UserModel user) {
    role.getUserMappings().removeIf(m -> m.getUserId().equals(user.getId()));
  }

  @Override
  public void grantRole(OrganizationGroupModel group) {
    revokeRole(group);
    GroupOrganizationRoleMappingEntity m = new GroupOrganizationRoleMappingEntity();
    m.setId(KeycloakModelUtils.generateId());
    m.setGroup(group.getEntity());
    m.setRole(role);
    em.persist(m);
    role.getGroupMappings().add(m);
  }

  @Override
  public void revokeRole(OrganizationGroupModel group) {
    role.getGroupMappings().removeIf(m -> m.getGroup().getId().equals(group.getId()));
  }

  @Override
  public boolean hasRole(OrganizationGroupModel group) {
    return role.getGroupMappings().stream().anyMatch(m -> m.getGroup().getId().equals(group.getId()));
  }

  private boolean hasIndirectRole(UserModel user) {
    TypedQuery<OrganizationGroupEntity> query = em.createNamedQuery("getOrganizationGroupsInRole", OrganizationGroupEntity.class);
    query.setParameter("role", role);
    Stream<OrganizationGroupModel> groupStream = closing(query.getResultStream()
            .map(g -> organization.getGroupById(g.getId()))
            .filter(Objects::nonNull));

    return groupStream
            .flatMap(this::getUserModelStreamFromGroup)
            .anyMatch(r -> r.getId().equals(user.getId()));
  }

  private Stream<UserModel> getUserModelStreamFromGroup(OrganizationGroupModel g) {
    OrganizationGroupModel parent = g.getParent();
    Stream<UserModel> parentStream = parent == null ? Stream.empty() : getUserModelStreamFromGroup(parent);
    return Streams.concat(g.getUserMappingsStream(), parentStream);
  }

  @Override
  public boolean hasRole(UserModel user) {
    return hasDirectRole(user) || hasIndirectRole(user);
  }

  @Override
  public boolean hasDirectRole(UserModel user) {
    return role.getUserMappings().stream().anyMatch(m -> m.getUserId().equals(user.getId()));
  }
}
