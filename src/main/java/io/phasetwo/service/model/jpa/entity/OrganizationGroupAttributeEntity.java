package io.phasetwo.service.model.jpa.entity;

import org.hibernate.annotations.Nationalized;

import javax.persistence.*;
import java.util.Objects;

@Table(
  name = "ORGANIZATION_GROUP_ATTRIBUTE",
  uniqueConstraints = {@UniqueConstraint(columnNames = {"GROUP_ID", "NAME"})})
@Entity
public class OrganizationGroupAttributeEntity {

  @Id
  @Column(name = "ID", length = 36)
  @Access(
          AccessType.PROPERTY) // we do this because relationships often fetch id, but not entity.  This
  // avoids an extra SQL
  protected String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "GROUP_ID")
  protected OrganizationGroupEntity group;

  @Column(name = "NAME")
  protected String name;

  @Nationalized
  @Column(name = "VALUE")
  protected String value;

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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (!(o instanceof OrganizationGroupAttributeEntity)) return false;

    OrganizationGroupAttributeEntity key = (OrganizationGroupAttributeEntity) o;

    if (!name.equals(key.name)) return false;
    if (!group.equals(key.group)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(group, name);
  }
}
