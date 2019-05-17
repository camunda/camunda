/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionUpdateDto;
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
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
public class CollectionService implements ReportReferencingService, DashboardReferencingService {

  private final CollectionWriter collectionWriter;
  private final CollectionReader collectionReader;

  public IdDto createNewCollectionAndReturnId(String userId) {
    return collectionWriter.createNewCollectionAndReturnId(userId);
  }

  public void updatePartialCollection(String collectionId,
                                      String userId,
                                      PartialCollectionUpdateDto collectionUpdate) {
    CollectionDefinitionUpdateDto updateDto = new CollectionDefinitionUpdateDto();
    updateDto.setName(collectionUpdate.getName());

    if (collectionUpdate.getData() != null) {
      final PartialCollectionDataDto collectionDataDto = new PartialCollectionDataDto();
      collectionDataDto.setConfiguration(collectionUpdate.getData().getConfiguration());
      collectionDataDto.setEntities(collectionUpdate.getData().getEntities());
      updateDto.setData(collectionDataDto);
    }

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
