/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.AuthorizedEntitiesService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
@Slf4j
public class CollectionEntityService {
  private final AuthorizedEntitiesService authorizedEntitiesService;
  private final AuthorizedCollectionService authorizedCollectionService;
  private final AlertService alertService;
  private final ReportService reportService;
  private final DashboardService dashboardService;

  public List<EntityResponseDto> getAuthorizedCollectionEntities(final String userId, final String collectionId) {
    AuthorizedCollectionDefinitionDto authCollectionDto =
      authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId);
    List<EntityResponseDto> entities = authorizedEntitiesService.getAuthorizedCollectionEntities(userId, collectionId);

    final RoleType currentUserResourceRole = authCollectionDto.getCollectionResourceRole();
    entities = entities.stream()
      .filter(Objects::nonNull)
      .peek(entityDto -> entityDto.setCurrentUserRole(currentUserResourceRole))
      .sorted(
        Comparator.comparing(EntityResponseDto::getEntityType)
          .thenComparing(EntityResponseDto::getLastModified, Comparator.reverseOrder())
      )
      .collect(Collectors.toList());
    return entities;
  }

  public List<AlertDefinitionDto> getStoredAlertsForCollection(String userId, String collectionId) {
    return alertService.getStoredAlertsForCollection(userId, collectionId);
  }

  public List<AuthorizedReportDefinitionResponseDto> findAndFilterReports(String userId, String collectionId) {
    return reportService.findAndFilterReports(userId, collectionId);
  }

  public void copyCollectionEntities(String userId, CollectionDefinitionRestDto collectionDefinitionDto,
                                     String newCollectionId) {
    final Map<String, String> uniqueReportCopies = new HashMap<>();
    List<EntityResponseDto> oldCollectionEntities = getAuthorizedCollectionEntities(userId, collectionDefinitionDto.getId());

    oldCollectionEntities.forEach(e -> {
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
