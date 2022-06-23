/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionRestDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.service.es.reader.CollectionReader;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import org.camunda.optimize.service.relations.CollectionRelationService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@AllArgsConstructor
@Component
@Slf4j
public class CollectionService {

  private final AuthorizedCollectionService authorizedCollectionService;
  private final CollectionRelationService collectionRelationService;
  private final CollectionEntityService collectionEntityService;
  private final CollectionWriter collectionWriter;
  private final CollectionReader collectionReader;

  public IdResponseDto createNewCollectionAndReturnId(final String userId,
                                                      final PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto) {
    return collectionWriter.createNewCollectionAndReturnId(userId, partialCollectionDefinitionDto);
  }

  public Optional<IdResponseDto> createNewCollectionWithPresetId(final String userId,
                                                       final PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto,
                                                       final String presetId,
                                                       final boolean automaticallyCreated) {
    try {
      return Optional.of(collectionWriter.createNewCollectionAndReturnId(userId, partialCollectionDefinitionDto,
                                                                        presetId, automaticallyCreated));
    } catch (OptimizeRuntimeException e) {
      // This can happen if the collection has been created in parallel, let's check if it already exists
      if (Optional.ofNullable(getCollectionDefinition(presetId)).isEmpty()) {
        // If it doesn't exist yet and it could not be created, then we have another problem, rethrow exception
        throw e;
      }
      else {
        // If it exists already, there's nothing we need to do, return empty to avoid the re-creation of reports
        return Optional.empty();
      }
    }
  }

  public AuthorizedCollectionDefinitionRestDto getCollectionDefinitionRestDto(final String userId,
                                                                              final String collectionId) {
    log.debug("Fetching resolved collection with id [{}]", collectionId);

    final AuthorizedCollectionDefinitionDto collectionDefinition =
      authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(
        userId,
        collectionId
      );

    return AuthorizedCollectionDefinitionRestDto.from(collectionDefinition);
  }

  public void updatePartialCollection(final String userId,
                                      final String collectionId,
                                      final PartialCollectionDefinitionRequestDto collectionUpdate) {
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

  public void deleteCollection(final String userId, final String collectionId, final boolean force) {
    final AuthorizedCollectionDefinitionDto collectionDefinition = authorizedCollectionService
      .getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);

    if (!force) {
      final Set<ConflictedItemDto> conflictedItems = getConflictedItemsForDelete(userId, collectionId);

      if (!conflictedItems.isEmpty()) {
        throw new OptimizeCollectionConflictException(conflictedItems);
      }
    }

    collectionRelationService.handleDeleted(collectionDefinition.getDefinitionDto());
    collectionWriter.deleteCollection(collectionId);
  }

  public ConflictResponseDto getDeleteConflictingItems(String userId, String collectionId) {
    return new ConflictResponseDto(getConflictedItemsForDelete(userId, collectionId));
  }

  public CollectionDefinitionDto getCollectionDefinition(final String collectionId) {
    return collectionReader.getCollection(collectionId)
      .orElseThrow(() -> new NotFoundException("Collection with ID [" + collectionId + "] does not exist."));
  }

  private AuthorizedCollectionDefinitionDto getCollectionDefinition(final String userId, final String collectionId) {
    return authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId);
  }

  public List<AuthorizedCollectionDefinitionDto> getAllCollectionDefinitions(final String userId) {
    return authorizedCollectionService.getAuthorizedCollectionDefinitions(userId);
  }

  public IdResponseDto copyCollection(String userId, String collectionId, String newCollectionName) {
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
    return new IdResponseDto(newCollection.getId());
  }

  private Set<ConflictedItemDto> getConflictedItemsForDelete(String userId, String collectionId) {
    return collectionRelationService.getConflictedItemsForDelete(
      getCollectionDefinition(userId, collectionId).getDefinitionDto()
    );
  }

  private AuthorizedCollectionDefinitionRestDto getCollectionDefinitionRestDto(
    final AuthorizedCollectionDefinitionDto collectionDefinitionDto) {
    return AuthorizedCollectionDefinitionRestDto.from(collectionDefinitionDto);
  }
}
