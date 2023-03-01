package io.phasetwo.service.resource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.openshift.internal.restclient.model.oauth.OAuthClient;
import io.phasetwo.client.OrganizationResource;
import io.phasetwo.client.OrganizationsResource;
import io.phasetwo.client.UserResource;
import io.phasetwo.client.*;
import io.phasetwo.client.openapi.model.IdentityProviderMapperRepresentation;
import io.phasetwo.client.openapi.model.OrganizationGroupRepresentation;
import io.phasetwo.client.openapi.model.OrganizationRepresentation;
import io.phasetwo.client.openapi.model.OrganizationRoleRepresentation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.models.ClientModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.representations.idm.*;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import java.util.List;

import static io.phasetwo.service.Helpers.createUser;
import static io.phasetwo.service.Helpers.getResponseMessage;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThrows;

public class GroupResourceTest extends AbstractResourceTest {

  private final PhaseTwo client = phaseTwo();
  private OrganizationResource orgResource;
  private OrganizationGroupsResource groupsResource;

  @Before
  public void prepareOrganization() {
    OrganizationsResource orgsResource = client.organizations(REALM);
    String orgId = createDefaultOrg(orgsResource);
    this.orgResource = orgsResource.organization(orgId);
    this.groupsResource = orgResource.groups();
  }

  @After
  public void destroyOrganization() {
    orgResource.delete();
  }

  private OrganizationGroupResource createGroupWithRole(String groupName, String roleName) {
    OrganizationGroupResource groupResource = createGroup(groupName, null);
    String role = orgResource.roles().create(new OrganizationRoleRepresentation().name(roleName));
    groupResource.addRole(role);
    return groupResource;
  }

  private OrganizationGroupResource createGroup(String name) {
    return createGroup(name, null);
  }

  private OrganizationGroupResource createGroup(String name, String parentId) {
    return groupsResource.group(groupsResource.create(new OrganizationGroupRepresentation()
            .name(name)
            .parentId(parentId)));
  }

  @Test
  public void testGroupAttributes() {
    String groupId = groupsResource.create(new OrganizationGroupRepresentation()
            .name("group")
            .putAttributesItem("foo", List.of("bar")));

    OrganizationGroupRepresentation group = groupsResource.group(groupId).get();
    assertThat(group.getId(), is(groupId));
    assertThat(group.getName(), is("group"));
    assertThat(group.getAttributes(), aMapWithSize(1));
    assertThat(group.getAttributes(), hasKey("foo"));
    assertThat(group.getAttributes(), hasValue(allOf(hasItem("bar"), iterableWithSize(1))));
  }

  @Test
  public void testGroupCreationLifecycle() {
    String groupId = groupsResource.create(new OrganizationGroupRepresentation().name("group"));
    assertThat(groupId, notNullValue());

    List<OrganizationGroupRepresentation> groups = groupsResource.get();
    assertThat(groups, hasSize(1));

    OrganizationGroupResource groupResource = groupsResource.group(groupId);
    OrganizationGroupRepresentation group = groupResource.get();
    assertThat(group, notNullValue());
    assertThat(group.getId(), notNullValue());
    assertThat(group.getName(), is("group"));

    groupResource.delete();
    assertThrows(NotFoundException.class, groupResource::get);
    groups = groupsResource.get();
    assertThat(groups, empty());
  }

  @Test
  public void ensureTopLevelGroupNameUniqueness() {
    createGroup("root");
    ClientErrorException ex = assertThrows(ClientErrorException.class, () -> createGroup("root"));
    assertThat(getResponseMessage(ex), is("Level group named 'root' already exists."));
  }

  @Test
  public void ensureChildLevelGroupNameUniqueness() {
    OrganizationGroupResource groupResource = createGroup("root");
    String rootId = groupResource.get().getId();
    createGroup("child", rootId);
    ClientErrorException ex = assertThrows(ClientErrorException.class, () -> createGroup("child", rootId));
    assertThat(getResponseMessage(ex), is("Level group named 'child' already exists."));
  }

