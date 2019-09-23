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
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.OptimizeConflictException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.relations.CollectionRelationService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.AuthorizedEntitiesService;
import org.camunda.optimize.service.security.IdentityService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
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

  private final AuthorizedCollectionService authorizedCollectionService;
  private final CollectionWriter collectionWriter;
  private final ReportReader reportReader;
  private final CollectionRelationService collectionRelationService;
  private final AuthorizedEntitiesService entitiesService;
  private final IdentityService identityService;

  public IdDto createNewCollectionAndReturnId(final String userId) {
    return collectionWriter.createNewCollectionAndReturnId(userId);
  }

  public AuthorizedSimpleCollectionDefinitionDto getSimpleCollectionDefinition(final String userId,
                                                                               final String collectionId) {
    return authorizedCollectionService.getAuthorizedSimpleCollectionDefinitionOrFail(userId, collectionId);
  }

  public List<AuthorizedSimpleCollectionDefinitionDto> getAllSimpleCollectionDefinitions(final String userId) {
    return authorizedCollectionService.getAuthorizedSimpleCollectionDefinitions(userId);
  }

  public AuthorizedResolvedCollectionDefinitionDto getResolvedCollectionDefinition(final String userId,
                                                                                   final String collectionId) {
    log.debug("Fetching resolved collection with id [{}]", collectionId);

    final AuthorizedSimpleCollectionDefinitionDto simpleCollectionDefinitionDto = authorizedCollectionService
      .getAuthorizedSimpleCollectionDefinitionOrFail(userId, collectionId);

    final List<EntityDto> collectionEntities = entitiesService.getAuthorizedCollectionEntities(userId, collectionId);

    return mapToResolvedCollection(simpleCollectionDefinitionDto, collectionEntities);
  }


  public void updatePartialCollection(final String userId,
                                      final String collectionId,
                                      final PartialCollectionUpdateDto collectionUpdate) {
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

  public CollectionScopeEntryDto addScopeEntryToCollection(String userId,
                                                           String collectionId,
                                                           CollectionScopeEntryDto entryDto) throws
                                                                                             OptimizeConflictException {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    return collectionWriter.addScopeEntryToCollection(collectionId, entryDto, userId);
  }

  public void removeScopeEntry(String userId, String collectionId, String scopeEntryId)
    throws NotFoundException, OptimizeConflictException {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);

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

  public void updateScopeEntry(String userId,
                               String collectionId,
                               CollectionScopeEntryUpdateDto entryDto,
                               String scopeEntryId) throws OptimizeConflictException {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    collectionWriter.updateScopeEntity(collectionId, entryDto, userId, scopeEntryId);
  }

  public CollectionRoleDto addRoleToCollection(final String userId,
                                               final String collectionId,
                                               final CollectionRoleDto roleDto) throws OptimizeConflictException {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
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

  private ConflictedItemDto reportToConflictedItem(CollectionEntity collectionEntity) {
    return new ConflictedItemDto(
      collectionEntity.getId(),
      ConflictedItemType.REPORT,
      collectionEntity.getName()
    );
  }

  private AuthorizedResolvedCollectionDefinitionDto mapToResolvedCollection(
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
        .map(entityDto -> {
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

  private Set<ConflictedItemDto> getConflictedItemsForDelete(String userId, String collectionId) {
    return collectionRelationService.getConflictedItemsForDelete(
      getSimpleCollectionDefinition(userId, collectionId).getDefinitionDto()
    );
  }
}
