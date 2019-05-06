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
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil;
import org.camunda.optimize.service.es.reader.CollectionReader;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.relations.DashboardReferencingService;
import org.camunda.optimize.service.relations.ReportReferencingService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CollectionService implements ReportReferencingService, DashboardReferencingService {

  private static final Logger logger = LoggerFactory.getLogger(CollectionService.class);

  private final CollectionWriter collectionWriter;
  private final CollectionReader collectionReader;

  @Autowired
  public CollectionService(final CollectionWriter collectionWriter,
                           final CollectionReader collectionReader) {
    this.collectionWriter = collectionWriter;
    this.collectionReader = collectionReader;
  }

  public IdDto createNewCollectionAndReturnId(String userId) {
    return collectionWriter.createNewCollectionAndReturnId(userId);
  }

  public void updateNameOfCollection(String collectionId, String name, String userId) {
    CollectionDefinitionUpdateDto updateDto = new CollectionDefinitionUpdateDto();
    updateDto.setName(name);
    updateDto.setLastModifier(userId);
    updateDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    collectionWriter.updateCollection(updateDto, collectionId);
  }

  public void addEntityToCollection(String collectionId, String entityId, String userId) {
    collectionWriter.addEntityToCollection(collectionId, entityId, userId);
  }

  public void removeEntityFromCollection(String collectionId, String entityId, String userId) {
    collectionWriter.removeEntityFromCollection(collectionId, entityId, userId);
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

  public void removeEntityFromAllCollections(String entityId) {
    collectionWriter.removeEntityFromAllCollections(entityId);
  }

  public void deleteCollection(String collectionId) {
    collectionWriter.deleteCollection(collectionId);
  }

  public List<SimpleCollectionDefinitionDto> findFirstCollectionsForEntity(String entityId) {
    return collectionReader.findFirstCollectionsForEntity(entityId);
  }


  @Override
  public Set<ConflictedItemDto> getConflictedItemsForDashboardDelete(final DashboardDefinitionDto definition) {
    return mapCollectionsToConflictingItems(findFirstCollectionsForEntity(definition.getId()));
  }

  @Override
  public void handleDashboardDeleted(final DashboardDefinitionDto definition) {
    removeEntityFromAllCollections(definition.getId());
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForDashboardUpdate(final DashboardDefinitionDto currentDefinition,
                                                                     final DashboardDefinitionDto updateDefinition) {
    //NOOP
    return Collections.emptySet();
  }

  @Override
  public void handleDashboardUpdated(final String id, final DashboardDefinitionDto updateDefinition) {
    //NOOP
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportDelete(final ReportDefinitionDto reportDefinition) {
    return mapCollectionsToConflictingItems(findFirstCollectionsForEntity(reportDefinition.getId()));
  }

  @Override
  public void handleReportDeleted(final ReportDefinitionDto reportDefinition) {
    removeEntityFromAllCollections(reportDefinition.getId());
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportUpdate(final ReportDefinitionDto currentDefinition,
                                                                  final ReportDefinitionDto updateDefinition) {
    //NOOP
    return Collections.emptySet();
  }

  @Override
  public void handleReportUpdated(final String id, final ReportDefinitionDto updateDefinition) {
    //NOOP
  }

  private Set<ConflictedItemDto> mapCollectionsToConflictingItems(List<SimpleCollectionDefinitionDto> collections) {
    return collections.stream()
      .map(collection -> new ConflictedItemDto(
        collection.getId(), ConflictedItemType.COLLECTION, collection.getName()
      ))
      .collect(Collectors.toSet());
  }
}
