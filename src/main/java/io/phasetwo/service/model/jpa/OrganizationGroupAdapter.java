package io.phasetwo.service.model.jpa;

import io.phasetwo.service.model.OrganizationGroupModel;
import io.phasetwo.service.model.OrganizationModel;
import io.phasetwo.service.model.OrganizationRoleModel;
import io.phasetwo.service.model.jpa.entity.OrganizationAttributeEntity;
import io.phasetwo.service.model.jpa.entity.OrganizationGroupAttributeEntity;
import io.phasetwo.service.model.jpa.entity.OrganizationGroupEntity;
import io.phasetwo.service.model.jpa.entity.OrganizationGroupMemberEntity;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.jpa.JpaModel;
import org.keycloak.models.jpa.entities.GroupEntity;
import org.keycloak.models.utils.KeycloakModelUtils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.keycloak.utils.StreamsUtil.closing;

public class OrganizationGroupAdapter implements JpaModel<OrganizationGroupEntity>, OrganizationGroupModel {

    protected final KeycloakSession session;
    protected final OrganizationGroupEntity group;
    protected final EntityManager em;
    protected final RealmModel realm;
    protected final OrganizationModel organization;

    public OrganizationGroupAdapter(
            KeycloakSession session,
            RealmModel realm,
            EntityManager em,
            OrganizationModel organization,
            OrganizationGroupEntity group
    ) {
        this.session = session;
        this.realm = realm;
        this.em = em;
        this.organization = organization;
        this.group = group;
    }

    @Override
    public OrganizationGroupEntity getEntity() {
        return group;
    }

    @Override
    public String getId() {
        return group.getId();
    }

    @Override
    public String getName() {
        return group.getName();
    }

    @Override
    public String getDescription() {
        return group.getDescription();
    }

    @Override
    public void setDescription(String description) {
        group.setDescription(description);
    }

    @Override
    public Stream<UserModel> getUserMappingsStream() {
        return group.getUserMappings().stream()
                .map(OrganizationGroupMemberEntity::getUserId)
                .map(uid -> session.users().getUserById(realm, uid));
    }

    @Override
    public Stream<OrganizationGroupModel> getSubGroupsStream() {
        TypedQuery<String> query = em.createNamedQuery("getOrganizationGroupIdsByParent", String.class);
        query.setParameter("parent", group.getId());
        return closing(query.getResultStream().map(organization::getGroupById).filter(Objects::nonNull));
    }

    @Override
    public String getParentId() {
        return GroupEntity.TOP_PARENT_ID.equals(group.getParentId())? null : group.getParentId();
    }

    @Override
    public Stream<OrganizationRoleModel> getRoleMappingsStream() {
        return group.getRoleMappings().stream().map(m -> new OrganizationRoleAdapter(session, realm, em, m.getRole(), organization));
    }

    @Override
    public OrganizationGroupModel getParent() {
        String parentId = group.getParentId();
        if (parentId == null) {
            return null;
        }

        OrganizationAdapter adapter = new OrganizationAdapter(session, realm, em, group.getOrganization());
        return adapter.getGroupsStream().filter(g -> g.getId().equals(parentId)).findFirst().orElse(null);
    }

    @Override
    public void setParent(OrganizationGroupModel parent) {
        if (parent == null) {
            group.setParentId(GroupEntity.TOP_PARENT_ID);
        } else if (!parent.getId().equals(getId())) {
            OrganizationGroupEntity parentEntity = parent.getEntity();
            group.setParentId(parentEntity.getId());
        }
    }

    @Override
    public void addChild(OrganizationGroupModel subGroup) {
        if (subGroup.getId().equals(getId())) {
            return;
        }
        subGroup.setParent(this);
    }

    @Override
    public void removeChild(OrganizationGroupModel subGroup) {
        if (subGroup.getId().equals(getId())) {
            return;
        }
        subGroup.setParent(null);
    }

    @Override
    public void joinGroup(UserModel user) {
        leaveGroup(user);

        OrganizationGroupMemberEntity m = new OrganizationGroupMemberEntity();
        m.setId(KeycloakModelUtils.generateId());
        m.setUserId(user.getId());
        m.setGroup(group);
        m.setOrganization(group.getOrganization());
        em.persist(m);
        group.getUserMappings().add(m);
    }

    @Override
    public void leaveGroup(UserModel user) {
        group.getUserMappings().removeIf(m -> m.getUserId().equals(user.getId()));
    }

    @Override
    public boolean isMember(UserModel user) {
        return group.getUserMappings().stream().anyMatch(m -> m.getUserId().equals(user.getId()));
    }

    @Override
    public void removeGroup() {
        group.getUserMappings().clear();
        group.getRoleMappings().clear();
        getSubGroupsStream().forEach(g -> organization.removeGroup(g.getId()));
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        MultivaluedHashMap<String, String> result = new MultivaluedHashMap<>();
        for (OrganizationGroupAttributeEntity attr : group.getAttributes()) {
            result.add(attr.getName(), attr.getValue());
        }
        return result;
    }

    @Override
    public void removeAttribute(String name) {
        group.getAttributes().removeIf(attribute -> attribute.getName().equals(name));
    }

    @Override
    public void removeAttributes() {
        group.getAttributes().clear();
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        removeAttribute(name);
        for (String value : values) {
            OrganizationGroupAttributeEntity a = new OrganizationGroupAttributeEntity();
            a.setId(KeycloakModelUtils.generateId());
            a.setName(name);
            a.setValue(value);
            a.setGroup(group);
            em.persist(a);
            group.getAttributes().add(a);
        }
    }
}
