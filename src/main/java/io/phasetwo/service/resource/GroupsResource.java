package io.phasetwo.service.resource;

import static io.phasetwo.service.resource.Converters.convertOrganizationGroup;

import io.phasetwo.service.model.OrganizationGroupModel;
import io.phasetwo.service.model.OrganizationModel;
import io.phasetwo.service.representation.Group;
import java.net.URI;
import java.util.stream.Stream;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;

@JBossLog
public class GroupsResource extends OrganizationAdminResource {

  private final OrganizationModel organization;

  public GroupsResource(OrganizationAdminResource parent, OrganizationModel organization) {
    super(parent);
    this.organization = organization;
  }

  @Path("/{groupId}")
  public GroupResource group(@PathParam("groupId") String groupId) {
    if (auth.hasViewOrgs() || auth.hasOrgViewGroups(organization)) {
      if (organization.getGroupById(groupId) == null) {
        throw new NotFoundException();
      }
      return new GroupResource(this, organization, groupId);
    } else {
      throw new NotAuthorizedException(
          String.format("Insufficient permission to access role for %s", organization.getId()));
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Stream<Group> getGroups() {
    return organization.getGroupsStream().map(Converters::convertOrganizationGroup);
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createGroup(@Valid Group representation) {
    if (!auth.hasManageOrgs() && !auth.hasOrgManageGroups(organization)) {
      throw notAuthorized(OrganizationAdminAuth.ORG_ROLE_MANAGE_ROLES, organization);
    }

    String groupName = representation.getName();
    try {
      OrganizationGroupModel parent = null;
      if (representation.getParentId() != null) {
        parent = organization.getGroupById(representation.getParentId());
        if (parent == null) {
          throw new NotFoundException("Could not find parent group by id");
        }
      }

      OrganizationGroupModel child = organization.createGroup(groupName, parent);
      GroupResource groupResource = new GroupResource(this, organization, child.getId());
      groupResource.applySecondaryProperties(representation, child);
      URI uri = session.getContext().getUri().getAbsolutePathBuilder().path(child.getId()).build();

      adminEvent
          .operation(OperationType.CREATE)
          .resourcePath(session.getContext().getUri(), child.getId())
          .representation(convertOrganizationGroup(child))
          .success();

      return Response.created(uri).build();
    } catch (ModelDuplicateException e) {
      throw new ClientErrorException(
          String.format("Level group named '%s' already exists.", groupName),
          Response.Status.CONFLICT);
    }
  }
}
