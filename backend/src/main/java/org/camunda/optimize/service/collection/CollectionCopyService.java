/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedSimpleCollectionDefinitionDto;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.AuthorizedEntitiesService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@Component
@Slf4j
public class CollectionCopyService {

  private final AuthorizedCollectionService authorizedCollectionService;
  private final AuthorizedEntitiesService entitiesService;
  private final CollectionService collectionService;
  private final AlertService alertService;
  private final ReportService reportService;
  private final DashboardService dashboardService;
  private final CollectionScopeService collectionScopeService;

  public IdDto copyCollection(String userId, String collectionId, String newCollectionName) {
    AuthorizedSimpleCollectionDefinitionDto simpleAuthorizedCollectionDefinition = authorizedCollectionService
      .getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);

    ResolvedCollectionDefinitionDto collectionDefinitionDto =
      getResolvedCollectionDefinition(userId, simpleAuthorizedCollectionDefinition).getDefinitionDto();

    IdDto newCollectionId = collectionService.createNewCollectionAndReturnId(
      userId,
      new PartialCollectionDefinitionDto(
        newCollectionName != null ? newCollectionName : collectionDefinitionDto.getName() + " – Copy"
      )
    );

    copyCollectionScope(userId, collectionDefinitionDto, newCollectionId);
    copyCollectionPermissions(userId, collectionDefinitionDto, newCollectionId);
    copyCollectionEntities(userId, collectionDefinitionDto, newCollectionId);
    return newCollectionId;
  }

  private AuthorizedResolvedCollectionDefinitionDto getResolvedCollectionDefinition(
    final String userId,
    final AuthorizedSimpleCollectionDefinitionDto simpleCollectionDefinitionDto
  ) {
    return collectionService.mapToResolvedCollection(
      simpleCollectionDefinitionDto,
      entitiesService.getAuthorizedCollectionEntities(userId, simpleCollectionDefinitionDto.getDefinitionDto().getId())
    );
  }

  private void copyCollectionEntities(String userId, ResolvedCollectionDefinitionDto collectionDefinitionDto,
                                      IdDto newCollectionId) {
    final Map<String, String> uniqueReportCopies = new HashMap<>();

    collectionDefinitionDto.getData().getEntities().forEach(e -> {
      final String originalEntityId = e.getId();
      switch (e.getEntityType()) {
        case REPORT:
          String entityCopyId = uniqueReportCopies.get(originalEntityId);
          if (entityCopyId == null) {
            entityCopyId = reportService.copyAndMoveReport(
              originalEntityId, userId, newCollectionId.getId(), e.getName(), uniqueReportCopies, true
            ).getId();
            uniqueReportCopies.put(originalEntityId, entityCopyId);
          }
          alertService.copyAndMoveAlerts(originalEntityId, entityCopyId);
          break;
        case DASHBOARD:
          dashboardService.copyAndMoveDashboard(
            originalEntityId, userId, newCollectionId.getId(), e.getName(), uniqueReportCopies, true
          );
          break;
        default:
          throw new OptimizeRuntimeException(
            "You can't copy a " + e.getEntityType().toString().toLowerCase() + " to a collection"
          );
      }
    });
  }

  private void copyCollectionPermissions(String userId, ResolvedCollectionDefinitionDto collectionDefinitionDto,
                                         IdDto newCollectionId) {
    collectionDefinitionDto.getData().getRoles().forEach(r -> {
      try {
        if (!r.getIdentity().getId().equals(userId)) {
          collectionService.addRoleToCollection(userId, newCollectionId.getId(), new CollectionRoleDto(r));
        }
      } catch (OptimizeCollectionConflictException e) {
        log.error(e.getMessage());
      }
    });
  }

  private void copyCollectionScope(String userId, ResolvedCollectionDefinitionDto collectionDefinitionDto,
                                   IdDto newCollectionId) {
    collectionDefinitionDto.getData().getScope().forEach(e -> {
      try {
        collectionScopeService.addScopeEntryToCollection(
          userId,
          newCollectionId.getId(),
          new CollectionScopeEntryDto(e)
        );
      } catch (OptimizeCollectionConflictException ex) {
        log.error(ex.getMessage());
      }
    });
  }
}
