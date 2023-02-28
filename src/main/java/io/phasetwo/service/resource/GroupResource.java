package io.phasetwo.service.resource;

import io.phasetwo.service.model.OrganizationGroupModel;
import io.phasetwo.service.model.OrganizationModel;
import io.phasetwo.service.model.OrganizationRoleModel;
import io.phasetwo.service.representation.Group;
import io.phasetwo.service.representation.OrganizationRole;
import org.jetbrains.annotations.NotNull;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.idm.UserRepresentation;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.stream.Stream;

import static io.phasetwo.service.resource.Converters.convertOrganizationGroup;
import static io.phasetwo.service.resource.OrganizationResourceType.*;
import static org.keycloak.models.utils.ModelToRepresentation.toRepresentation;

public class GroupResource extends OrganizationAdminResource {

  private final OrganizationModel organization;
  private final OrganizationGroupModel group;
  private final String groupId;

  protected GroupResource(RealmModel realm, OrganizationModel organization, String groupId) {
    super(realm);
    this.organization = organization;
    this.group = organization.getGroupById(groupId);
    this.groupId = groupId;
  }

  @GET
  @Path("children")
  @Produces(MediaType.APPLICATION_JSON)
  public Stream<Group> getChildren() {
    return group.getSubGroupsStream().map(Converters::convertOrganizationGroup);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Group getGroup() {
    return convertOrganizationGroup(group);
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateGroup(Group representation) {
    canManageGroups();

    if (representation.getParentId() != null && !representation.getParentId().equals(group.getParentId())) {
      OrganizationGroupModel newParent = organization.getGroupById(representation.getParentId());
      if (newParent == null) {
        throw new NotFoundException("Could not find parent group by id");
      }
      try {
        organization.moveGroup(group, newParent);
      } catch (ModelDuplicateException e) {
        throw new ClientErrorException(
                String.format("Level group named '%s' already exists.", group.getName()), Response.Status.CONFLICT);
      }
    }

    OrganizationGroupModel or = applySecondaryProperties(representation, group);
    adminEvent
            .resource(ORGANIZATION_GROUP.name())
            .operation(OperationType.UPDATE)
            .resourcePath(session.getContext().getUri(), or.getId())
            .representation(convertOrganizationGroup(or))
            .success();
    return Response.noContent().build();
  }

  protected OrganizationGroupModel applySecondaryProperties(Group representation, OrganizationGroupModel group) {
    group.setDescription(representation.getDescription());
    group.removeAttributes();
    if (representation.getAttributes() != null) {
      representation.getAttributes().forEach(group::setAttribute);
    }
    return group;
  }

  @DELETE
  public Response deleteGroup() {
    canManageGroups();

    organization.removeGroup(groupId);

    adminEvent
            .resource(ORGANIZATION_GROUP.name())
            .operation(OperationType.DELETE)
            .resourcePath(session.getContext().getUri(), groupId)
            .success();

    return Response.noContent().build();
  }

  @GET
  @Path("users")
  @Produces(MediaType.APPLICATION_JSON)
  public Stream<UserRepresentation> users() {
    return group.getUserMappingsStream().map(m -> toRepresentation(session, realm, m));
  }

  @GET
  @Path("users/{userId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response userInGroup(@PathParam("userId") String userId) {
    UserModel user = session.users().getUserById(realm, userId);
    if (user != null && group.isMember(user)) {
      return Response.noContent().build();
    } else {
      throw new NotFoundException(String.format("User %s doesn't have membership in group %s", userId, groupId));
    }
  }

  @PUT
  @Path("users/{userId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response joinGroup(@PathParam("userId") String userId) {
    canManageGroups();

    UserModel user = session.users().getUserById(realm, userId);
    if (user != null) {
      if (!organization.hasMembership(user)) {
        throw new BadRequestException(
                String.format(
                        "User '%s' must be a member of '%s' to be included into group.",
                        userId, organization.getName()));
      }
      if (!group.isMember(user)) {
        group.joinGroup(user);

        adminEvent
            .resource(ORGANIZATION_GROUP_MEMBERSHIP.name())
            .operation(OperationType.CREATE)
            .resourcePath(session.getContext().getUri())
            .representation(userId)
            .success();
      }

      return Response.created(session.getContext().getUri().getAbsolutePathBuilder().build())
              .build();
    } else {
      throw new NotFoundException(String.format("User %s doesn't exist", userId));
    }
  }

  @DELETE
  @Path("users/{userId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response leaveGroup(@PathParam("userId") String userId) {
    canManageGroups();

    UserModel user = session.users().getUserById(realm, userId);
    if (user != null && group.isMember(user)) {
      group.leaveGroup(user);
      adminEvent
          .resource(ORGANIZATION_GROUP_MEMBERSHIP.name())
          .operation(OperationType.DELETE)
          .resourcePath(session.getContext().getUri())
          .representation(userId)
          .success();
      return Response.noContent().build();
    } else {
      throw new NotFoundException(String.format("User %s doesn't have role %s", userId, groupId));
    }
  }

  @GET
  @Path("roles")
  @Produces(MediaType.APPLICATION_JSON)
  public Stream<OrganizationRole> getGroupRoles() {
    canViewRoles();
    return group.getRoleMappingsStream().map(Converters::convertOrganizationRole);
  }

  @PUT
  @Path("roles/{roleName}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response addRole(@PathParam("roleName") String roleName) {
    canManageGroups();
    canManageRoles();

    OrganizationRoleModel role = getOrganizationRole(roleName);
    role.grantRole(group);

    adminEvent
            .resource(ORGANIZATION_GROUP_ROLE_MAPPING.name())
            .operation(OperationType.CREATE)
            .resourcePath(session.getContext().getUri(), groupId)
            .success();

    return Response.noContent().build();
  }

  @DELETE
  @Path("roles/{roleName}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response removeRole(@PathParam("roleName") String roleName) {
    canManageGroups();
    canManageRoles();

    OrganizationRoleModel role = organization.getRoleByName(roleName);
    if (role != null) {
      role.revokeRole(group);

      adminEvent
              .resource(ORGANIZATION_GROUP_ROLE_MAPPING.name())
              .operation(OperationType.DELETE)
              .resourcePath(session.getContext().getUri(), groupId)
              .success();
    }

    return Response.noContent().build();
  }

  @GET
  @Path("roles/{roleName}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response hasRole(@PathParam("roleName") String roleName) {
    canViewRoles();

    OrganizationRoleModel role = getOrganizationRole(roleName);
    if (role.hasRole(group)) {
      return Response.noContent().build();
    } else {
      throw new NotFoundException(String.format("Group %s doesn't have role %s", groupId, roleName));
    }
  }

  @NotNull
  private OrganizationRoleModel getOrganizationRole(String roleName) {
    OrganizationRoleModel role = organization.getRoleByName(roleName);
    if (role == null) {
      throw new NotFoundException(
              String.format("Role '%s' not found in the organization '%s'", roleName, organization.getName()));
    }
    return role;
  }

  private void canManageGroups() {
    if (!auth.hasManageOrgs() && !auth.hasOrgManageGroups(organization)) {
      throw notAuthorized(OrganizationAdminAuth.ORG_ROLE_MANAGE_GROUPS, organization);
    }
  }

  private void canManageRoles() {
    if (!auth.hasManageOrgs() && !auth.hasOrgManageRoles(organization)) {
      throw notAuthorized(OrganizationAdminAuth.ORG_ROLE_MANAGE_ROLES, organization);
    }
  }

  private void canViewRoles() {
    if (!auth.hasViewOrgs() && !auth.hasOrgViewRoles(organization)) {
      throw notAuthorized(OrganizationAdminAuth.ORG_ROLE_VIEW_ROLES, organization);
    }
  }
}
