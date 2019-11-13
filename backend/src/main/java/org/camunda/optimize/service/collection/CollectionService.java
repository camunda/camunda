/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
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
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.relations.CollectionRelationService;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.AuthorizedEntitiesService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private final CollectionRoleService collectionRoleService;
  private final AlertService alertService;
  private final ReportService reportService;
  private final DashboardService dashboardService;

  public IdDto createNewCollectionAndReturnId(final String userId,
                                              final PartialCollectionDefinitionDto partialCollectionDefinitionDto) {
    return collectionWriter.createNewCollectionAndReturnId(userId, partialCollectionDefinitionDto);
  }

  public AuthorizedResolvedCollectionDefinitionDto getResolvedCollectionDefinition(final String userId,
                                                                                   final String collectionId) {
    log.debug("Fetching resolved collection with id [{}]", collectionId);

    final AuthorizedSimpleCollectionDefinitionDto simpleCollectionDefinition =
      collectionRoleService.getSimpleCollectionDefinitionWithRoleMetadata(userId, collectionId);

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


  AuthorizedSimpleCollectionDefinitionDto getSimpleCollectionDefinition(final String userId,
                                                                                final String collectionId) {
    return authorizedCollectionService.getAuthorizedSimpleCollectionDefinitionOrFail(userId, collectionId);
  }

  public List<AuthorizedSimpleCollectionDefinitionDto> getAllSimpleCollectionDefinitions(final String userId) {
    return authorizedCollectionService.getAuthorizedSimpleCollectionDefinitions(userId);
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

  public IdDto copyCollection(String userId, String collectionId, String newCollectionName) {
    AuthorizedSimpleCollectionDefinitionDto oldSimpleCollection = authorizedCollectionService
      .getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);

    SimpleCollectionDefinitionDto newSimpleCollection = new SimpleCollectionDefinitionDto(
      oldSimpleCollection.getDefinitionDto().getData(),
      OffsetDateTime.now(),
      IdGenerator.getNextId(),
      newCollectionName != null ? newCollectionName : oldSimpleCollection.getDefinitionDto()
        .getName() + " – Copy",
      OffsetDateTime.now(),
      userId,
      userId
    );

    ResolvedCollectionDefinitionDto oldResolvedCollection =
      getResolvedCollectionDefinition(userId, oldSimpleCollection).getDefinitionDto();

    collectionWriter.createNewCollection(newSimpleCollection);

    copyCollectionEntities(userId, oldResolvedCollection, newSimpleCollection.getId());
    return new IdDto(newSimpleCollection.getId());
  }

  private AuthorizedResolvedCollectionDefinitionDto getResolvedCollectionDefinition(
    final String userId,
    final AuthorizedSimpleCollectionDefinitionDto simpleCollectionDefinitionDto
  ) {
    return mapToResolvedCollection(
      simpleCollectionDefinitionDto,
      entitiesService.getAuthorizedCollectionEntities(userId, simpleCollectionDefinitionDto.getDefinitionDto().getId())
    );
  }

  private void copyCollectionEntities(String userId, ResolvedCollectionDefinitionDto collectionDefinitionDto,
                                      String newCollectionId) {
    final Map<String, String> uniqueReportCopies = new HashMap<>();

    collectionDefinitionDto.getData().getEntities().forEach(e -> {
      final String originalEntityId = e.getId();
      switch (e.getEntityType()) {
        case REPORT:
          String entityCopyId = uniqueReportCopies.get(originalEntityId);
          if (entityCopyId == null) {
            entityCopyId = reportService.copyAndMoveReport(
              originalEntityId, userId, newCollectionId, e.getName(), uniqueReportCopies, true
            ).getId();
            uniqueReportCopies.put(originalEntityId, entityCopyId);
          }
          alertService.copyAndMoveAlerts(originalEntityId, entityCopyId);
          break;
        case DASHBOARD:
          dashboardService.copyAndMoveDashboard(
            originalEntityId, userId, newCollectionId, e.getName(), uniqueReportCopies, true
          );
          break;
        default:
          throw new OptimizeRuntimeException(
            "You can't copy a " + e.getEntityType().toString().toLowerCase() + " to a collection"
          );
      }
    });
  }
}
