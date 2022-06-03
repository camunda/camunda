/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.service.collection.CollectionRoleService;
import org.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_GROUP;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_USER;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_EMAIL_DOMAIN;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;

public class CollectionRestServiceRoleIT extends AbstractIT {

  private static final String USER_KERMIT_ID = "kermit";
  private static final String TEST_GROUP_ID = "testGroup";
  private static final String TEST_GROUP_NAME = "The Testing Group";
  private static final String TEST_GROUP_B_ID = "anotherTestGroup";
  private static final String TEST_GROUP_B_NAME = "Just Another Testing Group";
  private static final String USER_MISS_PIGGY_ID = "missPiggy";

  @RegisterExtension
  @Order(5)
  protected final LogCapturer logCapturer =
    LogCapturer.create().forLevel(Level.DEBUG).captureForType(CollectionRoleService.class);

  @Test
  public void partialCollectionUpdateDoesNotAffectRoles() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final List<CollectionRoleResponseDto> expectedRoles = collectionClient.getCollectionRoles(collectionId);

    // when
    final PartialCollectionDefinitionRequestDto collectionRenameDto = new PartialCollectionDefinitionRequestDto("Test");
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    final List<CollectionRoleResponseDto> actualRoles = collectionClient.getCollectionRoles(collectionId);
    assertThat(actualRoles).isEqualTo(expectedRoles);
  }

  @Test
  public void getRoles() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final List<CollectionRoleResponseDto> expectedRoles = collectionClient.getCollectionRoles(collectionId);

    // when
    List<CollectionRoleResponseDto> roles = collectionClient.getCollectionRoles(collectionId);

    // then
    assertThat(roles)
      .hasSize(1)
      .isEqualTo(expectedRoles);
  }

  @Test
  public void getRolesSortedCorrectly() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.createGroupAndGrantOptimizeAccess(TEST_GROUP_ID, TEST_GROUP_NAME);
    authorizationClient.createGroupAndGrantOptimizeAccess(TEST_GROUP_B_ID, TEST_GROUP_B_NAME);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_MISS_PIGGY_ID);

    GroupDto testGroupDto = new GroupDto(TEST_GROUP_ID, TEST_GROUP_NAME);
    GroupDto anotherTestGroupDto = new GroupDto(TEST_GROUP_B_ID, TEST_GROUP_B_NAME);
    UserDto kermitUserDto = new UserDto(USER_KERMIT_ID, USER_KERMIT_ID);
    UserDto missPiggyUserDto = new UserDto(USER_MISS_PIGGY_ID, USER_MISS_PIGGY_ID);
    UserDto demoUserDto = new UserDto(DEFAULT_USERNAME, DEFAULT_USERNAME);

    List<IdentityWithMetadataResponseDto> identities = new ArrayList<>();
    identities.add(testGroupDto);
    identities.add(anotherTestGroupDto);
    identities.add(kermitUserDto);
    identities.add(missPiggyUserDto);

    identities.forEach(i -> embeddedOptimizeExtension.getIdentityService().addIdentity(i));

    collectionClient.addRolesToCollection(
      collectionId,
      new CollectionRoleRequestDto(new IdentityDto(TEST_GROUP_ID, IdentityType.GROUP), RoleType.EDITOR)
    );
    collectionClient.addRolesToCollection(
      collectionId,
      new CollectionRoleRequestDto(new IdentityDto(TEST_GROUP_B_ID, IdentityType.GROUP), RoleType.EDITOR)
    );
    collectionClient.addRolesToCollection(
      collectionId,
      new CollectionRoleRequestDto(new IdentityDto(USER_KERMIT_ID, IdentityType.USER), RoleType.EDITOR)
    );
    collectionClient.addRolesToCollection(
      collectionId,
      new CollectionRoleRequestDto(new IdentityDto(USER_MISS_PIGGY_ID, IdentityType.USER), RoleType.EDITOR)
    );

    // when
    List<CollectionRoleResponseDto> roles = collectionClient.getCollectionRoles(collectionId);

    // then
    // expected order(groups first, user second, then by name ascending):
    // anotherTestGroupRole, testGroupRole, demoManagerRole, kermitRole, missPiggyRole
    assertThat(roles).hasSize(identities.size() + 1); // +1 for demo manager role
    assertThat(roles.get(0).getIdentity().getId()).isEqualTo(anotherTestGroupDto.getId());
    assertThat(roles.get(1).getIdentity().getId()).isEqualTo(testGroupDto.getId());
    assertThat(roles.get(2).getIdentity().getId()).isEqualTo(demoUserDto.getId());
    assertThat(roles.get(3).getIdentity().getId()).isEqualTo(kermitUserDto.getId());
    assertThat(roles.get(4).getIdentity().getId()).isEqualTo(missPiggyUserDto.getId());
  }

  @Test
  public void getRolesSortedCorrectlyFiltersUnauthorizedIdentities() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.createGroupAndGrantOptimizeAccess(TEST_GROUP_ID, TEST_GROUP_NAME);
    authorizationClient.createGroupAndGrantOptimizeAccess(TEST_GROUP_B_ID, TEST_GROUP_B_NAME);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_MISS_PIGGY_ID);

    GroupDto testGroupDto = new GroupDto(TEST_GROUP_ID, TEST_GROUP_NAME);
    GroupDto anotherTestGroupDto = new GroupDto(TEST_GROUP_B_ID, TEST_GROUP_B_NAME);
    UserDto kermitUserDto = new UserDto(USER_KERMIT_ID, USER_KERMIT_ID);
    UserDto missPiggyUserDto = new UserDto(USER_MISS_PIGGY_ID, USER_MISS_PIGGY_ID);
    UserDto demoUserDto = new UserDto(DEFAULT_USERNAME, DEFAULT_USERNAME);

    List<IdentityWithMetadataResponseDto> identities = new ArrayList<>();
    identities.add(testGroupDto);
    identities.add(anotherTestGroupDto);
    identities.add(kermitUserDto);
    identities.add(missPiggyUserDto);

    identities.forEach(i -> embeddedOptimizeExtension.getIdentityService().addIdentity(i));

    collectionClient.addRolesToCollection(
      collectionId,
      new CollectionRoleRequestDto(new IdentityDto(TEST_GROUP_ID, IdentityType.GROUP), RoleType.EDITOR)
    );
    collectionClient.addRolesToCollection(
      collectionId,
      new CollectionRoleRequestDto(new IdentityDto(TEST_GROUP_B_ID, IdentityType.GROUP), RoleType.EDITOR)
    );
    collectionClient.addRolesToCollection(
      collectionId,
      new CollectionRoleRequestDto(new IdentityDto(USER_KERMIT_ID, IdentityType.USER), RoleType.EDITOR)
    );
    collectionClient.addRolesToCollection(
      collectionId,
      new CollectionRoleRequestDto(new IdentityDto(USER_MISS_PIGGY_ID, IdentityType.USER), RoleType.EDITOR)
    );

    authorizationClient.grantSingleResourceAuthorizationForKermit(anotherTestGroupDto.getId(), RESOURCE_TYPE_GROUP);
    authorizationClient.grantSingleResourceAuthorizationForKermit(demoUserDto.getId(), RESOURCE_TYPE_USER);

    // when
    List<CollectionRoleResponseDto> roles = collectionClient.getCollectionRolesAsUser(
      collectionId,
      USER_KERMIT_ID,
      USER_KERMIT_ID
    );

    // then
    // Kermit only sees those identities he's authorized to see
    // expected order(groups first, user second, then by name ascending):
    // anotherTestGroupRole, demoManagerRole, kermitRole
    assertThat(roles.size()).isEqualTo(3);
    assertThat(roles.get(0).getIdentity().getId()).isEqualTo(anotherTestGroupDto.getId());
    assertThat(roles.get(1).getIdentity().getId()).isEqualTo(demoUserDto.getId());
    assertThat(roles.get(2).getIdentity().getId()).isEqualTo(kermitUserDto.getId());
  }

  @Test
  public void getRolesContainsUserMetadata_retrieveFromCache() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    UserDto expectedUserDtoWithData =
      new UserDto(DEFAULT_USERNAME, DEFAULT_FIRSTNAME, DEFAULT_LASTNAME, "me@camunda.com");

    embeddedOptimizeExtension.getIdentityService().addIdentity(expectedUserDtoWithData);

    // when
    List<CollectionRoleResponseDto> roles = collectionClient.getCollectionRoles(collectionId);

    // then
    assertThat(roles.size()).isEqualTo(1);
    final IdentityWithMetadataResponseDto identityRestDto = roles.get(0).getIdentity();
    assertThat(identityRestDto).isInstanceOf(UserDto.class);
    final UserDto userDto = (UserDto) identityRestDto;
    assertThat(userDto.getFirstName()).isEqualTo(expectedUserDtoWithData.getFirstName());
    assertThat(userDto.getLastName()).isEqualTo(expectedUserDtoWithData.getLastName());
    assertThat(userDto.getName())
      .isEqualTo(expectedUserDtoWithData.getFirstName() + " " + expectedUserDtoWithData.getLastName());
    assertThat(userDto.getEmail()).isEqualTo(expectedUserDtoWithData.getEmail());
  }

  @Test
  public void getRolesContainsUserMetadata_fetchIfNotInCache() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    // when
    List<CollectionRoleResponseDto> roles = collectionClient.getCollectionRoles(collectionId);

    // then
    assertThat(roles.size()).isEqualTo(1);
    final IdentityWithMetadataResponseDto identityRestDto = roles.get(0).getIdentity();
    assertThat(identityRestDto).isInstanceOf(UserDto.class);
    final UserDto userDto = (UserDto) identityRestDto;
    assertThat(userDto.getId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(userDto.getFirstName()).isEqualTo(DEFAULT_FIRSTNAME);
    assertThat(userDto.getLastName()).isEqualTo(DEFAULT_LASTNAME);
    assertThat(userDto.getName())
      .isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(userDto.getEmail()).endsWith(DEFAULT_EMAIL_DOMAIN);
  }

  @Test
  public void getRolesContainsGroupMetadata() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_MISS_PIGGY_ID);
    authorizationClient.createGroupAndAddUsers(TEST_GROUP_ID, USER_KERMIT_ID, USER_MISS_PIGGY_ID);

    GroupDto testGroupDto = new GroupDto(TEST_GROUP_ID);
    embeddedOptimizeExtension.getIdentityService().addIdentity(testGroupDto);

    final CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(
      new IdentityDto(TEST_GROUP_ID, IdentityType.GROUP),
      RoleType.EDITOR
    );
    collectionClient.addRolesToCollection(collectionId, roleDto);

    // when
    List<CollectionRoleResponseDto> roles = collectionClient.getCollectionRoles(collectionId);

    // then
    final List<IdentityWithMetadataResponseDto> groupIdentities = roles.stream()
      .map(CollectionRoleResponseDto::getIdentity)
      .filter(identityDto -> identityDto instanceof GroupDto)
      .collect(Collectors.toList());
    assertThat(groupIdentities.size()).isEqualTo(1);

    final GroupDto expectedGroupDto = new GroupDto(TEST_GROUP_ID, null, 2L);
    final GroupDto actualGroupDto = (GroupDto) groupIdentities.get(0);
    assertThat(actualGroupDto).isEqualTo(expectedGroupDto);
  }

  @Test
  public void getRolesNoGroupMetadataAvailable() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    GroupDto testGroupDto = new GroupDto(TEST_GROUP_ID);
    embeddedOptimizeExtension.getIdentityService().addIdentity(testGroupDto);

    final CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(
      new IdentityDto(TEST_GROUP_ID, IdentityType.GROUP),
      RoleType.EDITOR
    );
    collectionClient.addRolesToCollection(collectionId, roleDto);

    // when
    List<CollectionRoleResponseDto> roles = collectionClient.getCollectionRoles(collectionId);

    // then
    final List<IdentityWithMetadataResponseDto> groupIdentities = roles.stream()
      .map(CollectionRoleResponseDto::getIdentity)
      .filter(identityDto -> identityDto instanceof GroupDto)
      .collect(Collectors.toList());
    assertThat(groupIdentities.size()).isEqualTo(1);

    final GroupDto groupDto = (GroupDto) groupIdentities.get(0);
    assertThat(groupDto.getName()).isEqualTo(TEST_GROUP_ID);
  }

  @Test
  public void addUserRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    final CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(
      new IdentityDto(USER_KERMIT_ID, IdentityType.USER),
      RoleType.EDITOR
    );
    collectionClient.addRolesToCollection(collectionId, roleDto);

    // then
    final List<String> roleIds = collectionClient.getCollectionRoleIdDtos(collectionId)
      .stream()
      .map(IdResponseDto::getId)
      .collect(Collectors.toList());
    assertThat(roleIds).contains(roleDto.getId());
  }

  @Test
  public void addExistingUserIdWithoutIdentityType() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    final CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(
      new IdentityDto(USER_KERMIT_ID, null),
      RoleType.EDITOR
    );
    collectionClient.addRolesToCollection(collectionId, roleDto);
    final CollectionRoleRequestDto expectedRoleDto = new CollectionRoleRequestDto(
      new IdentityDto(USER_KERMIT_ID, IdentityType.USER),
      RoleType.EDITOR
    );

    // then
    final List<String> roleIds = collectionClient.getCollectionRoleIdDtos(collectionId)
      .stream()
      .map(IdResponseDto::getId)
      .collect(Collectors.toList());
    assertThat(roleIds).contains(expectedRoleDto.getId());
  }

  @Test
  public void addExistingGroupIdWithoutIdentityType() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    GroupDto testGroupDto = new GroupDto(TEST_GROUP_ID);
    embeddedOptimizeExtension.getIdentityService().addIdentity(testGroupDto);

    // when
    final CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(
      new IdentityDto(TEST_GROUP_ID, null),
      RoleType.EDITOR
    );
    collectionClient.addRolesToCollection(collectionId, roleDto);
    final CollectionRoleRequestDto expectedRoleDto = new CollectionRoleRequestDto(
      new IdentityDto(TEST_GROUP_ID, IdentityType.GROUP),
      RoleType.EDITOR
    );

    // then
    final List<String> roleIds = collectionClient.getCollectionRoleIdDtos(collectionId)
      .stream()
      .map(IdResponseDto::getId)
      .collect(Collectors.toList());
    assertThat(roleIds).contains(expectedRoleDto.getId());
  }

  @Test
  public void addNonExistentIdWithoutIdentityType() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    // when
    final CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(
      new IdentityDto(TEST_GROUP_ID, null),
      RoleType.EDITOR
    );
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRolesToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void addMultipleUserRolesAtOnce() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_MISS_PIGGY_ID);

    // when
    final CollectionRoleRequestDto kermitRoleDto = new CollectionRoleRequestDto(
      new IdentityDto(USER_KERMIT_ID, IdentityType.USER),
      RoleType.EDITOR
    );
    final CollectionRoleRequestDto missPiggyRoleDto = new CollectionRoleRequestDto(
      new IdentityDto(USER_MISS_PIGGY_ID, IdentityType.USER),
      RoleType.VIEWER
    );
    collectionClient.addRolesToCollection(collectionId, kermitRoleDto, missPiggyRoleDto);

    // then
    final List<String> roleIds = collectionClient.getCollectionRoleIdDtos(collectionId)
      .stream()
      .map(IdResponseDto::getId)
      .collect(Collectors.toList());
    assertThat(roleIds).contains(kermitRoleDto.getId(), missPiggyRoleDto.getId());
  }

  @Test
  public void addMultipleUserRolesInARow() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_MISS_PIGGY_ID);

    // when
    final CollectionRoleRequestDto kermitRoleDto = new CollectionRoleRequestDto(
      new IdentityDto(USER_KERMIT_ID, IdentityType.USER),
      RoleType.EDITOR
    );
    collectionClient.addRolesToCollection(collectionId, kermitRoleDto);

    final CollectionRoleRequestDto missPiggyRoleDto = new CollectionRoleRequestDto(
      new IdentityDto(USER_MISS_PIGGY_ID, IdentityType.USER),
      RoleType.VIEWER
    );
    collectionClient.addRolesToCollection(collectionId, missPiggyRoleDto);

    // then
    final List<String> roleIds = collectionClient.getCollectionRoleIdDtos(collectionId)
      .stream()
      .map(IdResponseDto::getId)
      .collect(Collectors.toList());
    assertThat(roleIds).contains(kermitRoleDto.getId(), missPiggyRoleDto.getId());
  }

  @Test
  public void addUserRoleFailsForUnknownUsers() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    // when
    final CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(
      new IdentityDto(USER_KERMIT_ID, IdentityType.USER),
      RoleType.EDITOR
    );
    final Response addRoleResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRolesToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(addRoleResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void addUserRoleFailsNotExistingUser() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    // when
    final CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(
      new IdentityDto(USER_KERMIT_ID, IdentityType.USER),
      RoleType.EDITOR
    );
    final Response addRoleResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRolesToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(addRoleResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void addMultipleUserRolesFailsNotExistingUser() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    final CollectionRoleRequestDto kermitRoleDto = new CollectionRoleRequestDto(
      new IdentityDto(USER_KERMIT_ID, IdentityType.USER),
      RoleType.EDITOR
    );
    final CollectionRoleRequestDto missPiggyRoleDto = new CollectionRoleRequestDto(
      new IdentityDto(USER_MISS_PIGGY_ID, IdentityType.USER),
      RoleType.VIEWER
    );
    final Response addRoleResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRolesToCollectionRequest(collectionId, kermitRoleDto, missPiggyRoleDto)
      .execute();

    // then
    assertThat(addRoleResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(collectionClient.getCollectionRoleIdDtos(collectionId))
      .extracting(IdResponseDto::getId)
      .doesNotContain(kermitRoleDto.getId(), missPiggyRoleDto.getId());
  }

  @Test
  public void addUserRoleFailsForUnauthorizedUser() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final UserDto testUser = new UserDto("Test", "User");
    embeddedOptimizeExtension.getIdentityService().addIdentity(testUser);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    final CollectionRoleRequestDto kermitRoleDto = new CollectionRoleRequestDto(
      new IdentityDto(USER_KERMIT_ID, IdentityType.USER),
      RoleType.MANAGER
    );
    collectionClient.addRolesToCollection(collectionId, kermitRoleDto);

    // when
    final CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(
      testUser,
      RoleType.EDITOR
    );
    final Response addRoleResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(USER_KERMIT_ID, USER_KERMIT_ID)
      .buildAddRolesToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(addRoleResponse.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void addGroupRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    GroupDto testGroupDto = new GroupDto(TEST_GROUP_ID);
    embeddedOptimizeExtension.getIdentityService().addIdentity(testGroupDto);

    // when
    final CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(
      new IdentityDto(TEST_GROUP_ID, IdentityType.GROUP),
      RoleType.EDITOR
    );
    collectionClient.addRolesToCollection(collectionId, roleDto);

    // then
    final List<String> roleIds = collectionClient.getCollectionRoleIdDtos(collectionId)
      .stream()
      .map(IdResponseDto::getId)
      .collect(Collectors.toList());
    assertThat(roleIds).contains(roleDto.getId());
  }

  @Test
  public void addGroupRoleFailsNotExistingGroup() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    // when
    final CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(
      new IdentityDto(TEST_GROUP_ID, IdentityType.GROUP),
      RoleType.EDITOR
    );
    final Response addRoleResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRolesToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(addRoleResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void duplicateIdentityRoleIsRejected_singleDuplicate() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final List<CollectionRoleResponseDto> expectedRoles = collectionClient.getCollectionRoles(collectionId);

    // when
    final CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(
      // there is already an entry for the default user who created the collection
      new IdentityDto(DEFAULT_USERNAME, IdentityType.USER),
      RoleType.EDITOR
    );
    ConflictResponseDto conflictResponseDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRolesToCollectionRequest(collectionId, roleDto)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

    // then
    assertThat(conflictResponseDto.getErrorCode()).isEqualTo(OptimizeCollectionConflictException.ERROR_CODE);
    assertThat(conflictResponseDto.getErrorMessage()).isNotNull();

    assertThat(collectionClient.getCollectionRoles(collectionId)).isEqualTo(expectedRoles);
  }

  @Test
  public void duplicateIdentityRoleIsRejected_firstRoleIsNewAndSecondIsDuplicate() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final List<CollectionRoleResponseDto> expectedRoles = collectionClient.getCollectionRoles(collectionId);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    final CollectionRoleRequestDto kermit = new CollectionRoleRequestDto(
      new IdentityDto(USER_KERMIT_ID, IdentityType.USER),
      RoleType.EDITOR
    );
    final CollectionRoleRequestDto demo = new CollectionRoleRequestDto(
      // there is already an entry for the default user who created the collection
      new IdentityDto(DEFAULT_USERNAME, IdentityType.USER),
      RoleType.EDITOR
    );
    ConflictResponseDto conflictResponseDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRolesToCollectionRequest(collectionId, kermit, demo)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

    // then
    assertThat(conflictResponseDto.getErrorCode()).isEqualTo(OptimizeCollectionConflictException.ERROR_CODE);
    assertThat(conflictResponseDto.getErrorMessage()).isNotNull();

    assertThat(collectionClient.getCollectionRoles(collectionId)).isEqualTo(expectedRoles);
  }

  @Test
  public void roleCanGetUpdated() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    final IdentityDto identityDto = new IdentityDto(USER_KERMIT_ID, IdentityType.USER);
    final CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(identityDto, RoleType.EDITOR);
    collectionClient.addRolesToCollection(collectionId, roleDto);

    final CollectionRoleUpdateRequestDto updatedRoleDto = new CollectionRoleUpdateRequestDto(RoleType.VIEWER);
    updateRoleOnCollection(collectionId, roleDto.getId(), updatedRoleDto);

    // then
    final List<String> roleIds = collectionClient.getCollectionRoleIdDtos(collectionId)
      .stream()
      .map(IdResponseDto::getId)
      .collect(Collectors.toList());
    assertThat(roleIds).hasSize(2)
      .contains(roleDto.getId());
  }

  @Test
  public void updateRoleFailsForUnauthorizedUser() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_MISS_PIGGY_ID);

    // when
    final IdentityDto identityDto = new IdentityDto(USER_MISS_PIGGY_ID, IdentityType.USER);
    final CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(identityDto, RoleType.EDITOR);
    collectionClient.addRolesToCollection(collectionId, roleDto);

    final CollectionRoleUpdateRequestDto updatedRoleDto = new CollectionRoleUpdateRequestDto(RoleType.VIEWER);
    final Response updateRoleResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(USER_KERMIT_ID, USER_KERMIT_ID)
      .buildUpdateRoleToCollectionRequest(collectionId, roleDto.getId(), updatedRoleDto)
      .execute();

    // then
    assertThat(updateRoleResponse.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void updatingLastManagerFails() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final List<CollectionRoleResponseDto> expectedRoles = collectionClient.getCollectionRoles(collectionId);
    final CollectionRoleResponseDto roleEntryDto = expectedRoles.get(0);

    // when
    final CollectionRoleUpdateRequestDto updatedRoleDto = new CollectionRoleUpdateRequestDto(RoleType.EDITOR);
    ConflictResponseDto conflictResponseDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateRoleToCollectionRequest(collectionId, roleEntryDto.getId(), updatedRoleDto)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

    // then
    assertThat(conflictResponseDto.getErrorCode()).isEqualTo(OptimizeCollectionConflictException.ERROR_CODE);
    assertThat(conflictResponseDto.getErrorMessage()).isNotNull();

    assertThat(collectionClient.getCollectionRoles(collectionId)).isEqualTo(expectedRoles);
  }

  @Test
  public void updatingNonPresentRoleFails() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final List<CollectionRoleResponseDto> expectedRoles = collectionClient.getCollectionRoles(collectionId);
    final String notExistingRoleEntryId = "USER:abc";

    // when
    final CollectionRoleUpdateRequestDto updatedRoleDto = new CollectionRoleUpdateRequestDto(RoleType.EDITOR);
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateRoleToCollectionRequest(collectionId, notExistingRoleEntryId, updatedRoleDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

    assertThat(collectionClient.getCollectionRoles(collectionId)).isEqualTo(expectedRoles);
  }

  @Test
  public void roleCanGetDeleted() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    final IdentityDto identityDto = new IdentityDto(USER_KERMIT_ID, IdentityType.USER);
    final CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(identityDto, RoleType.EDITOR);
    collectionClient.addRolesToCollection(collectionId, roleDto);
    deleteRoleFromCollection(collectionId, roleDto.getId());

    // then
    final List<String> roleIds = collectionClient.getCollectionRoleIdDtos(collectionId)
      .stream()
      .map(IdResponseDto::getId)
      .collect(Collectors.toList());
    assertThat(roleIds.size()).isEqualTo(1);
    assertThat(roleIds).doesNotContain(roleDto.getId());
  }

  @Test
  public void deletingLastManagerFails() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final List<CollectionRoleResponseDto> expectedRoles = collectionClient.getCollectionRoles(collectionId);
    final CollectionRoleResponseDto roleEntryDto = expectedRoles.get(0);

    // when
    ConflictResponseDto conflictResponseDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteRoleToCollectionRequest(collectionId, roleEntryDto.getId())
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

    // then
    assertThat(conflictResponseDto.getErrorCode()).isEqualTo(OptimizeCollectionConflictException.ERROR_CODE);

    final List<CollectionRoleResponseDto> actualRoles = collectionClient.getCollectionRoles(collectionId);
    assertThat(actualRoles).isEqualTo(expectedRoles);
  }

  @Test
  public void bulkDeleteCollectionRolesNoAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteCollectionRolesRequest(Collections.emptyList(), null)
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void bulkDeleteCollectionRolesEmptyList() {
    // when
    Response response = createAndExecuteBuildDeleteCollectionRolesRequest(Collections.emptyList(), null);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void bulkDeleteCollectionRolesNullList() {
    // when
    String collectionId = collectionClient.createNewCollection();
    Response response = createAndExecuteBuildDeleteCollectionRolesRequest(null, collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void bulkDeleteCollectionRolesCollectionDoesntExist() {
    // when
    Response response = createAndExecuteBuildDeleteCollectionRolesRequest(Collections.emptyList(), "doesntExist");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void bulkDeleteCollectionRolesNoUserAuthorizationToCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_MISS_PIGGY_ID);
    authorizationClient.createGroupAndGrantOptimizeAccess(TEST_GROUP_ID, TEST_GROUP_NAME);
    String collectionId = collectionClient.createNewCollection();

    final CollectionRoleRequestDto roleDto1 = new CollectionRoleRequestDto(
      new IdentityDto(TEST_GROUP_ID, IdentityType.GROUP), RoleType.VIEWER
    );

    final CollectionRoleRequestDto roleDto2 = new CollectionRoleRequestDto(
      new IdentityDto(USER_MISS_PIGGY_ID, IdentityType.USER), RoleType.VIEWER
    );
    collectionClient.addRolesToCollection(collectionId, roleDto1, roleDto2);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteCollectionRolesRequest(Arrays.asList(roleDto1.getId(), roleDto2.getId()), collectionId)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void bulkDeleteCollectionRoles() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createGroupAndGrantOptimizeAccess(TEST_GROUP_ID, TEST_GROUP_NAME);
    String collectionId = collectionClient.createNewCollection();

    final CollectionRoleRequestDto groupViewerRole = new CollectionRoleRequestDto(
      new IdentityDto(TEST_GROUP_ID, IdentityType.GROUP), RoleType.VIEWER
    );

    final CollectionRoleRequestDto userViewerRole = new CollectionRoleRequestDto(
      new IdentityDto(USER_KERMIT_ID, IdentityType.USER), RoleType.VIEWER
    );

    collectionClient.addRolesToCollection(collectionId, groupViewerRole, userViewerRole);

    // when
    Response response = createAndExecuteBuildDeleteCollectionRolesRequest(Arrays.asList(
      groupViewerRole.getId(),
      userViewerRole.getId()
    ), collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    final List<CollectionRoleResponseDto> actualRoles = collectionClient.getCollectionRoles(collectionId);
    assertThat(actualRoles.size()).isEqualTo(1);
    assertThat(actualRoles).extracting(role -> role.getIdentity().getId()).containsExactly(DEFAULT_USERNAME);
  }

  @Test
  public void bulkDeleteDoesNotDeleteLastManager() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final List<CollectionRoleResponseDto> expectedRoles = collectionClient.getCollectionRoles(collectionId);
    final CollectionRoleResponseDto demoManagerRole = expectedRoles.get(0);
    final CollectionRoleRequestDto groupViewerRole = new CollectionRoleRequestDto(
      new IdentityDto(TEST_GROUP_ID, IdentityType.GROUP), RoleType.VIEWER
    );

    // when
    Response response = createAndExecuteBuildDeleteCollectionRolesRequest(Arrays.asList(
      demoManagerRole.getId(),
      groupViewerRole.getId()
    ), collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    final List<CollectionRoleResponseDto> actualRoles = collectionClient.getCollectionRoles(collectionId);
    assertThat(actualRoles).isEqualTo(expectedRoles);
    assertThat(actualRoles).extracting(role -> role.getIdentity().getId()).containsExactly(DEFAULT_USERNAME);
    String message = String.format(
      "Could not delete role with id %s, because the user with that id is a manager.",
      demoManagerRole.getId()
    );
    logCapturer.assertContains(message);
  }

  @Test
  public void bulkDeleteDoesNotAbortWhenRoleIdDoesNotExist() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createGroupAndGrantOptimizeAccess(TEST_GROUP_ID, TEST_GROUP_NAME);
    String collectionId = collectionClient.createNewCollection();

    final CollectionRoleRequestDto groupViewerRole = new CollectionRoleRequestDto(
      new IdentityDto(TEST_GROUP_ID, IdentityType.GROUP), RoleType.VIEWER
    );

    final CollectionRoleRequestDto userViewerRole = new CollectionRoleRequestDto(
      new IdentityDto(USER_KERMIT_ID, IdentityType.USER), RoleType.VIEWER
    );

    collectionClient.addRolesToCollection(collectionId, groupViewerRole, userViewerRole);

    // when
    Response response = createAndExecuteBuildDeleteCollectionRolesRequest(Arrays.asList(
      groupViewerRole.getId(),
      "doesNotExist",
      userViewerRole.getId()
    ), collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    final List<CollectionRoleResponseDto> actualRoles = collectionClient.getCollectionRoles(collectionId);
    assertThat(actualRoles.size()).isEqualTo(1);
    assertThat(actualRoles).extracting(role -> role.getIdentity().getId()).containsExactly(DEFAULT_USERNAME);
    logCapturer.assertContains("Could not delete role with id doesNotExist. The role is already deleted.");
  }

  private void updateRoleOnCollection(final String collectionId,
                                      final String roleEntryId,
                                      final CollectionRoleUpdateRequestDto updateDto) {
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateRoleToCollectionRequest(collectionId, roleEntryId, updateDto)
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  private void deleteRoleFromCollection(final String collectionId,
                                        final String roleEntryId) {
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteRoleToCollectionRequest(collectionId, roleEntryId)
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  private Response createAndExecuteBuildDeleteCollectionRolesRequest(List<String> collectionRoles, String collectionId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteCollectionRolesRequest(collectionRoles, collectionId)
      .execute();
  }
}