  @Test
  public void testUserMembership() {
    Keycloak keycloak = server.client();
    UserRepresentation user = createUser(keycloak, REALM, "johndoe");
    OrganizationGroupResource apples = createGroupWithRole("apples", "eat-apples");
    OrganizationGroupResource vegetables = createGroupWithRole("vegetables", "eat-vegetables");
    String id = orgResource.get().getId();

    // join user to organization and all groups
    OrganizationMembershipsResource membershipsResource = orgResource.memberships();
    membershipsResource.add(user.getId());
    apples.addUser(user.getId());
    vegetables.addUser(user.getId());

    UserResource userResource = client.users(REALM).user(user.getId());
    List<OrganizationRoleRepresentation> userRoles = userResource.getRoles(id);
    assertThat(userRoles, hasSize(2));
    assertThat(userRoles, hasItem(hasProperty("name", is("eat-apples"))));
    assertThat(userRoles, hasItem(hasProperty("name", is("eat-vegetables"))));

    // leave the organization
    membershipsResource.remove(user.getId());
    assertThrows(NotFoundException.class, () -> userResource.getRoles(id));

    // join back to organization
    membershipsResource.add(user.getId());
    assertThat(userResource.getRoles(id), empty());
    assertThat(apples.isMember(user.getId()), is(false));
    assertThat(vegetables.isMember(user.getId()), is(false));
  }

  @Test
  public void testUpdateGroupDescription() {
    OrganizationGroupResource groupResource = createGroup("test");
    OrganizationGroupRepresentation group = groupResource.get();
    assertThat(group.getName(), is("test"));
    assertThat(group.getDescription(), nullValue());

    groupResource.update(group.description("foobar"));
    group = groupResource.get();
    assertThat(group.getName(), is("test"));
    assertThat(group.getDescription(), is("foobar"));
  }

  @Test
  public void testMoveGroup() {
    String rootId = groupsResource.create(new OrganizationGroupRepresentation().name("root"));
    String sub1 = groupsResource.create(new OrganizationGroupRepresentation().name("sub").parentId(rootId));
    String sub2 = groupsResource.create(new OrganizationGroupRepresentation().name("sub").parentId(sub1));

    List<OrganizationGroupRepresentation> rootChildren = groupsResource.group(rootId).children();
    assertThat(rootChildren, hasSize(1));
    assertThat(rootChildren, everyItem(hasProperty("name", is("sub"))));

    // move sub2 to upper level
    ClientErrorException ex = assertThrows(ClientErrorException.class,
            () -> groupsResource.group(sub2).update(new OrganizationGroupRepresentation().parentId(rootId)));
    assertThat(getResponseMessage(ex), is("Level group named 'sub' already exists."));
  }

  @Test
  public void testGroupsHierarchy() {
    OrganizationGroupResource rootResource = createGroup("root");
    String rootId = rootResource.get().getId();
    UserRepresentation user = createUser(server.client(), REALM, "johndoe");
    UserResource userResource = client.users(REALM).user(user.getId());

    // make root with 3 children
    OrganizationGroupResource child1Resource = createGroup("child1", rootId);
    OrganizationGroupResource child2Resource = createGroup("child2", rootId);
    OrganizationGroupResource child3Resource = createGroup("child3", rootId);

    assertThat(groupsResource.get(), hasSize(4));
    assertThat(rootResource.children(), hasSize(3));

    // make roles and apply them to groups
    OrganizationRolesResource rolesResource = orgResource.roles();
    rolesResource.create(new OrganizationRoleRepresentation().name("eat-everything"));
    rolesResource.create(new OrganizationRoleRepresentation().name("eat-fruits"));
    rolesResource.create(new OrganizationRoleRepresentation().name("eat-apples"));
    rolesResource.create(new OrganizationRoleRepresentation().name("eat-vegetables"));

    rootResource.addRole("eat-everything");
    child1Resource.addRole("eat-fruits");
    child2Resource.addRole("eat-apples");
    child3Resource.addRole("eat-vegetables");

    // add user to 'child3' group
    orgResource.memberships().add(user.getId());
    child3Resource.addUser(user.getId());

    String id = orgResource.get().getId();
    List<OrganizationRoleRepresentation> userRoles = userResource.getRoles(id);
    assertThat(userRoles, hasSize(1));
    assertThat(userRoles, everyItem(hasProperty("name", oneOf("eat-vegetables"))));

    // add user to 'child2' group
    child2Resource.addUser(user.getId());
    userRoles = userResource.getRoles(id);

    // user get roles from 'child2' and 'child3'
    assertThat(userRoles, hasSize(2));
    assertThat(userRoles, everyItem(hasProperty("name", oneOf("eat-vegetables", "eat-apples"))));

    // change hierarchy to: root -> child1 -> child2 -> child3
    child2Resource.update(child2Resource.get().parentId(child1Resource.get().getId()));
    child3Resource.update(child3Resource.get().parentId(child2Resource.get().getId()));

    assertThat(rootResource.children(), hasSize(1));
    assertThat(child1Resource.children(), hasSize(1));
    assertThat(child2Resource.children(), hasSize(1));
    assertThat(child3Resource.children(), empty());

    // add user to root
    rootResource.addUser(user.getId());
    userRoles = userResource.getRoles(id);
    assertThat(userRoles, hasSize(4));
    assertThat(userRoles, everyItem(hasProperty("name",
            oneOf("eat-vegetables", "eat-apples", "eat-fruits", "eat-everything"))));

    // remove from root
    rootResource.removeUser(user.getId());
    userRoles = userResource.getRoles(id);
    assertThat(userRoles, hasSize(2));
    assertThat(userRoles, everyItem(hasProperty("name", oneOf("eat-vegetables", "eat-apples"))));

    // make cycle: child3 -> root
    // todo: detect graph cycle and throw exception
    rootResource.update(rootResource.get().parentId(child3Resource.get().getId()));
    assertThat(rootResource.children(), hasSize(1));
    assertThat(child1Resource.children(), hasSize(1));
    assertThat(child2Resource.children(), hasSize(1));
    assertThat(child3Resource.children(), hasSize(1));
  }

