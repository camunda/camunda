/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.apache.http.HttpStatus;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class CollectionRestServiceRoleIT {

  private static final String USER_KERMIT = "kermit";
  private static final String TEST_GROUP = "testGroup";
  private static final String USER_MISS_PIGGY = "MissPiggy";

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Test
  public void partialCollectionUpdateDoesNotAffectRoles() {
    //given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);

    // when
    final PartialCollectionDefinitionDto collectionRenameDto = new PartialCollectionDefinitionDto("Test");
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(204));
    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection.getData().getRoles(), is(expectedCollection.getData().getRoles()));
  }

  @Test
  public void getRoles() {
    //given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);

    // when
    List<CollectionRoleDto> roles = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetRolesToCollectionRequest(collectionId)
      .executeAndReturnList(CollectionRoleDto.class, 200);

    // then
    assertThat(roles.size(), is(1));
    assertThat(roles, is(expectedCollection.getData().getRoles()));
  }

  @Test
  public void addUserRole() {
    // given
    final String collectionId = createNewCollection();
    engineIntegrationExtensionRule.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtensionRule.grantUserOptimizeAccess(USER_KERMIT);

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(new UserDto(USER_KERMIT), RoleType.EDITOR);
    IdDto idDto = addRoleToCollection(collectionId, roleDto);

    // then
    assertThat(idDto.getId(), is(roleDto.getId()));
    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection.getData().getRoles(), hasItem(roleDto));
  }

  @Test
  public void addMultipleUserRoles() {
    // given
    final String collectionId = createNewCollection();
    engineIntegrationExtensionRule.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtensionRule.grantUserOptimizeAccess(USER_KERMIT);
    engineIntegrationExtensionRule.addUser(USER_MISS_PIGGY, USER_MISS_PIGGY);
    engineIntegrationExtensionRule.grantUserOptimizeAccess(USER_MISS_PIGGY);

    // when
    final CollectionRoleDto kermitRoleDto = new CollectionRoleDto(new UserDto(USER_KERMIT), RoleType.EDITOR);
    IdDto kermitRoleIdDto = addRoleToCollection(collectionId, kermitRoleDto);

    final CollectionRoleDto missPiggyRoleDto = new CollectionRoleDto(new UserDto(USER_MISS_PIGGY), RoleType.VIEWER);
    IdDto missPiggyIdDto = addRoleToCollection(collectionId, missPiggyRoleDto);

    // then
    assertThat(kermitRoleIdDto.getId(), is(kermitRoleDto.getId()));
    assertThat(missPiggyIdDto.getId(), is(missPiggyRoleDto.getId()));

    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection.getData().getRoles(), hasItems(kermitRoleDto, missPiggyRoleDto));
  }

  @Test
  public void addUserRoleFailsNotAuthorizedUser() {
    // given
    final String collectionId = createNewCollection();
    engineIntegrationExtensionRule.addUser(USER_KERMIT, USER_KERMIT);

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(new UserDto(USER_KERMIT), RoleType.EDITOR);
    final Response addRoleResponse = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(addRoleResponse.getStatus(), is(400));
  }

  @Test
  public void addUserRoleFailsNotExistingUser() {
    // given
    final String collectionId = createNewCollection();

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(new UserDto(USER_KERMIT), RoleType.EDITOR);
    final Response addRoleResponse = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(addRoleResponse.getStatus(), is(400));
  }

  @Test
  public void addGroupRole() {
    // given
    final String collectionId = createNewCollection();
    engineIntegrationExtensionRule.createGroup(TEST_GROUP);

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(new GroupDto(TEST_GROUP, null), RoleType.EDITOR);
    IdDto idDto = addRoleToCollection(collectionId, roleDto);

    // then
    assertThat(idDto.getId(), is(roleDto.getId()));
    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection.getData().getRoles(), hasItem(roleDto));
  }

  @Test
  public void addGroupRoleFailsNotExistingGroup() {
    // given
    final String collectionId = createNewCollection();

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(new UserDto(TEST_GROUP), RoleType.EDITOR);
    final Response addRoleResponse = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(addRoleResponse.getStatus(), is(400));
  }

  @Test
  public void duplicateIdentityRoleIsRejected() {
    // given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(
      // there is already an entry for the default user who created the collection
      new UserDto(DEFAULT_USERNAME),
      RoleType.EDITOR
    );
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_CONFLICT));
    final ConflictResponseDto conflictResponseDto = response.readEntity(ConflictResponseDto.class);
    assertThat(conflictResponseDto.getErrorMessage(), is(notNullValue()));

    assertThat(getCollection(collectionId), is(expectedCollection));
  }

  @Test
  public void roleCanGetUpdated() {
    // given
    final String collectionId = createNewCollection();
    engineIntegrationExtensionRule.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtensionRule.grantUserOptimizeAccess(USER_KERMIT);

    // when
    final IdentityDto identityDto = new UserDto(USER_KERMIT);
    final CollectionRoleDto roleDto = new CollectionRoleDto(identityDto, RoleType.EDITOR);
    addRoleToCollection(collectionId, roleDto);

    final CollectionRoleUpdateDto updatedRoleDto = new CollectionRoleUpdateDto(RoleType.VIEWER);
    updateRoleOnCollection(collectionId, roleDto.getId(), updatedRoleDto);

    // then
    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection.getData().getRoles().size(), is(2));
    assertThat(collection.getData().getRoles(), hasItem(new CollectionRoleDto(identityDto, RoleType.VIEWER)));
  }

  @Test
  public void updatingLastManagerFails() {
    // given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);
    final CollectionRoleDto roleEntryDto = expectedCollection.getData().getRoles().get(0);

    // when
    final CollectionRoleUpdateDto updatedRoleDto = new CollectionRoleUpdateDto(RoleType.EDITOR);
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdateRoleToCollectionRequest(collectionId, roleEntryDto.getId(), updatedRoleDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_CONFLICT));
    final ConflictResponseDto conflictResponseDto = response.readEntity(ConflictResponseDto.class);
    assertThat(conflictResponseDto.getErrorMessage(), is(notNullValue()));

    assertThat(getCollection(collectionId), is(expectedCollection));
  }

  @Test
  public void updatingNonPresentRoleFails() {
    // given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);
    final String notExistingRoleEntryId = "USER:abc";

    // when
    final CollectionRoleUpdateDto updatedRoleDto = new CollectionRoleUpdateDto(RoleType.EDITOR);
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdateRoleToCollectionRequest(collectionId, notExistingRoleEntryId, updatedRoleDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_NOT_FOUND));

    assertThat(getCollection(collectionId), is(expectedCollection));
  }

  @Test
  public void roleCanGetDeleted() {
    // given
    final String collectionId = createNewCollection();
    engineIntegrationExtensionRule.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtensionRule.grantUserOptimizeAccess(USER_KERMIT);

    // when
    final IdentityDto identityDto = new UserDto(USER_KERMIT);
    final CollectionRoleDto roleDto = new CollectionRoleDto(identityDto, RoleType.EDITOR);
    addRoleToCollection(collectionId, roleDto);
    deleteRoleFromCollection(collectionId, roleDto.getId());

    // then
    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection.getData().getRoles().size(), is(1));
    assertThat(collection.getData().getRoles(), not(hasItem(roleDto)));
  }

  @Test
  public void deletingLastManagerFails() {
    // given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);
    final CollectionRoleDto roleEntryDto = expectedCollection.getData().getRoles().get(0);

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildDeleteRoleToCollectionRequest(collectionId, roleEntryDto.getId())
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_CONFLICT));

    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection, is(expectedCollection));
  }

  private IdDto addRoleToCollection(final String collectionId, final CollectionRoleDto roleDto) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute(IdDto.class, 200);
  }

  private void updateRoleOnCollection(final String collectionId,
                                      final String roleEntryId,
                                      final CollectionRoleUpdateDto updateDto) {
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdateRoleToCollectionRequest(collectionId, roleEntryId, updateDto)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  private void deleteRoleFromCollection(final String collectionId,
                                        final String roleEntryId) {
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildDeleteRoleToCollectionRequest(collectionId, roleEntryId)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  private SimpleCollectionDefinitionDto getCollection(final String id) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetCollectionRequest(id)
      .execute(SimpleCollectionDefinitionDto.class, 200);
  }

  private String createNewCollection() {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }
}
