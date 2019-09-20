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
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedSimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.service.es.reader.CollectionReader;
import org.camunda.optimize.service.es.reader.EntitiesReader;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.OptimizeConflictException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.relations.CollectionRelationService;
import org.camunda.optimize.service.security.CollectionAuthorizationService;
import org.camunda.optimize.service.security.IdentityService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
@Slf4j
public class CollectionService {

  private static final String VIEW_NOT_AUTHORIZED_MESSAGE = "User [%s] is not authorized to access collection [%s].";
  private static final String EDIT_NOT_AUTHORIZED_MESSAGE =
    "User [%s] is not authorized to edit/delete collection [%s].";
  private static final String RESOURCE_EDIT_NOT_AUTHORIZED_MESSAGE =
    "User %s does not have the role to add/edit collection [%s] resources.";

  private final CollectionWriter collectionWriter;
  private final CollectionReader collectionReader;
  private final ReportReader reportReader;
  private final EntitiesReader entitiesReader;
  private final CollectionRelationService collectionRelationService;
  private final CollectionAuthorizationService collectionAuthorizationService;
  private final IdentityService identityService;

  public IdDto createNewCollectionAndReturnId(final String userId) {
    return collectionWriter.createNewCollectionAndReturnId(userId);
  }

  public Optional<RoleType> getUsersCollectionResourceRole(final String collectionId, final String userId)
    throws NotFoundException, ForbiddenException {
    return getAuthorizedSimpleCollectionDefinitionDto(collectionId, userId)
      .map(this::getCollectionResourceRoleForCollection);
  }

  public void verifyUserAuthorizedToEditCollectionResources(final String collectionId, final String userId)
    throws NotFoundException, ForbiddenException {
    if (collectionId != null) {
      final AuthorizedSimpleCollectionDefinitionDto authorizedCollection =
        getAuthorizedSimpleCollectionDefinitionDtoOrFail(collectionId, userId);
      if (authorizedCollection.getCurrentUserRole().ordinal() < RoleType.EDITOR.ordinal()) {
        throw new ForbiddenException(String.format(RESOURCE_EDIT_NOT_AUTHORIZED_MESSAGE, userId, collectionId));
      }
    }
  }

  public AuthorizedSimpleCollectionDefinitionDto getSimpleCollectionDefinition(final String collectionId,
                                                                               final String userId) {
    return getAuthorizedSimpleCollectionDefinitionDtoOrFail(collectionId, userId);
  }