  @Test
  public void testUserInMultipleGroups() {
    // create hierarchy: root1 -> [child1, child2]
    OrganizationGroupResource root1Resource = createGroup("root1");
    String root1Id = root1Resource.get().getId();
    OrganizationGroupResource child1Resource = createGroup("child1", root1Id);
    OrganizationGroupResource child2Resource = createGroup("child2", root1Id);

    // create hierarchy: root2 -> [child3, child4]
    OrganizationGroupResource root2Resource = createGroup("root2");
    String root2Id = root2Resource.get().getId();
    OrganizationGroupResource child3Resource = createGroup("child3", root2Id);
    OrganizationGroupResource child4Resource = createGroup("child4", root2Id);

    // single role for child group
    OrganizationRolesResource rolesResource = orgResource.roles();
    rolesResource.create(new OrganizationRoleRepresentation().name("1"));
    rolesResource.create(new OrganizationRoleRepresentation().name("2"));
    rolesResource.create(new OrganizationRoleRepresentation().name("3"));
    rolesResource.create(new OrganizationRoleRepresentation().name("4"));

    child1Resource.addRole("1");
    child2Resource.addRole("2");
    child3Resource.addRole("3");
    child4Resource.addRole("4");

    // create user and join it to all groups
    UserRepresentation user = createUser(server.client(), REALM, "johndoe");
    orgResource.memberships().add(user.getId());
    child1Resource.addUser(user.getId());
    child2Resource.addUser(user.getId());
    child3Resource.addUser(user.getId());
    child4Resource.addUser(user.getId());

    UserResource userResource = client.users(REALM).user(user.getId());
    List<OrganizationRepresentation> organizations = userResource.getOrganizations();
    assertThat(organizations, hasSize(1));
    String orgId = organizations.get(0).getId();

    List<OrganizationRoleRepresentation> roles = userResource.getRoles(orgId);
    assertThat(roles, hasSize(4));
    assertThat(roles, everyItem(hasProperty("name", oneOf("1", "2", "3", "4"))));

    // delete role: verify user lost that role
    rolesResource.delete("1");
    roles = userResource.getRoles(orgId);
    assertThat(roles, hasSize(3));
    assertThat(roles, everyItem(hasProperty("name", oneOf("2", "3", "4"))));

    // delete parent group: verify user lost all children roles
    root2Resource.delete();
    roles = userResource.getRoles(orgId);
    assertThat(roles, hasSize(1));
    assertThat(roles, everyItem(hasProperty("name", oneOf("2"))));

    // remove last group
    child2Resource.delete();
    roles = userResource.getRoles(orgId);
    assertThat(roles, empty());

    assertThat(root1Resource.isMember(user.getId()), is(false));
    assertThat(child1Resource.isMember(user.getId()), is(true));
    assertThrows(NotFoundException.class, root2Resource::get);
    assertThrows(NotFoundException.class, child2Resource::get);
    assertThrows(NotFoundException.class, child3Resource::get);
    assertThrows(NotFoundException.class, child4Resource::get);
  }

