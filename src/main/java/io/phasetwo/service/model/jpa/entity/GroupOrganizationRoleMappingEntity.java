package io.phasetwo.service.model.jpa.entity;

import java.util.Date;
import java.util.Objects;
import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

@NamedQueries({
  @NamedQuery(
    name = "getOrganizationGroupsInRole",
    query = "select g from GroupOrganizationRoleMappingEntity m, OrganizationGroupEntity g where m.role = :role and g.id = m.group")
})
@Table(
    name = "GROUP_ORGANIZATION_ROLE_MAPPING",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"GROUP_ID", "ROLE_ID"})})
@Entity
public class GroupOrganizationRoleMappingEntity {

  @Id
  @Column(name = "ID", length = 36)
  @Access(
      AccessType.PROPERTY) // we do this because relationships often fetch id, but not entity.  This
  // avoids an extra SQL
  protected String id;

  @ManyToOne
  @JoinColumn(name = "GROUP_ID")
  protected OrganizationGroupEntity group;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ROLE_ID")
  protected OrganizationRoleEntity role;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATED_AT")
  protected Date createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) createdAt = new Date();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public OrganizationGroupEntity getGroup() {
    return group;
  }

  public void setGroup(OrganizationGroupEntity group) {
    this.group = group;
  }

  public OrganizationRoleEntity getRole() {
    return role;
  }

  public void setRole(OrganizationRoleEntity role) {
    this.role = role;
  }

  public Date getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Date at) {
    createdAt = at;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (!(o instanceof GroupOrganizationRoleMappingEntity)) return false;

    GroupOrganizationRoleMappingEntity key = (GroupOrganizationRoleMappingEntity) o;

    if (!group.equals(key.group)) return false;
    if (!role.equals(key.role)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(role, group);
  }
}
