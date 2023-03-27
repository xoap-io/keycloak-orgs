package io.phasetwo.service.model.jpa.entity;

import java.util.Date;
import javax.persistence.*;

@Table(
    name = "ORGANIZATION_GROUP_MEMBER",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"GROUP_ID", "USER_ID"})})
@Entity
public class OrganizationGroupMemberEntity {

  @Id
  @Column(name = "ID", length = 36)
  @Access(
      AccessType.PROPERTY) // we do this because relationships often fetch id, but not entity.  This
  // avoids an extra SQL
  protected String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ORGANIZATION_ID")
  protected OrganizationEntity organization;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "GROUP_ID")
  protected OrganizationGroupEntity group;

  @Column(name = "USER_ID")
  protected String userId;

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

  public OrganizationEntity getOrganization() {
    return organization;
  }

  public void setOrganization(OrganizationEntity organization) {
    this.organization = organization;
  }

  public OrganizationGroupEntity getGroup() {
    return group;
  }

  public void setGroup(OrganizationGroupEntity group) {
    this.group = group;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public Date getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Date createdAt) {
    this.createdAt = createdAt;
  }
}
