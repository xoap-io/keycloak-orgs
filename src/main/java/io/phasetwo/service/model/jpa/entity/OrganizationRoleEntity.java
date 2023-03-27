package io.phasetwo.service.model.jpa.entity;

import static io.phasetwo.service.model.jpa.entity.Entities.setCollection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import javax.persistence.*;

/** */
@NamedQueries({
  @NamedQuery(
      name = "getOrganizationRoles",
      query = "SELECT m FROM OrganizationRoleEntity m"),
  @NamedQuery(
      name = "getOrganizationRoleByName",
      query =
          "SELECT m FROM OrganizationRoleEntity m WHERE m.name = :name"),
  @NamedQuery(
      name = "removeOrganizationRole",
      query =
          "DELETE FROM OrganizationRoleEntity m WHERE m.name = :name")
})
@Table(
    name = "ORGANIZATION_ROLE",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"NAME"})})
@Entity
public class OrganizationRoleEntity {

  @Id
  @Column(name = "ID", length = 36)
  @Access(AccessType.PROPERTY)
  protected String id;

  @Column(name = "NAME", nullable = false)
  protected String name;

  @Column(name = "DESCRIPTION")
  protected String description;

  @OneToMany(
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      mappedBy = "role")
  protected Collection<UserOrganizationRoleMappingEntity> userMappings =
      new ArrayList<UserOrganizationRoleMappingEntity>();

  @OneToMany(
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      mappedBy = "role")
  protected Collection<GroupOrganizationRoleMappingEntity> groupMappings =
      new ArrayList<GroupOrganizationRoleMappingEntity>();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Collection<UserOrganizationRoleMappingEntity> getUserMappings() {
    return userMappings;
  }

  public void setUserMappings(Collection<UserOrganizationRoleMappingEntity> userMappings) {
    setCollection(userMappings, this.userMappings);
  }

  public Collection<GroupOrganizationRoleMappingEntity> getGroupMappings() {
    return groupMappings;
  }

  public void setGroupMappings(Collection<GroupOrganizationRoleMappingEntity> groupMappings) {
    this.groupMappings = groupMappings;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (!(o instanceof OrganizationRoleEntity)) return false;

    OrganizationRoleEntity key = (OrganizationRoleEntity) o;

    if (!name.equals(key.name)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }
}
