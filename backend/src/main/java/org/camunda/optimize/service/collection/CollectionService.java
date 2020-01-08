/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionRestDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.relations.CollectionRelationService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@AllArgsConstructor
@Component
@Slf4j
public class CollectionService {

  private final AuthorizedCollectionService authorizedCollectionService;
  private final CollectionWriter collectionWriter;
  private final CollectionRelationService collectionRelationService;
  private final CollectionEntityService collectionEntityService;

  public IdDto createNewCollectionAndReturnId(final String userId,
                                              final PartialCollectionDefinitionDto partialCollectionDefinitionDto) {
    return collectionWriter.createNewCollectionAndReturnId(userId, partialCollectionDefinitionDto);
  }

  public AuthorizedCollectionDefinitionRestDto getCollectionDefinitionRestDto(final String userId,
                                                                              final String collectionId) {
    log.debug("Fetching resolved collection with id [{}]", collectionId);

    final AuthorizedCollectionDefinitionDto collectionDefinition =
      authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(
        userId,
        collectionId
      );

    return mapToCollectionRestDto(collectionDefinition);
  }

  public void updatePartialCollection(final String userId,
                                      final String collectionId,
                                      final PartialCollectionDefinitionDto collectionUpdate) {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);

    final CollectionDefinitionUpdateDto updateDto = new CollectionDefinitionUpdateDto();
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

  public void deleteCollection(final String userId, final String collectionId, final boolean force)
    throws OptimizeConflictException {
    final AuthorizedCollectionDefinitionDto collectionDefinition = authorizedCollectionService
      .getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);

    if (!force) {
      final Set<ConflictedItemDto> conflictedItems = getConflictedItemsForDelete(userId, collectionId);

      if (!conflictedItems.isEmpty()) {
        throw new OptimizeConflictException(conflictedItems);
      }
    }

    collectionRelationService.handleDeleted(collectionDefinition.getDefinitionDto());
    collectionWriter.deleteCollection(collectionId);
  }

  public ConflictResponseDto getDeleteConflictingItems(String userId, String collectionId) {
    return new ConflictResponseDto(getConflictedItemsForDelete(userId, collectionId));
  }


  private AuthorizedCollectionDefinitionDto getCollectionDefinition(final String userId,
                                                                    final String collectionId) {
    return authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId);
  }

  public List<AuthorizedCollectionDefinitionDto> getAllCollectionDefinitions(final String userId) {
    return authorizedCollectionService.getAuthorizedCollectionDefinitions(userId);
  }

  public AuthorizedCollectionDefinitionRestDto mapToCollectionRestDto(
    final AuthorizedCollectionDefinitionDto authorizedCollectionDto) {

    final CollectionDefinitionDto collectionDefinitionDto = authorizedCollectionDto.getDefinitionDto();
    final CollectionDefinitionRestDto resolvedCollection = new CollectionDefinitionRestDto();
    resolvedCollection.setId(collectionDefinitionDto.getId());
    resolvedCollection.setName(collectionDefinitionDto.getName());
    resolvedCollection.setLastModifier(collectionDefinitionDto.getLastModifier());
    resolvedCollection.setOwner(collectionDefinitionDto.getOwner());
    resolvedCollection.setCreated(collectionDefinitionDto.getCreated());
    resolvedCollection.setLastModified(collectionDefinitionDto.getLastModified());

    resolvedCollection.setData(collectionDefinitionDto.getData());
    return new AuthorizedCollectionDefinitionRestDto(
      authorizedCollectionDto.getCurrentUserRole(), resolvedCollection
    );
  }

  private Set<ConflictedItemDto> getConflictedItemsForDelete(String userId, String collectionId) {
    return collectionRelationService.getConflictedItemsForDelete(
      getCollectionDefinition(userId, collectionId).getDefinitionDto()
    );
  }

  public IdDto copyCollection(String userId, String collectionId, String newCollectionName) {
    AuthorizedCollectionDefinitionDto oldCollection = authorizedCollectionService
      .getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);

    CollectionDefinitionDto newCollection = new CollectionDefinitionDto(
      oldCollection.getDefinitionDto().getData(),
      OffsetDateTime.now(),
      IdGenerator.getNextId(),
      newCollectionName != null ? newCollectionName : oldCollection.getDefinitionDto()
        .getName() + " – Copy",
      OffsetDateTime.now(),
      userId,
      userId
    );

    CollectionDefinitionRestDto oldResolvedCollection =
      getCollectionDefinitionRestDto(oldCollection).getDefinitionDto();

    collectionWriter.createNewCollection(newCollection);

    collectionEntityService.copyCollectionEntities(userId, oldResolvedCollection, newCollection.getId());
    return new IdDto(newCollection.getId());
  }

  private AuthorizedCollectionDefinitionRestDto getCollectionDefinitionRestDto(
    final AuthorizedCollectionDefinitionDto collectionDefinitionDto) {
    return mapToCollectionRestDto(collectionDefinitionDto);
  }
}
