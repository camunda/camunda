/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedSimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.service.IdentityService;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.relations.CollectionRelationService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.AuthorizedEntitiesService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
@Slf4j
public class CollectionService {

  private final AuthorizedCollectionService authorizedCollectionService;
  private final CollectionWriter collectionWriter;
  private final CollectionRelationService collectionRelationService;
  private final AuthorizedEntitiesService entitiesService;
  private final IdentityService identityService;

  public IdDto createNewCollectionAndReturnId(final String userId,
                                              final PartialCollectionDefinitionDto partialCollectionDefinitionDto) {
    return collectionWriter.createNewCollectionAndReturnId(userId, partialCollectionDefinitionDto);
  }

  public AuthorizedSimpleCollectionDefinitionDto getSimpleCollectionDefinitionWithRoleMetadata(final String userId,
                                                                                               final String collectionId) {
    final AuthorizedSimpleCollectionDefinitionDto simpleCollectionDefinition = getSimpleCollectionDefinition(
      userId, collectionId
    );
    enrichRoleIdentityMetaData(simpleCollectionDefinition.getDefinitionDto());
    return simpleCollectionDefinition;
  }

  public AuthorizedResolvedCollectionDefinitionDto getResolvedCollectionDefinition(final String userId,
                                                                                   final String collectionId) {
    log.debug("Fetching resolved collection with id [{}]", collectionId);

    final AuthorizedSimpleCollectionDefinitionDto simpleCollectionDefinition =
      getSimpleCollectionDefinitionWithRoleMetadata(userId, collectionId);

    final List<EntityDto> collectionEntities = entitiesService.getAuthorizedCollectionEntities(userId, collectionId);

    return mapToResolvedCollection(simpleCollectionDefinition, collectionEntities);
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
    final AuthorizedSimpleCollectionDefinitionDto collectionDefinition = authorizedCollectionService
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

  public List<CollectionRoleDto> getAllRolesOfCollectionSorted(String userId, String collectionId) {
    List<CollectionRoleDto> roles = getSimpleCollectionDefinitionWithRoleMetadata(
      userId,
      collectionId
    )
      .getDefinitionDto()
      .getData()
      .getRoles();

    Collections.sort(roles);
    return roles;
  }

  public CollectionRoleDto addRoleToCollection(final String userId,
                                               final String collectionId,
                                               final CollectionRoleDto roleDto) throws
                                                                                OptimizeCollectionConflictException {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    verifyIdentityExists(roleDto.getIdentity());
    return collectionWriter.addRoleToCollection(collectionId, roleDto, userId);
  }

  public void updateRoleOfCollection(final String userId,
                                     final String collectionId,
                                     final String roleEntryId,
                                     final CollectionRoleUpdateDto roleUpdateDto) throws OptimizeConflictException {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    collectionWriter.updateRoleInCollection(collectionId, roleEntryId, roleUpdateDto, userId);
  }

  public void removeRoleFromCollection(String userId, String collectionId, String roleEntryId)
    throws OptimizeConflictException {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    collectionWriter.removeRoleFromCollection(collectionId, roleEntryId, userId);
  }


  private AuthorizedSimpleCollectionDefinitionDto getSimpleCollectionDefinition(final String userId,
                                                                                final String collectionId) {
    return authorizedCollectionService.getAuthorizedSimpleCollectionDefinitionOrFail(userId, collectionId);
  }

  public List<AuthorizedSimpleCollectionDefinitionDto> getAllSimpleCollectionDefinitions(final String userId) {
    return authorizedCollectionService.getAuthorizedSimpleCollectionDefinitions(userId);
  }

  private void verifyIdentityExists(final IdentityDto identity) {
    final boolean identityFound;
    switch (identity.getType()) {
      case USER:
        identityFound = identityService.getUserById(identity.getId()).isPresent();
        break;
      case GROUP:
        identityFound = identityService.getGroupById(identity.getId()).isPresent();
        break;
      default:
        throw new OptimizeRuntimeException("Unsupported identity type: " + identity.getType());
    }
    if (!identityFound) {
      throw new BadRequestException(
        String.format("%s with id %s does not exist in Optimize", identity.getType(), identity.getId())
      );
    }
  }

  @SuppressWarnings("Convert2MethodRef")
  private void enrichRoleIdentityMetaData(final BaseCollectionDefinitionDto<?> collectionDefinitionDto) {
    final CollectionDataDto collectionData = collectionDefinitionDto.getData();
    collectionData.setRoles(
      collectionData.getRoles().stream()
        .peek(roleDto -> {
          final IdentityDto roleIdentity = roleDto.getIdentity();
          switch (roleIdentity.getType()) {
            case GROUP:
              // Note: Method reference cannot be used here as it might trigger
              // a compilation AssertionError on newer JDK's.
              // See https://bugs.openjdk.java.net/browse/JDK-8210734
              identityService.getGroupById(roleIdentity.getId()).ifPresent(groupDto -> roleDto.setIdentity(groupDto));
              break;
            case USER:
              // Note: Method reference cannot be used here as it might trigger
              // a compilation AssertionError on newer JDK's.
              // See https://bugs.openjdk.java.net/browse/JDK-8210734
              identityService.getUserById(roleIdentity.getId()).ifPresent(userDto -> roleDto.setIdentity(userDto));
              break;
            default:
              throw new OptimizeRuntimeException("Unsupported identity type " + roleIdentity.getType());
          }
        })
        .collect(Collectors.toList())
    );
  }

  public AuthorizedResolvedCollectionDefinitionDto mapToResolvedCollection(
    final AuthorizedSimpleCollectionDefinitionDto authorizedCollectionDto,
    final Collection<EntityDto> collectionEntities) {

    final SimpleCollectionDefinitionDto collectionDefinitionDto = authorizedCollectionDto.getDefinitionDto();
    final ResolvedCollectionDefinitionDto resolvedCollection = new ResolvedCollectionDefinitionDto();
    resolvedCollection.setId(collectionDefinitionDto.getId());
    resolvedCollection.setName(collectionDefinitionDto.getName());
    resolvedCollection.setLastModifier(collectionDefinitionDto.getLastModifier());
    resolvedCollection.setOwner(collectionDefinitionDto.getOwner());
    resolvedCollection.setCreated(collectionDefinitionDto.getCreated());
    resolvedCollection.setLastModified(collectionDefinitionDto.getLastModified());

    final ResolvedCollectionDataDto resolvedCollectionData = new ResolvedCollectionDataDto();

    final CollectionDataDto collectionData = collectionDefinitionDto.getData();
    if (collectionData != null) {
      resolvedCollectionData.setConfiguration(collectionData.getConfiguration());
      resolvedCollectionData.setRoles(collectionData.getRoles());
      resolvedCollectionData.setScope(collectionData.getScope());
    }

    final RoleType currentUserResourceRole = authorizedCollectionDto.getCollectionResourceRole();
    resolvedCollectionData.setEntities(
      collectionEntities
        .stream()
        .filter(Objects::nonNull)
        .peek(entityDto -> entityDto.setCurrentUserRole(currentUserResourceRole))
        .sorted(
          Comparator.comparing(EntityDto::getEntityType)
            .thenComparing(EntityDto::getLastModified, Comparator.reverseOrder())
        )
        .collect(Collectors.toList())
    );

    resolvedCollection.setData(resolvedCollectionData);
    return new AuthorizedResolvedCollectionDefinitionDto(
      authorizedCollectionDto.getCurrentUserRole(), resolvedCollection
    );
  }

  private Set<ConflictedItemDto> getConflictedItemsForDelete(String userId, String collectionId) {
    return collectionRelationService.getConflictedItemsForDelete(
      getSimpleCollectionDefinition(userId, collectionId).getDefinitionDto()
    );
  }
}
