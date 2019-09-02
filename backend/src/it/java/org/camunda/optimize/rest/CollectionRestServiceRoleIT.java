/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.apache.http.HttpStatus;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class CollectionRestServiceRoleIT {

  public EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineIntegrationRule).around(embeddedOptimizeRule);

  @Test
  public void partialCollectionUpdateDoesNotAffectRoles() {
    //given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);

    // when
    final PartialCollectionUpdateDto collectionRenameDto = new PartialCollectionUpdateDto("Test");
    Response response = embeddedOptimizeRule
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
    List<CollectionRoleDto> roles = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetRolesToCollectionRequest(collectionId)
      .executeAndReturnList(CollectionRoleDto.class, 200);

    // then
    assertThat(roles.size(), is(1));
    assertThat(roles, is(expectedCollection.getData().getRoles()));
  }

  @Test
  public void roleCanBeAdded() {
    // given
    final String collectionId = createNewCollection();

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(
      new IdentityDto("kermit", IdentityType.USER),
      RoleType.EDITOR
    );
    IdDto idDto = addRoleToCollection(collectionId, roleDto);

    // then
    assertThat(idDto.getId(), is(roleDto.getId()));
    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection.getData().getRoles(), hasItem(roleDto));
  }

  @Test
  public void duplicateIdentityRoleIsRejected() {
    // given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(
      // there is already an entry for the default user who created the collection
      new IdentityDto(DEFAULT_USERNAME, IdentityType.USER),
      RoleType.EDITOR
    );
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_CONFLICT));

    assertThat(getCollection(collectionId), is(expectedCollection));
  }

  @Test
  public void roleCanGetUpdated() {
    // given
    final String collectionId = createNewCollection();

    // when
    final IdentityDto identityDto = new IdentityDto("kermit", IdentityType.USER);
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
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateRoleToCollectionRequest(collectionId, roleEntryDto.getId(), updatedRoleDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_CONFLICT));

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
    Response response = embeddedOptimizeRule
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

    // when
    final IdentityDto identityDto = new IdentityDto("kermit", IdentityType.USER);
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
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteRoleToCollectionRequest(collectionId, roleEntryDto.getId())
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_CONFLICT));

    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection, is(expectedCollection));
  }

  private IdDto addRoleToCollection(final String collectionId, final CollectionRoleDto roleDto) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute(IdDto.class, 200);
  }

  private void updateRoleOnCollection(final String collectionId,
                                      final String roleEntryId,
                                      final CollectionRoleUpdateDto updateDto) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateRoleToCollectionRequest(collectionId, roleEntryId, updateDto)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  private void deleteRoleFromCollection(final String collectionId,
                                        final String roleEntryId) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteRoleToCollectionRequest(collectionId, roleEntryId)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  private SimpleCollectionDefinitionDto getCollection(final String id) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetCollectionRequest(id)
      .execute(SimpleCollectionDefinitionDto.class, 200);
  }

  private String createNewCollection() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }
}