  public List<AuthorizedSimpleCollectionDefinitionDto> getAllSimpleCollectionDefinitions(final String userId) {
    return collectionReader.getAllCollections().stream()
      .map(definitionDto -> collectionAuthorizationService.resolveToAuthorizedSimpleCollection(definitionDto, userId))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  public AuthorizedResolvedCollectionDefinitionDto getResolvedCollectionDefinition(final String collectionId,
                                                                                   final String userId) {
    log.debug("Fetching resolved collection with id [{}]", collectionId);

    final AuthorizedSimpleCollectionDefinitionDto simpleCollectionDefinitionDto =
      getAuthorizedSimpleCollectionDefinitionDtoOrFail(collectionId, userId);

    final List<CollectionEntity> collectionEntities =
      entitiesReader.getAllEntitiesForCollection(simpleCollectionDefinitionDto.getDefinitionDto());

    return mapToResolvedCollection(simpleCollectionDefinitionDto, collectionEntities);
  }

  public void updatePartialCollection(final String collectionId,
                                      final String userId,
                                      final PartialCollectionUpdateDto collectionUpdate) {
    getCollectionAndVerifyUserAuthorizedToManageOrFail(collectionId, userId);

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

  public void deleteCollection(final String collectionId, final boolean force, final String userId)
    throws OptimizeConflictException {
    final AuthorizedSimpleCollectionDefinitionDto collectionDefinition =
      getCollectionAndVerifyUserAuthorizedToManageOrFail(collectionId, userId);

    if (!force) {
      final Set<ConflictedItemDto> conflictedItems = getConflictedItemsForDelete(collectionId, userId);

      if (!conflictedItems.isEmpty()) {
        throw new OptimizeConflictException(conflictedItems);
      }
    }

    collectionRelationService.handleDeleted(collectionDefinition.getDefinitionDto());
    collectionWriter.deleteCollection(collectionId);
  }

  public ConflictResponseDto getDeleteConflictingItems(String collectionId, String userId) {
    return new ConflictResponseDto(getConflictedItemsForDelete(collectionId, userId));
  }

  public CollectionScopeEntryDto addScopeEntryToCollection(String collectionId,
                                                           CollectionScopeEntryDto entryDto,
                                                           String userId) throws OptimizeConflictException {
    getCollectionAndVerifyUserAuthorizedToManageOrFail(collectionId, userId);
    return collectionWriter.addScopeEntryToCollection(collectionId, entryDto, userId);
  }

  public void removeScopeEntry(String collectionId, String scopeEntryId, String userId)
    throws NotFoundException, OptimizeConflictException {
    getCollectionAndVerifyUserAuthorizedToManageOrFail(collectionId, userId);

    List<ReportDefinitionDto> entities = reportReader.findReportsForCollection(collectionId);
    CollectionScopeEntryDto scopeEntry = new CollectionScopeEntryDto(scopeEntryId);

    List<ReportDefinitionDto> conflictedItems = entities.stream()
      .filter(report -> report.getData() instanceof SingleReportDataDto)
      .filter(report ->
                ((SingleReportDataDto) report.getData()).getDefinitionKey().equals(scopeEntry.getDefinitionKey())
                  && report.getReportType().toString().equalsIgnoreCase(scopeEntry.getDefinitionType().getId()))
      .collect(Collectors.toList());

    if (conflictedItems.size() == 0) {
      collectionWriter.removeScopeEntry(collectionId, scopeEntryId, userId);
    } else {
      throw new OptimizeConflictException(
        conflictedItems.stream().map(this::reportToConflictedItem).collect(Collectors.toSet())
      );
    }
  }

  public void updateScopeEntry(String collectionId,
                               CollectionScopeEntryUpdateDto entryDto,
                               String userId,
                               String scopeEntryId) throws OptimizeConflictException {
    getCollectionAndVerifyUserAuthorizedToManageOrFail(collectionId, userId);
    collectionWriter.updateScopeEntity(collectionId, entryDto, userId, scopeEntryId);
  }

  public CollectionRoleDto addRoleToCollection(final String collectionId,
                                               final CollectionRoleDto roleDto,
                                               final String userId) throws OptimizeConflictException {
    getCollectionAndVerifyUserAuthorizedToManageOrFail(collectionId, userId);
    verifyIdentityExists(roleDto.getIdentity());
    return collectionWriter.addRoleToCollection(collectionId, roleDto, userId);
  }

  private void verifyIdentityExists(final IdentityDto identity) {
    final Optional<IdentityDto> foundIdentity;
    switch (identity.getType()) {
      case USER:
        foundIdentity = identityService.getOptimizeUserById(identity.getId());
        break;
      case GROUP:
        foundIdentity = identityService.getGroupById(identity.getId());
        break;
      default:
        throw new OptimizeRuntimeException("Unsupported identity type: " + identity.getType());
    }
    if (!foundIdentity.isPresent()) {
      throw new BadRequestException(
        String.format("%s with id %s does not exist in Optimize", identity.getType(), identity.getId())
      );
    }
  }

  public void updateRoleOfCollection(final String collectionId,
                                     final String roleEntryId,
                                     final CollectionRoleUpdateDto roleUpdateDto,
                                     final String userId) throws OptimizeConflictException {
    getCollectionAndVerifyUserAuthorizedToManageOrFail(collectionId, userId);
    collectionWriter.updateRoleInCollection(collectionId, roleEntryId, roleUpdateDto, userId);
  }

  public void removeRoleFromCollection(String collectionId, String roleEntryId, String userId)
    throws OptimizeConflictException {
    getCollectionAndVerifyUserAuthorizedToManageOrFail(collectionId, userId);
    collectionWriter.removeRoleFromCollection(collectionId, roleEntryId, userId);
  }

  private AuthorizedSimpleCollectionDefinitionDto getCollectionAndVerifyUserAuthorizedToManageOrFail(
    final String collectionId,
    final String userId) {

    final AuthorizedSimpleCollectionDefinitionDto collectionDefinition = getSimpleCollectionDefinition(
      collectionId, userId
    );
    if (collectionDefinition.getCurrentUserRole().ordinal() < RoleType.MANAGER.ordinal()) {
      throw new ForbiddenException(String.format(EDIT_NOT_AUTHORIZED_MESSAGE, collectionId, userId));
    }
    return collectionDefinition;
  }

  private AuthorizedSimpleCollectionDefinitionDto getAuthorizedSimpleCollectionDefinitionDtoOrFail(
    final String collectionId, final String userId) {
    return getAuthorizedSimpleCollectionDefinitionDto(collectionId, userId)
      .orElseThrow(() -> new ForbiddenException(String.format(VIEW_NOT_AUTHORIZED_MESSAGE, userId, collectionId)));
  }

  private Optional<AuthorizedSimpleCollectionDefinitionDto> getAuthorizedSimpleCollectionDefinitionDto(
    final String collectionId, final String userId) {
    final SimpleCollectionDefinitionDto collection = collectionReader.getCollection(collectionId);
    return collectionAuthorizationService.resolveToAuthorizedSimpleCollection(collection, userId);
  }

  private ConflictedItemDto reportToConflictedItem(CollectionEntity collectionEntity) {
    return new ConflictedItemDto(
      collectionEntity.getId(),
      ConflictedItemType.REPORT,
      collectionEntity.getName()
    );
  }

  private AuthorizedResolvedCollectionDefinitionDto mapToResolvedCollection(
    final AuthorizedSimpleCollectionDefinitionDto authorizedCollectionDto,
    final Collection<CollectionEntity> collectionEntities) {

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

    final RoleType currentUserResourceRole = getCollectionResourceRoleForCollection(authorizedCollectionDto);
    resolvedCollectionData.setEntities(
      collectionEntities
        .stream()
        .filter(Objects::nonNull)
        .map(collectionEntity -> {
          final EntityDto entityDto = collectionEntity.toEntityDto();
          entityDto.setCurrentUserRole(currentUserResourceRole);
          return entityDto;
        })
        .sorted(Comparator.comparing(EntityDto::getLastModified).reversed())
        .collect(Collectors.toList())
    );

    resolvedCollection.setData(resolvedCollectionData);
    return new AuthorizedResolvedCollectionDefinitionDto(
      authorizedCollectionDto.getCurrentUserRole(), resolvedCollection
    );
  }

  private RoleType getCollectionResourceRoleForCollection(final AuthorizedSimpleCollectionDefinitionDto authorizedDto) {
    switch (authorizedDto.getCurrentUserRole()) {
      case EDITOR:
      case MANAGER:
        return RoleType.EDITOR;
      case VIEWER:
      default:
        return RoleType.VIEWER;
    }
  }

  private Set<ConflictedItemDto> getConflictedItemsForDelete(String collectionId, String userId) {
    return collectionRelationService.getConflictedItemsForDelete(
      getSimpleCollectionDefinition(collectionId, userId).getDefinitionDto()
    );
  }
}
