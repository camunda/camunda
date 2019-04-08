/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.collection;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil;
import org.camunda.optimize.service.es.reader.CollectionReader;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

@Component
public class CollectionService {

  private static final Logger logger = LoggerFactory.getLogger(CollectionService.class);

  private final CollectionWriter collectionWriter;
  private final CollectionReader collectionReader;

  @Autowired
  public CollectionService(final CollectionWriter collectionWriter, final CollectionReader collectionReader) {
    this.collectionWriter = collectionWriter;
    this.collectionReader = collectionReader;
  }

  public IdDto createNewCollectionAndReturnId(String userId) {
    return collectionWriter.createNewCollectionAndReturnId(userId);
  }

  public void updateCollection(SimpleCollectionDefinitionDto updatedCollection, String userId) {
    CollectionDefinitionUpdateDto updateDto = new CollectionDefinitionUpdateDto();
    updateDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    updateDto.setOwner(updatedCollection.getOwner());
    updateDto.setName(updatedCollection.getName());
    updateDto.setData(updatedCollection.getData());
    updateDto.setLastModifier(userId);
    updateDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    collectionWriter.updateCollection(updateDto, updatedCollection.getId());
  }

  public List<ResolvedCollectionDefinitionDto> getAllResolvedCollections(MultivaluedMap<String, String> queryParameters) {
    List<ResolvedCollectionDefinitionDto> resolvedConnections = collectionReader.getAllResolvedCollections();
    resolvedConnections = QueryParamAdjustmentUtil.adjustCollectionResultsToQueryParameters(
      resolvedConnections,
      queryParameters
    );
    return resolvedConnections;
  }

  public SimpleCollectionDefinitionDto getCollectionDefinition(String collectionId) {
    return collectionReader.getCollection(collectionId);
  }

  public void removeEntityFromCollection(String entityId) {
    collectionWriter.removeEntityFromCollections(entityId);
  }

  public void deleteCollection(String collectionId) {
    collectionWriter.deleteCollection(collectionId);
  }

  public List<SimpleCollectionDefinitionDto> findFirstCollectionsForEntity(String entityId) {
    return collectionReader.findFirstCollectionsForEntity(entityId);
  }
}
