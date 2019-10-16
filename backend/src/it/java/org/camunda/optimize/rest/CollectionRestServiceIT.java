/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class CollectionRestServiceIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Test
  public void createNewCollectionWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildCreateCollectionRequest()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewCollection() {
    // when
    IdDto idDto = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200);

    // then the status code is okay
    assertThat(idDto, is(notNullValue()));

    // and saved Collection has expected properties
    ResolvedCollectionDefinitionDto savedCollectionDto = getCollectionById(idDto.getId());
    assertThat(savedCollectionDto.getName(), is(CollectionWriter.DEFAULT_COLLECTION_NAME));
    assertThat(savedCollectionDto.getData().getConfiguration(), equalTo(Collections.EMPTY_MAP));
    assertThat(savedCollectionDto.getData().getRoles().size(), is(1));
    assertThat(savedCollectionDto.getData().getRoles().get(0).getRole(), is(RoleType.MANAGER));
    assertThat(savedCollectionDto.getData().getRoles().get(0).getIdentity().getType(), is(IdentityType.USER));
  }

  @Test
  public void createNewCollectionWithPartialDefinition() {
    // when
    String collectionName = "some collection";
    Map<String, String> configMap = Collections.singletonMap("Foo", "Bar");
    PartialCollectionDefinitionDto partialCollectionDefinitionDto = new PartialCollectionDefinitionDto();
    partialCollectionDefinitionDto.setName(collectionName);
    PartialCollectionDataDto partialCollectionDataDto = new PartialCollectionDataDto();
    partialCollectionDataDto.setConfiguration(configMap);
    partialCollectionDefinitionDto.setData(partialCollectionDataDto);
    IdDto idDto = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateCollectionRequestWithPartialDefinition(partialCollectionDefinitionDto)
      .execute(IdDto.class, 200);

    // then the status code is okay
    assertThat(idDto, is(notNullValue()));

    // and saved Collection has expected properties
    ResolvedCollectionDefinitionDto savedCollectionDto = getCollectionById(idDto.getId());
    assertThat(savedCollectionDto.getName(), is(collectionName));
    assertThat(savedCollectionDto.getData().getConfiguration(), is(configMap));
    assertThat(savedCollectionDto.getData().getRoles().size(), is(1));
    assertThat(savedCollectionDto.getData().getRoles().get(0).getRole(), is(RoleType.MANAGER));
    assertThat(savedCollectionDto.getData().getRoles().get(0).getIdentity().getType(), is(IdentityType.USER));
  }

  @Test
  public void updateCollectionWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildUpdatePartialCollectionRequest("1", null)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateNonExistingCollection() {
    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdatePartialCollectionRequest("NonExistingId", new PartialCollectionDefinitionDto())
      .execute();

    // given
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void updateNameOfCollection() {
    //given
    String id = addEmptyCollectionToOptimize();
    final PartialCollectionDefinitionDto collectionRenameDto = new PartialCollectionDefinitionDto("Test");

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdatePartialCollectionRequest(id, collectionRenameDto)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void getCollectionWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetCollectionRequest("asdf")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getCollection() {
    //given
    String id = addEmptyCollectionToOptimize();

    // when
    ResolvedCollectionDefinitionDto collection = getCollectionById(id);

    // then
    assertThat(collection, is(notNullValue()));
    assertThat(collection.getId(), is(id));
    assertThat(collection.getData().getEntities().size(), is(0));
  }

  @Test
  public void getCollectionForNonExistingIdThrowsError() {
    // when
    String response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetCollectionRequest("fooid")
      .execute(String.class, 404);

    // then the status code is okay
    assertThat(response.contains("Collection does not exist!"), is(true));
  }

  @Test
  public void deleteCollectionWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildDeleteCollectionRequest("1124")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNewCollection() {
    //given
    String id = addEmptyCollectionToOptimize();

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildDeleteCollectionRequest(id)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));

    final Response getByIdResponse = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetCollectionRequest(id)
      .execute();
    assertThat(getByIdResponse.getStatus(), is(404));
  }

  @Test
  public void deleteNonExitingCollection() {
    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildDeleteCollectionRequest("NonExistingId")
      .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  private String addEmptyCollectionToOptimize() {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private ResolvedCollectionDefinitionDto getCollectionById(final String collectionId) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(ResolvedCollectionDefinitionDto.class, 200);
  }
}
