/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateRequestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import io.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.IdGenerator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface CollectionWriter {

  String DEFAULT_COLLECTION_NAME = "New Collection";

  String UPDATE_ENTITY_SCRIPT_CODE =
      """
          Map newScopes = ctx._source.data.scope.stream()
            .collect(Collectors.toMap(s -> s.id, Function.identity()));
          params.scopeEntriesToUpdate
            .forEach(newScope -> {
               newScopes.computeIfPresent(newScope.id, (key, oldScope) -> {
                 newScope.tenants = Stream.concat(oldScope.tenants.stream(), newScope.tenants.stream())
                  .distinct()
                  .collect(Collectors.toList());
                 return newScope;
               });
               newScopes.putIfAbsent(newScope.id, newScope);
            });
          ctx._source.data.scope = newScopes.values();
          ctx._source.lastModifier = params.lastModifier;
          ctx._source.lastModified = params.lastModified;
          """;

  String REMOVE_SCOPE_ENTRY_FROM_COLLECTION_SCRIPT_CODE =
      """
          def scopes = ctx._source.data.scope;
          if(scopes != null) {
             scopes.removeIf(scope -> scope.id.equals(params.scopeEntryIdToRemove));
          }
          """;

  String REMOVE_SCOPE_ENTRY_SCRIPT_CODE =
      """
          boolean removed = ctx._source.data.scope.removeIf(scope -> scope.id.equals(params.id));
          if (removed) {
            ctx._source.lastModifier = params.lastModifier;
            ctx._source.lastModified = params.lastModified;
          } else {
            ctx.op = "none";
          }
          """;

  String REMOVE_SCOPE_ENTRIES_SCRIPT_CODE =
      """
          for (id in params.ids) {
            ctx._source.data.scope.removeIf(scope -> scope.id.equals(id));
          }
          ctx._source.lastModifier = params.lastModifier;
          ctx._source.lastModified = params.lastModified;
          """;

  String UPDATE_SCOPE_ENTITY_SCRIPT_CODE =
      """
          def optionalEntry = ctx._source.data.scope.stream()
            .filter(s -> s.id.equals(params.entryId))
            .findFirst();
          if (optionalEntry.isPresent()) {
            def entry = optionalEntry.get();
            entry.tenants = params.entryDto.tenants;
            ctx._source.lastModifier = params.lastModifier;
            ctx._source.lastModified = params.lastModified;
          } else {
            throw new Exception('Cannot find scope entry.');
          }
          """;

  String ADD_ROLE_TO_COLLECTION_SCRIPT_CODE =
      """
          def newRoles = new ArrayList();
          for (roleToAdd in params.rolesToAdd) {
              boolean exists = ctx._source.data.roles.stream()
                 .anyMatch(existingRole -> existingRole.id.equals(roleToAdd.id));
              if (!exists){
                newRoles.add(roleToAdd);
              }
          }
          if (newRoles.size() == params.rolesToAdd.size()) {
              ctx._source.data.roles.addAll(newRoles);
              ctx._source.lastModifier = params.lastModifier;
              ctx._source.lastModified = params.lastModified;
          } else {
              ctx.op = "none";
          }
          """;

  String UPDATE_ROLE_IN_COLLECTION_SCRIPT_CODE =
      """
          def optionalExistingEntry = ctx._source.data.roles.stream()
          .filter(dto -> dto.id.equals(params.roleEntryId))
          .findFirst();
          if(optionalExistingEntry.isPresent()){
             def existingEntry = optionalExistingEntry.get();
             def moreThanOneManagerPresent = ctx._source.data.roles.stream()
             .filter(dto -> params.managerRole.equals(dto.role))
             .limit(2)
             .count()
              == 2;
          if (!moreThanOneManagerPresent && params.managerRole.equals(existingEntry.role)) {
          // updating of last manager is not allowed
             ctx.op = "none";
          } else {
             existingEntry.role = params.role;
             ctx._source.lastModifier = params.lastModifier;
             ctx._source.lastModified = params.lastModified;
          }
          } else {
          throw new Exception('Cannot find role.');
          }
          """;

  String REMOVE_ROLE_FROM_COLLECTION_SCRIPT_CODE =
      """
          def optionalExistingEntry = ctx._source.data.roles.stream()
          .filter(dto -> dto.id.equals(params.roleEntryId))
          .findFirst();
          if(optionalExistingEntry.isPresent()){
             def existingEntry = optionalExistingEntry.get();
             ctx._source.data.roles.removeIf(entry -> entry.id.equals(params.roleEntryId));
             if (params.containsKey("lastModifier")) {
                ctx._source.lastModifier = params.lastModifier;
             }
             if (params.containsKey("lastModified")) {
                ctx._source.lastModified = params.lastModified;
             }
          } else {
            throw new Exception('Cannot find role.');
          }
          """;

  String REMOVE_ROLE_FROM_COLLECTION_UNLESS_IS_LAST_MANAGER =
      """
          def optionalExistingEntry = ctx._source.data.roles.stream()
          .filter(dto -> dto.id.equals(params.roleEntryId))
          .findFirst();
          if(optionalExistingEntry.isPresent()){
              def existingEntry = optionalExistingEntry.get();
              def moreThanOneManagerPresent = ctx._source.data.roles.stream()
              .filter(dto -> params.managerRole.equals(dto.role))
              .limit(2)
              .count()
               == 2;
              if (!moreThanOneManagerPresent && params.managerRole.equals(existingEntry.role)) {
                  // deletion of last manager is not allowed
                  ctx.op = "none";
              } else {
                  ctx._source.data.roles.removeIf(entry -> entry.id.equals(params.roleEntryId));
              if (params.containsKey("lastModifier")) {
                  ctx._source.lastModifier = params.lastModifier;
              }
              if (params.containsKey("lastModified")) {
                  ctx._source.lastModified = params.lastModified;
              }
          }
          } else {
             throw new Exception('Cannot find role.');
          }
          """;

  Logger LOG = LoggerFactory.getLogger(CollectionWriter.class);

  default IdResponseDto createNewCollectionAndReturnId(
      final String userId,
      final PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto) {
    if (userId == null) {
      throw new OptimizeRuntimeException("userId cannot be null");
    }
    if (partialCollectionDefinitionDto == null) {
      throw new OptimizeRuntimeException("partialCollectionDefinitionDto cannot be null");
    }
    return createNewCollectionAndReturnId(
        userId, partialCollectionDefinitionDto, IdGenerator.getNextId(), false);
  }

  default IdResponseDto createNewCollectionAndReturnId(
      final String userId,
      final PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto,
      final String id,
      final boolean automaticallyCreated) {
    if (userId == null) {
      throw new OptimizeRuntimeException("userId cannot be null");
    }
    if (partialCollectionDefinitionDto == null) {
      throw new OptimizeRuntimeException("partialCollectionDefinitionDto cannot be null");
    }
    if (id == null) {
      throw new OptimizeRuntimeException("id cannot be null");
    }
    LOG.debug("Writing new collection to Database");
    final CollectionDefinitionDto collectionDefinitionDto = new CollectionDefinitionDto();
    collectionDefinitionDto.setId(id);
    collectionDefinitionDto.setCreated(LocalDateUtil.getCurrentDateTime());
    collectionDefinitionDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    collectionDefinitionDto.setOwner(userId);
    collectionDefinitionDto.setLastModifier(userId);
    collectionDefinitionDto.setAutomaticallyCreated(automaticallyCreated);
    collectionDefinitionDto.setName(
        Optional.ofNullable(partialCollectionDefinitionDto.getName())
            .orElse(DEFAULT_COLLECTION_NAME));

    final CollectionDataDto newCollectionDataDto = new CollectionDataDto();
    newCollectionDataDto
        .getRoles()
        .add(
            new CollectionRoleRequestDto(
                new IdentityDto(userId, IdentityType.USER), RoleType.MANAGER));
    if (partialCollectionDefinitionDto.getData() != null) {
      newCollectionDataDto.setConfiguration(
          partialCollectionDefinitionDto.getData().getConfiguration());
    }
    collectionDefinitionDto.setData(newCollectionDataDto);
    persistCollection(id, collectionDefinitionDto);
    return new IdResponseDto(id);
  }

  default void createNewCollection(final CollectionDefinitionDto collectionDefinitionDto) {
    if (collectionDefinitionDto == null) {
      throw new OptimizeRuntimeException("collectionDefinitionDto cannot be null");
    }

    persistCollection(collectionDefinitionDto.getId(), collectionDefinitionDto);
  }

  void updateCollection(CollectionDefinitionUpdateDto collection, String id);

  void deleteCollection(String collectionId);

  void addScopeEntriesToCollection(
      final String userId,
      final String collectionId,
      final List<CollectionScopeEntryDto> scopeUpdates);

  void deleteScopeEntryFromAllCollections(final String scopeEntryId);

  void updateScopeEntity(
      String collectionId,
      CollectionScopeEntryUpdateDto scopeEntry,
      String userId,
      String scopeEntryId);

  void removeScopeEntries(String collectionId, List<String> scopeEntryIds, String userId)
      throws NotFoundException;

  void removeScopeEntry(String collectionId, String scopeEntryId, String userId)
      throws NotFoundException;

  void addRoleToCollection(
      String collectionId, List<CollectionRoleRequestDto> rolesToAdd, String userId);

  void updateRoleInCollection(
      final String collectionId,
      final String roleEntryId,
      final CollectionRoleUpdateRequestDto roleUpdateDto,
      final String userId);

  void removeRoleFromCollectionUnlessIsLastManager(
      final String collectionId, final String roleEntryId, final String userId);

  void persistCollection(String id, CollectionDefinitionDto collectionDefinitionDto);
}
