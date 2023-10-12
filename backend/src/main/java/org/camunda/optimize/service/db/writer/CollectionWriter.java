/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import jakarta.ws.rs.NotFoundException;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.*;

import java.util.List;

public interface CollectionWriter {

  String DEFAULT_COLLECTION_NAME = "New Collection";

  IdResponseDto createNewCollectionAndReturnId(@NonNull String userId,
                                               @NonNull PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto);

  IdResponseDto createNewCollectionAndReturnId(@NonNull String userId,
                                               @NonNull PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto,
                                               @NonNull String id,
                                               boolean automaticallyCreated);

  void createNewCollection(@NonNull CollectionDefinitionDto collectionDefinitionDto);

  void updateCollection(CollectionDefinitionUpdateDto collection, String id);

  void deleteCollection(String collectionId);

  void addScopeEntriesToCollection(final String userId,
                                   final String collectionId,
                                   final List<CollectionScopeEntryDto> scopeUpdates);

  void deleteScopeEntryFromAllCollections(final String scopeEntryId);

  void updateScopeEntity(String collectionId,
                         CollectionScopeEntryUpdateDto scopeEntry,
                         String userId,
                         String scopeEntryId);

  void removeScopeEntries(String collectionId, List<String> scopeEntryIds, String userId) throws NotFoundException;

  void removeScopeEntry(String collectionId, String scopeEntryId, String userId) throws NotFoundException;


  void addRoleToCollection(String collectionId, List<CollectionRoleRequestDto> rolesToAdd, String userId);

  void updateRoleInCollection(final String collectionId,
                              final String roleEntryId,
                              final CollectionRoleUpdateRequestDto roleUpdateDto,
                              final String userId);

  void removeRoleFromCollection(final String collectionId, final String roleEntryId);


  void removeRoleFromCollectionUnlessIsLastManager(final String collectionId, final String roleEntryId,
                                                   final String userId);

}
