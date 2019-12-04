/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.apache.http.HttpStatus;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityRestDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_EMAIL_DOMAIN;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class CollectionRestServiceRoleIT extends AbstractIT {

  private static final String USER_KERMIT = "kermit";
  private static final String TEST_GROUP = "testGroup";
  private static final String TEST_GROUP_B = "anotherTestGroup";
  private static final String USER_MISS_PIGGY = "MissPiggy";

  @Test
  public void partialCollectionUpdateDoesNotAffectRoles() {
    //given
    final String collectionId = collectionClient.createNewCollection();
    final List<CollectionRoleDto> expectedRoles = collectionClient.getCollectionRoles(collectionId);

    // when
    final PartialCollectionDefinitionDto collectionRenameDto = new PartialCollectionDefinitionDto("Test");
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(204));
    final List<CollectionRoleDto> actualRoles = collectionClient.getCollectionRoles(collectionId);
    assertThat(actualRoles, is(expectedRoles));
  }

  @Test
  public void getRoles() {
    //given
    final String collectionId = collectionClient.createNewCollection();
    final List<CollectionRoleRestDto> expectedRoles = getRoles(collectionId);

    // when
    List<CollectionRoleRestDto> roles = getRoles(collectionId);

    // then
    assertThat(roles.size(), is(1));
    assertThat(roles, is(expectedRoles));
  }

  @Test
  public void getRolesSortedCorrectly() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    engineIntegrationExtension.createGroup(TEST_GROUP, TEST_GROUP);
    engineIntegrationExtension.createGroup(TEST_GROUP_B, TEST_GROUP_B);
    engineIntegrationExtension.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_KERMIT);
    engineIntegrationExtension.addUser(USER_MISS_PIGGY, USER_MISS_PIGGY);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_MISS_PIGGY);

    GroupDto testGroupDto = new GroupDto(TEST_GROUP, TEST_GROUP);
    GroupDto anotherTestGroupDto = new GroupDto(TEST_GROUP_B, TEST_GROUP_B);
    UserDto kermitUserDto = new UserDto(USER_KERMIT, USER_KERMIT);
    UserDto missPiggyUserDto = new UserDto(USER_MISS_PIGGY, USER_MISS_PIGGY);
    UserDto demoUserDto = new UserDto(DEFAULT_USERNAME, DEFAULT_USERNAME);

    List<IdentityRestDto> identities = new ArrayList<>();
    identities.add(testGroupDto);
    identities.add(anotherTestGroupDto);
    identities.add(kermitUserDto);
    identities.add(missPiggyUserDto);

    identities.forEach(i -> embeddedOptimizeExtension.getIdentityService().addIdentity(i));

    collectionClient.addRoleToCollection(
      collectionId,
      new CollectionRoleDto(new IdentityDto(TEST_GROUP, IdentityType.GROUP), RoleType.EDITOR)
    );
    collectionClient.addRoleToCollection(
      collectionId,
      new CollectionRoleDto(new IdentityDto(TEST_GROUP_B, IdentityType.GROUP), RoleType.EDITOR)
    );
    collectionClient.addRoleToCollection(
      collectionId,
      new CollectionRoleDto(new IdentityDto(USER_KERMIT, IdentityType.USER), RoleType.EDITOR)
    );
    collectionClient.addRoleToCollection(
      collectionId,
      new CollectionRoleDto(new IdentityDto(USER_MISS_PIGGY, IdentityType.USER), RoleType.EDITOR)
    );

    // when
    List<CollectionRoleRestDto> roles = getRoles(collectionId);

    // then
    // expected order(groups first, user second, then by name ascending):
    // anotherTestGroupRole, testGroupRole, demoManagerRole, kermitRole, missPiggyRole
    assertThat(roles.size(), is(identities.size() + 1)); // +1 for demo manager role
    assertThat(roles.get(0).getIdentity(), is(anotherTestGroupDto));
    assertThat(roles.get(1).getIdentity(), is(testGroupDto));
    assertThat(roles.get(2).getIdentity(), is(demoUserDto));
    assertThat(roles.get(3).getIdentity(), is(kermitUserDto));
    assertThat(roles.get(4).getIdentity(), is(missPiggyUserDto));
  }

  @Test
  public void getRolesContainsUserMetadata_retrieveFromCache() {
    //given
    final String collectionId = collectionClient.createNewCollection();

    UserDto expectedUserDtoWithData =
      new UserDto(DEFAULT_USERNAME, DEFAULT_FIRSTNAME, DEFAULT_LASTNAME, "me@camunda.com");

    embeddedOptimizeExtension.getIdentityService().addIdentity(expectedUserDtoWithData);

    // when
    List<CollectionRoleRestDto> roles = getRoles(collectionId);

    // then
    assertThat(roles.size(), is(1));
    final IdentityRestDto identityRestDto = roles.get(0).getIdentity();
    assertThat(identityRestDto, is(instanceOf(UserDto.class)));
    final UserDto userDto = (UserDto) identityRestDto;
    assertThat(userDto.getFirstName(), is(expectedUserDtoWithData.getFirstName()));
    assertThat(userDto.getLastName(), is(expectedUserDtoWithData.getLastName()));
    assertThat(
      userDto.getName(),
      is(expectedUserDtoWithData.getFirstName() + " " + expectedUserDtoWithData.getLastName())
    );
    assertThat(userDto.getEmail(), is(expectedUserDtoWithData.getEmail()));
  }

  @Test
  public void getRolesContainsUserMetadata_fetchIfNotInCache() {
    //given
    final String collectionId = collectionClient.createNewCollection();

    // when
    List<CollectionRoleRestDto> roles = getRoles(collectionId);

    // then
    assertThat(roles.size(), is(1));
    final IdentityRestDto identityRestDto = roles.get(0).getIdentity();
    assertThat(identityRestDto, is(instanceOf(UserDto.class)));
    final UserDto userDto = (UserDto) identityRestDto;
    assertThat(userDto.getId(), is(DEFAULT_USERNAME));
    assertThat(userDto.getFirstName(), is(DEFAULT_FIRSTNAME));
    assertThat(userDto.getLastName(), is(DEFAULT_LASTNAME));
    assertThat(
      userDto.getName(),
      is(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME)
    );
    assertThat(userDto.getEmail(), endsWith(DEFAULT_EMAIL_DOMAIN));
  }

  @Test
  public void getRolesContainsGroupMetadata() {
    //given
    final String collectionId = collectionClient.createNewCollection();
    engineIntegrationExtension.createGroup(TEST_GROUP, TEST_GROUP);
    engineIntegrationExtension.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtension.addUserToGroup(USER_KERMIT, TEST_GROUP);
    engineIntegrationExtension.addUser(USER_MISS_PIGGY, USER_MISS_PIGGY);
    engineIntegrationExtension.addUserToGroup(USER_MISS_PIGGY, TEST_GROUP);

    final CollectionRoleDto roleDto = new CollectionRoleDto(
      new IdentityDto(TEST_GROUP, IdentityType.GROUP),
      RoleType.EDITOR
    );
    collectionClient.addRoleToCollection(collectionId, roleDto);

    // when
    List<CollectionRoleRestDto> roles = getRoles(collectionId);

    // then
    final List<IdentityRestDto> groupIdentities = roles.stream()
      .map(CollectionRoleRestDto::getIdentity)
      .filter(identityDto -> identityDto instanceof GroupDto)
      .collect(Collectors.toList());
    assertThat(groupIdentities.size(), is(1));

    final GroupDto expectedGroupDto = new GroupDto(TEST_GROUP, TEST_GROUP, 2L);
    final GroupDto actualGroupDto = (GroupDto) groupIdentities.get(0);
    assertThat(actualGroupDto, is(expectedGroupDto));
  }

  @Test
  public void getRolesNoGroupMetadataAvailable() {
    //given
    final String collectionId = collectionClient.createNewCollection();
    engineIntegrationExtension.createGroup(TEST_GROUP, null);

    final CollectionRoleDto roleDto = new CollectionRoleDto(
      new IdentityDto(TEST_GROUP, IdentityType.USER),
      RoleType.EDITOR
    );
    collectionClient.addRoleToCollection(collectionId, roleDto);

    // when
    List<CollectionRoleRestDto> roles = getRoles(collectionId);

    // then
    final List<IdentityRestDto> groupIdentities = roles.stream()
      .map(CollectionRoleRestDto::getIdentity)
      .filter(identityDto -> identityDto instanceof GroupDto)
      .collect(Collectors.toList());
    assertThat(groupIdentities.size(), is(1));

    final GroupDto groupDto = (GroupDto) groupIdentities.get(0);
    assertThat(groupDto.getName(), is(nullValue()));
  }

  @Test
  public void addUserRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    engineIntegrationExtension.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_KERMIT);

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(
      new IdentityDto(USER_KERMIT, IdentityType.USER),
      RoleType.EDITOR
    );
    IdDto idDto = collectionClient.addRoleToCollection(collectionId, roleDto);

    // then
    assertThat(idDto.getId(), is(roleDto.getId()));
    final List<CollectionRoleDto> roles = collectionClient.getCollectionRoles(collectionId);
    assertThat(roles, hasItem(roleDto));
  }

  @Test
  public void addMultipleUserRoles() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    engineIntegrationExtension.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_KERMIT);
    engineIntegrationExtension.addUser(USER_MISS_PIGGY, USER_MISS_PIGGY);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_MISS_PIGGY);

    // when
    final CollectionRoleDto kermitRoleDto = new CollectionRoleDto(
      new IdentityDto(USER_KERMIT, IdentityType.USER),
      RoleType.EDITOR
    );
    IdDto kermitRoleIdDto = collectionClient.addRoleToCollection(collectionId, kermitRoleDto);

    final CollectionRoleDto missPiggyRoleDto = new CollectionRoleDto(
      new IdentityDto(USER_MISS_PIGGY, IdentityType.USER),
      RoleType.VIEWER
    );
    IdDto missPiggyIdDto = collectionClient.addRoleToCollection(collectionId, missPiggyRoleDto);

    // then
    assertThat(kermitRoleIdDto.getId(), is(kermitRoleDto.getId()));
    assertThat(missPiggyIdDto.getId(), is(missPiggyRoleDto.getId()));

    final List<CollectionRoleDto> roles = collectionClient.getCollectionRoles(collectionId);
    assertThat(roles, hasItems(kermitRoleDto, missPiggyRoleDto));
  }

  @Test
  public void addUserRoleFailsForUnknownUsers() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(
      new IdentityDto(USER_KERMIT, IdentityType.USER),
      RoleType.EDITOR
    );
    final Response addRoleResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(addRoleResponse.getStatus(), is(400));
  }

  @Test
  public void addUserRoleFailsNotExistingUser() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(
      new IdentityDto(USER_KERMIT, IdentityType.USER),
      RoleType.EDITOR
    );
    final Response addRoleResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(addRoleResponse.getStatus(), is(400));
  }

  @Test
  public void addGroupRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    engineIntegrationExtension.createGroup(TEST_GROUP, TEST_GROUP);

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(
      new IdentityDto(TEST_GROUP, IdentityType.USER),
      RoleType.EDITOR
    );
    IdDto idDto = collectionClient.addRoleToCollection(collectionId, roleDto);

    // then
    assertThat(idDto.getId(), is(roleDto.getId()));
    final List<CollectionRoleDto> roles = collectionClient.getCollectionRoles(collectionId);
    assertThat(roles, hasItem(roleDto));
  }

  @Test
  public void addGroupRoleFailsNotExistingGroup() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(
      new IdentityDto(TEST_GROUP, IdentityType.GROUP),
      RoleType.EDITOR
    );
    final Response addRoleResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(addRoleResponse.getStatus(), is(400));
  }

  @Test
  public void duplicateIdentityRoleIsRejected() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final List<CollectionRoleDto> expectedRoles = collectionClient.getCollectionRoles(collectionId);

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(
      // there is already an entry for the default user who created the collection
      new IdentityDto(DEFAULT_USERNAME, IdentityType.USER),
      RoleType.EDITOR
    );
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_CONFLICT));
    final ConflictResponseDto conflictResponseDto = response.readEntity(ConflictResponseDto.class);
    assertThat(conflictResponseDto.getErrorMessage(), is(notNullValue()));

    assertThat(collectionClient.getCollectionRoles(collectionId), is(expectedRoles));
  }

  @Test
  public void roleCanGetUpdated() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    engineIntegrationExtension.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_KERMIT);

    // when
    final IdentityDto identityDto = new IdentityDto(USER_KERMIT, IdentityType.USER);
    final CollectionRoleDto roleDto = new CollectionRoleDto(identityDto, RoleType.EDITOR);
    collectionClient.addRoleToCollection(collectionId, roleDto);

    final CollectionRoleUpdateDto updatedRoleDto = new CollectionRoleUpdateDto(RoleType.VIEWER);
    updateRoleOnCollection(collectionId, roleDto.getId(), updatedRoleDto);

    // then
    final List<CollectionRoleDto> roles = collectionClient.getCollectionRoles(collectionId);
    assertThat(roles.size(), is(2));
    assertThat(roles, hasItem(new CollectionRoleDto(identityDto, RoleType.VIEWER)));
  }

  @Test
  public void updatingLastManagerFails() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final List<CollectionRoleDto> expectedRoles = collectionClient.getCollectionRoles(collectionId);
    final CollectionRoleDto roleEntryDto = expectedRoles.get(0);

    // when
    final CollectionRoleUpdateDto updatedRoleDto = new CollectionRoleUpdateDto(RoleType.EDITOR);
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateRoleToCollectionRequest(collectionId, roleEntryDto.getId(), updatedRoleDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_CONFLICT));
    final ConflictResponseDto conflictResponseDto = response.readEntity(ConflictResponseDto.class);
    assertThat(conflictResponseDto.getErrorMessage(), is(notNullValue()));

    assertThat(collectionClient.getCollectionRoles(collectionId), is(expectedRoles));
  }

  @Test
  public void updatingNonPresentRoleFails() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final List<CollectionRoleDto> expectedRoles = collectionClient.getCollectionRoles(collectionId);
    final String notExistingRoleEntryId = "USER:abc";

    // when
    final CollectionRoleUpdateDto updatedRoleDto = new CollectionRoleUpdateDto(RoleType.EDITOR);
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateRoleToCollectionRequest(collectionId, notExistingRoleEntryId, updatedRoleDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_NOT_FOUND));

    assertThat(collectionClient.getCollectionRoles(collectionId), is(expectedRoles));
  }

  @Test
  public void roleCanGetDeleted() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    engineIntegrationExtension.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_KERMIT);

    // when
    final IdentityDto identityDto = new IdentityDto(USER_KERMIT, IdentityType.USER);
    final CollectionRoleDto roleDto = new CollectionRoleDto(identityDto, RoleType.EDITOR);
    collectionClient.addRoleToCollection(collectionId, roleDto);
    deleteRoleFromCollection(collectionId, roleDto.getId());

    // then
    final List<CollectionRoleDto> roles = collectionClient.getCollectionRoles(collectionId);
    assertThat(roles.size(), is(1));
    assertThat(roles, not(hasItem(roleDto)));
  }

  @Test
  public void deletingLastManagerFails() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final List<CollectionRoleDto> expectedRoles = collectionClient.getCollectionRoles(collectionId);
    final CollectionRoleDto roleEntryDto = expectedRoles.get(0);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteRoleToCollectionRequest(collectionId, roleEntryDto.getId())
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_CONFLICT));

    final List<CollectionRoleDto> actualRoles = collectionClient.getCollectionRoles(collectionId);
    assertThat(actualRoles, is(expectedRoles));
  }

  private void updateRoleOnCollection(final String collectionId,
                                      final String roleEntryId,
                                      final CollectionRoleUpdateDto updateDto) {
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateRoleToCollectionRequest(collectionId, roleEntryId, updateDto)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  private void deleteRoleFromCollection(final String collectionId,
                                        final String roleEntryId) {
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteRoleToCollectionRequest(collectionId, roleEntryId)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  private List<CollectionRoleRestDto> getRoles(final String collectionId) {
    return getRoles(DEFAULT_USERNAME, DEFAULT_PASSWORD, collectionId);
  }

  private List<CollectionRoleRestDto> getRoles(final String userId, final String password, final String collectionId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(userId, password)
      .buildGetRolesToCollectionRequest(collectionId)
      .executeAndReturnList(CollectionRoleRestDto.class, 200);
  }
}
