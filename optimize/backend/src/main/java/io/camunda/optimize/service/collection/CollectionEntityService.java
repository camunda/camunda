/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.collection;

import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import io.camunda.optimize.service.alert.AlertService;
import io.camunda.optimize.service.dashboard.DashboardService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import io.camunda.optimize.service.security.AuthorizedEntitiesService;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class CollectionEntityService {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(CollectionEntityService.class);
  private final AuthorizedEntitiesService authorizedEntitiesService;
  private final AuthorizedCollectionService authorizedCollectionService;
  private final AlertService alertService;
  private final ReportService reportService;
  private final DashboardService dashboardService;

  public CollectionEntityService(
      final AuthorizedEntitiesService authorizedEntitiesService,
      final AuthorizedCollectionService authorizedCollectionService,
      final AlertService alertService,
      final ReportService reportService,
      final DashboardService dashboardService) {
    this.authorizedEntitiesService = authorizedEntitiesService;
    this.authorizedCollectionService = authorizedCollectionService;
    this.alertService = alertService;
    this.reportService = reportService;
    this.dashboardService = dashboardService;
  }

  public List<EntityResponseDto> getAuthorizedCollectionEntities(
      final String userId, final String collectionId) {
    final AuthorizedCollectionDefinitionDto authCollectionDto =
        authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId);
    List<EntityResponseDto> entities =
        authorizedEntitiesService.getAuthorizedCollectionEntities(userId, collectionId);

    final RoleType currentUserResourceRole = authCollectionDto.getCollectionResourceRole();
    entities =
        entities.stream()
            .filter(Objects::nonNull)
            .peek(entityDto -> entityDto.setCurrentUserRole(currentUserResourceRole))
            .sorted(
                Comparator.comparing(EntityResponseDto::getEntityType)
                    .thenComparing(EntityResponseDto::getLastModified, Comparator.reverseOrder()))
            .collect(Collectors.toList());
    return entities;
  }

  public List<AlertDefinitionDto> getStoredAlertsForCollection(
      final String userId, final String collectionId) {
    return alertService.getStoredAlertsForCollection(userId, collectionId);
  }

  public List<AuthorizedReportDefinitionResponseDto> findAndFilterReports(
      final String userId, final String collectionId) {
    return reportService.findAndFilterReports(userId, collectionId);
  }

  public void copyCollectionEntities(
      final String userId,
      final CollectionDefinitionRestDto collectionDefinitionDto,
      final String newCollectionId) {
    final Map<String, String> uniqueReportCopies = new HashMap<>();
    final List<EntityResponseDto> oldCollectionEntities =
        getAuthorizedCollectionEntities(userId, collectionDefinitionDto.getId());

    oldCollectionEntities.forEach(
        e -> {
          final String originalEntityId = e.getId();
          switch (e.getEntityType()) {
            case REPORT:
              String entityCopyId = uniqueReportCopies.get(originalEntityId);
              if (entityCopyId == null) {
                entityCopyId =
                    reportService
                        .copyAndMoveReport(
                            originalEntityId,
                            userId,
                            newCollectionId,
                            e.getName(),
                            uniqueReportCopies,
                            true)
                        .getId();
                uniqueReportCopies.put(originalEntityId, entityCopyId);
              }
              alertService.copyAndMoveAlerts(originalEntityId, entityCopyId);
              break;
            case DASHBOARD:
              dashboardService.copyAndMoveDashboard(
                  originalEntityId, userId, newCollectionId, e.getName(), uniqueReportCopies, true);
              break;
            default:
              throw new OptimizeRuntimeException(
                  "You can't copy a "
                      + e.getEntityType().toString().toLowerCase(Locale.ENGLISH)
                      + " to a collection");
          }
        });
  }
}
