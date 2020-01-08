/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class CollectionRestServiceIT extends AbstractIT {

  @Test
  public void createNewCollectionWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
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
    IdDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200);

    // then the status code is okay
    assertThat(idDto, is(notNullValue()));

    // and saved Collection has expected properties
    CollectionDefinitionRestDto savedCollectionDto = collectionClient.getCollectionById(idDto.getId());
    assertThat(savedCollectionDto.getName(), is(CollectionWriter.DEFAULT_COLLECTION_NAME));
    assertThat(savedCollectionDto.getData().getConfiguration(), equalTo(Collections.EMPTY_MAP));
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
    IdDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCollectionRequestWithPartialDefinition(partialCollectionDefinitionDto)
      .execute(IdDto.class, 200);

    // then the status code is okay
    assertThat(idDto, is(notNullValue()));

    // and saved Collection has expected properties
    CollectionDefinitionRestDto savedCollectionDto = collectionClient.getCollectionById(idDto.getId());
    assertThat(savedCollectionDto.getName(), is(collectionName));
    assertThat(savedCollectionDto.getData().getConfiguration(), is(configMap));
  }

  @Test
  public void updateCollectionWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
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
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdatePartialCollectionRequest("NonExistingId", new PartialCollectionDefinitionDto())
      .execute();

    // given
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void updateNameOfCollection() {
    //given
    String id = collectionClient.createNewCollection();
    final PartialCollectionDefinitionDto collectionRenameDto = new PartialCollectionDefinitionDto("Test");

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdatePartialCollectionRequest(id, collectionRenameDto)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void getCollectionWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
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
    String id = collectionClient.createNewCollection();

    // when
    CollectionDefinitionRestDto collection = collectionClient.getCollectionById(id);
    List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(id);

    // then
    assertThat(collection, is(notNullValue()));
    assertThat(collection.getId(), is(id));
    assertThat(collectionEntities.size(), is(0));
  }

  @Test
  public void getCollectionForNonExistingIdThrowsError() {
    // when
    String response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCollectionRequest("fooid")
      .execute(String.class, 404);

    // then the status code is okay
    assertThat(response.contains("Collection does not exist!"), is(true));
  }

  @Test
  public void deleteCollectionWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
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
    String id = collectionClient.createNewCollection();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteCollectionRequest(id)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));

    final Response getByIdResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCollectionRequest(id)
      .execute();
    assertThat(getByIdResponse.getStatus(), is(404));
  }

  @Test
  public void deleteNonExitingCollection() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteCollectionRequest("NonExistingId")
      .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }
}
