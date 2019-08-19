/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil;
import org.camunda.optimize.service.es.reader.CollectionReader;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.OptimizeConflictException;
import org.camunda.optimize.service.relations.CollectionRelationService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;
import java.util.Set;

@AllArgsConstructor
@Component
public class CollectionService {

  private final CollectionWriter collectionWriter;
  private final CollectionReader collectionReader;
  private final CollectionRelationService collectionRelationService;


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
      updateDto.setData(collectionDataDto);
    }

    updateDto.setLastModifier(userId);
    updateDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    collectionWriter.updateCollection(updateDto, collectionId);
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

  public void deleteCollection(String collectionId, boolean force) throws OptimizeConflictException {
    if (!force) {
      final Set<ConflictedItemDto> conflictedItems = getConflictedItemsForDelete(collectionId);

      if (!conflictedItems.isEmpty()) {
        throw new OptimizeConflictException(conflictedItems);
      }
    }

    final SimpleCollectionDefinitionDto collectionDefinition = getCollectionDefinition(collectionId);
    collectionWriter.deleteCollection(collectionId);
    collectionRelationService.handleDeleted(collectionDefinition);
  }


  public CollectionRoleDto addRoleToCollection(final String collectionId, final CollectionRoleDto roleDto,
                                               final String userId)
    throws OptimizeConflictException {
    return collectionWriter.addRoleToCollection(collectionId, roleDto, userId);
  }

  public void updateRoleOfCollection(final String collectionId,
                                     final String roleEntryId,
                                     final CollectionRoleUpdateDto roleUpdateDto,
                                     final String userId) throws OptimizeConflictException {
    collectionWriter.updateRoleInCollection(collectionId, roleEntryId, roleUpdateDto, userId);
  }

  public void removeRoleFromCollection(String collectionId, String roleEntryId, String userId)
    throws OptimizeConflictException {
    collectionWriter.removeRoleFromCollection(collectionId, roleEntryId, userId);
  }

  public boolean existsCollection(String collectionId) {
    return collectionReader.checkIfCollectionExists(collectionId);
  }


  public ConflictResponseDto getDeleteConflictingItems(String collectionId) {
    return new ConflictResponseDto(getConflictedItemsForDelete(collectionId));
  }

  private Set<ConflictedItemDto> getConflictedItemsForDelete(String collectionId) {
    return collectionRelationService.getConflictedItemsForDelete(getCollectionDefinition(collectionId));
  }
}