  @Test
  public void testJoinUserToGroup() {
    OrganizationGroupResource groupResource = createGroup("group");
    UserRepresentation user = createUser(server.client(), REALM, "johndoe");

    // user should be an organization member before joining group
    ClientErrorException ex = assertThrows(ClientErrorException.class, () -> groupResource.addUser(user.getId()));
    assertThat(ex.getResponse().getStatus(), is(BAD_REQUEST.getStatusCode()));
    assertThat(getResponseMessage(ex), endsWith("must be a member of 'example' to be included into group."));

    // join group
    orgResource.memberships().add(user.getId());
    groupResource.addUser(user.getId());

    List<io.phasetwo.client.openapi.model.UserRepresentation> users = groupResource.users();
    assertThat(users, hasSize(1));
    assertThat(users.get(0).getId(), is(user.getId()));
    assertThat(groupResource.isMember(user.getId()), is(true));

    // after removing membership
    orgResource.memberships().remove(user.getId());
    users = groupResource.users();
    assertThat(users, empty());
    assertThat(groupResource.isMember(user.getId()), is(false));
  }

  @Test
  public void testJoinUserToGroupWithRoles() {
    OrganizationRolesResource rolesResource = orgResource.roles();

    // create two groups - vegans and meat-eaters - with different roles
    OrganizationGroupResource vegansGroup = createGroup("vegans");
    OrganizationGroupResource meatEaters = createGroup("meat-eaters");

    rolesResource.create(new OrganizationRoleRepresentation().name("eat-meat"));
    rolesResource.create(new OrganizationRoleRepresentation().name("eat-apples"));
    rolesResource.create(new OrganizationRoleRepresentation().name("eat-vegetables"));

    vegansGroup.addRole("eat-apples");
    vegansGroup.addRole("eat-vegetables");
    meatEaters.addRole("eat-meat");

    assertThat(vegansGroup.roles(), hasSize(2));
    assertThat(meatEaters.roles(), hasSize(1));

    // create three users and join them to the organization
    UserRepresentation vegan = createUser(server.client(), REALM, "vegan");
    UserRepresentation meatEater = createUser(server.client(), REALM, "meat-eater");
    UserRepresentation omnivorous = createUser(server.client(), REALM, "omnivorous");
    orgResource.memberships().add(vegan.getId());
    orgResource.memberships().add(meatEater.getId());
    orgResource.memberships().add(omnivorous.getId());

    // join users to groups
    vegansGroup.addUser(vegan.getId());
    vegansGroup.addUser(omnivorous.getId());

    meatEaters.addUser(meatEater.getId());
    meatEaters.addUser(omnivorous.getId());

    // verify group members
    List<io.phasetwo.client.openapi.model.UserRepresentation> users = vegansGroup.users();
    assertThat(users, hasSize(2));
    assertThat(users, everyItem(hasProperty("username", oneOf("vegan", "omnivorous"))));

    users = meatEaters.users();
    assertThat(users, hasSize(2));
    assertThat(users, everyItem(hasProperty("username", oneOf("meat-eater", "omnivorous"))));

    UsersResource usersResource = client.users(REALM);
    String id = orgResource.get().getId();
    UserResource meatEaterResource = usersResource.user(meatEater.getId());
    UserResource veganResource = usersResource.user(vegan.getId());
    UserResource omnivorousResource = usersResource.user(omnivorous.getId());

    // verify applied roles
    List<OrganizationRoleRepresentation> meatEaterRoles = meatEaterResource.getRoles(id);
    assertThat(meatEaterRoles, hasSize(1));
    assertThat(meatEaterRoles, hasItem(hasProperty("name", is("eat-meat"))));

    List<OrganizationRoleRepresentation> veganRoles = veganResource.getRoles(id);
    assertThat(veganRoles, hasSize(2));
    assertThat(veganRoles, containsInAnyOrder(
            hasProperty("name", is("eat-vegetables")),
            hasProperty("name", is("eat-apples"))));

    List<OrganizationRoleRepresentation> omnivorousRoles = omnivorousResource.getRoles(id);
    assertThat(omnivorousRoles, hasSize(3));
    assertThat(omnivorousRoles, containsInAnyOrder(
            hasProperty("name", is("eat-vegetables")),
            hasProperty("name", is("eat-apples")),
            hasProperty("name", is("eat-meat"))));

    // remove on of the roles
    rolesResource.delete("eat-apples");
    assertThat(veganResource.getRoles(id), hasSize(1));
    assertThat(meatEaterResource.getRoles(id), hasSize(1));
    assertThat(omnivorousResource.getRoles(id), hasSize(2));

    // remove one of the groups
    meatEaters.delete();
    assertThat(veganResource.getRoles(id), hasSize(1));
    assertThat(meatEaterResource.getRoles(id), empty());
    assertThat(omnivorousResource.getRoles(id), hasSize(1));
  }
}
