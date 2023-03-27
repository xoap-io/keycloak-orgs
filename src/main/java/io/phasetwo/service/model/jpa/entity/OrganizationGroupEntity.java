package io.phasetwo.service.model.jpa.entity;

import java.util.ArrayList;
import java.util.Collection;
import javax.persistence.*;

@NamedQueries({
  @NamedQuery(
      name = "getOrganizationGroupIdsByParent",
      query = "select u.id from OrganizationGroupEntity u where u.parentId = :parent")
})
@Table(name = "ORGANIZATION_GROUP")
@Entity
public class OrganizationGroupEntity {

  @Id
  @Column(name = "ID", length = 36)
  @Access(AccessType.PROPERTY)
  protected String id;

  @Column(name = "NAME", nullable = false)
  protected String name;

  @Column(name = "DESCRIPTION")
  protected String description;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ORGANIZATION_ID")
  protected OrganizationEntity organization;

  @Column(name = "PARENT_ID")
  private String parentId;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "group")
  protected Collection<OrganizationGroupAttributeEntity> attributes =
      new ArrayList<OrganizationGroupAttributeEntity>();

  @OneToMany(
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      mappedBy = "group")
  protected Collection<OrganizationGroupMemberEntity> userMappings =
      new ArrayList<OrganizationGroupMemberEntity>();

  @OneToMany(
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      mappedBy = "group")
  protected Collection<GroupOrganizationRoleMappingEntity> roleMappings =
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

  public OrganizationEntity getOrganization() {
    return organization;
  }

  public void setOrganization(OrganizationEntity organization) {
    this.organization = organization;
  }

  public Collection<OrganizationGroupMemberEntity> getUserMappings() {
    return userMappings;
  }

  public void setUserMappings(Collection<OrganizationGroupMemberEntity> members) {
    this.userMappings = members;
  }

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }

  public Collection<GroupOrganizationRoleMappingEntity> getRoleMappings() {
    return roleMappings;
  }

  public void setRoleMappings(Collection<GroupOrganizationRoleMappingEntity> roleMappings) {
    this.roleMappings = roleMappings;
  }

  public Collection<OrganizationGroupAttributeEntity> getAttributes() {
    return attributes;
  }

  public void setAttributes(Collection<OrganizationGroupAttributeEntity> attributes) {
    this.attributes = attributes;
  }
}
