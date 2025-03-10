/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.collection;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.collection.PartialCollectionDataDto;
import io.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizationType;
import io.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionRestDto;
import io.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import io.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import io.camunda.optimize.service.db.reader.CollectionReader;
import io.camunda.optimize.service.db.writer.CollectionWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.relations.CollectionRelationService;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.IdGenerator;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class CollectionService {

  private final AuthorizedCollectionService authorizedCollectionService;
  private final CollectionRelationService collectionRelationService;
  private final CollectionEntityService collectionEntityService;
  private final CollectionWriter collectionWriter;
  private final CollectionReader collectionReader;
  private final AbstractIdentityService identityService;

  public List<CollectionDefinitionDto> getAllCollections() {
    return collectionReader.getAllCollections();
  }

  public IdResponseDto createNewCollectionAndReturnId(
      final String userId,
      final PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto) {
    if (!identityService.getEnabledAuthorizations().contains(AuthorizationType.ENTITY_EDITOR)) {
      throw new ForbiddenException("User is not an authorized entity editor");
    }
    return collectionWriter.createNewCollectionAndReturnId(userId, partialCollectionDefinitionDto);
  }

  public Optional<IdResponseDto> createNewCollectionWithPresetId(
      final String userId,
      final PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto,
      final String presetId,
      final boolean automaticallyCreated) {
    try {
      return Optional.of(
          collectionWriter.createNewCollectionAndReturnId(
              userId, partialCollectionDefinitionDto, presetId, automaticallyCreated));
    } catch (final OptimizeRuntimeException e) {
      // This can happen if the collection has been created in parallel, let's check if it already
      // exists
      if (Optional.ofNullable(getCollectionDefinition(presetId)).isEmpty()) {
        // If it doesn't exist yet and it could not be created, then we have another problem, log it
        // and rethrow
        // exception
        log.error("Unexpected error when trying to create collection with ID " + presetId, e);
        throw e;
      } else {
        // If it exists already, there's nothing we need to do, return empty to avoid the
        // re-creation of reports
        return Optional.empty();
      }
    }
  }

  public AuthorizedCollectionDefinitionRestDto getCollectionDefinitionRestDto(
      final String userId, final String collectionId) {
    log.debug("Fetching resolved collection with id [{}]", collectionId);

    final AuthorizedCollectionDefinitionDto collectionDefinition =
        authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId);

    return AuthorizedCollectionDefinitionRestDto.from(collectionDefinition);
  }

  public void updatePartialCollection(
      final String userId,
      final String collectionId,
      final PartialCollectionDefinitionRequestDto collectionUpdate) {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(
        userId, collectionId);

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

  public void deleteCollection(
      final String userId, final String collectionId, final boolean force) {
    final AuthorizedCollectionDefinitionDto collectionDefinition =
        authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(
            userId, collectionId);

    if (!force) {
      final Set<ConflictedItemDto> conflictedItems =
          getConflictedItemsForDelete(userId, collectionId);

      if (!conflictedItems.isEmpty()) {
        throw new OptimizeCollectionConflictException(conflictedItems);
      }
    }

    collectionRelationService.handleDeleted(collectionDefinition.getDefinitionDto());
    collectionWriter.deleteCollection(collectionId);
  }

  public ConflictResponseDto getDeleteConflictingItems(
      final String userId, final String collectionId) {
    return new ConflictResponseDto(getConflictedItemsForDelete(userId, collectionId));
  }

  public CollectionDefinitionDto getCollectionDefinition(final String collectionId) {
    return collectionReader
        .getCollection(collectionId)
        .orElseThrow(
            () ->
                new NotFoundException("Collection with ID [" + collectionId + "] does not exist."));
  }

  private AuthorizedCollectionDefinitionDto getCollectionDefinition(
      final String userId, final String collectionId) {
    return authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(
        userId, collectionId);
  }

  public List<AuthorizedCollectionDefinitionDto> getAllCollectionDefinitions(final String userId) {
    return authorizedCollectionService.getAuthorizedCollectionDefinitions(userId);
  }

  public IdResponseDto copyCollection(
      final String userId, final String collectionId, final String newCollectionName) {
    final AuthorizedCollectionDefinitionDto oldCollection =
        authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(
            userId, collectionId);

    final CollectionDefinitionDto newCollection =
        new CollectionDefinitionDto(
            oldCollection.getDefinitionDto().getData(),
            OffsetDateTime.now(),
            IdGenerator.getNextId(),
            newCollectionName != null
                ? newCollectionName
                : oldCollection.getDefinitionDto().getName() + " – Copy",
            OffsetDateTime.now(),
            userId,
            userId);

    final CollectionDefinitionRestDto oldResolvedCollection =
        getCollectionDefinitionRestDto(oldCollection).getDefinitionDto();

    collectionWriter.createNewCollection(newCollection);

    collectionEntityService.copyCollectionEntities(
        userId, oldResolvedCollection, newCollection.getId());
    return new IdResponseDto(newCollection.getId());
  }

  private Set<ConflictedItemDto> getConflictedItemsForDelete(
      final String userId, final String collectionId) {
    return collectionRelationService.getConflictedItemsForDelete(
        getCollectionDefinition(userId, collectionId).getDefinitionDto());
  }

  private AuthorizedCollectionDefinitionRestDto getCollectionDefinitionRestDto(
      final AuthorizedCollectionDefinitionDto collectionDefinitionDto) {
    return AuthorizedCollectionDefinitionRestDto.from(collectionDefinitionDto);
  }
}
