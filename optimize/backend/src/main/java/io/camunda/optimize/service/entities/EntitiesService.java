/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.entities;

import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntitiesDeleteRequestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import io.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import io.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.collection.CollectionService;
import io.camunda.optimize.service.dashboard.DashboardService;
import io.camunda.optimize.service.dashboard.InstantPreviewDashboardService;
import io.camunda.optimize.service.db.reader.EntitiesReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import io.camunda.optimize.service.security.AuthorizedEntitiesService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class EntitiesService {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(EntitiesService.class);
  private final CollectionService collectionService;
  private final AuthorizedEntitiesService authorizedEntitiesService;
  private final EntitiesReader entitiesReader;
  private final ReportService reportService;
  private final DashboardService dashboardService;
  private final AuthorizedCollectionService authorizedCollectionService;
  private final InstantPreviewDashboardService instantPreviewDashboardService;

  public EntitiesService(
      final CollectionService collectionService,
      final AuthorizedEntitiesService authorizedEntitiesService,
      final EntitiesReader entitiesReader,
      final ReportService reportService,
      final DashboardService dashboardService,
      final AuthorizedCollectionService authorizedCollectionService,
      final InstantPreviewDashboardService instantPreviewDashboardService) {
    this.collectionService = collectionService;
    this.authorizedEntitiesService = authorizedEntitiesService;
    this.entitiesReader = entitiesReader;
    this.reportService = reportService;
    this.dashboardService = dashboardService;
    this.authorizedCollectionService = authorizedCollectionService;
    this.instantPreviewDashboardService = instantPreviewDashboardService;
  }

  public List<EntityResponseDto> getAllEntities(final String userId) {
    final List<AuthorizedCollectionDefinitionDto> collectionDefinitions =
        collectionService.getAllCollectionDefinitions(userId);
    final Map<String, Map<EntityType, Long>> collectionEntityCounts =
        entitiesReader.countEntitiesForCollections(
            collectionDefinitions.stream()
                .map(AuthorizedCollectionDefinitionDto::getDefinitionDto)
                .collect(Collectors.toList()));
    final List<EntityResponseDto> privateEntities =
        authorizedEntitiesService.getAuthorizedPrivateEntities(userId);

    return Stream.concat(
            collectionDefinitions.stream()
                .map(AuthorizedCollectionDefinitionDto::toEntityDto)
                .peek(
                    entityDto ->
                        entityDto
                            .getData()
                            .setSubEntityCounts(collectionEntityCounts.get(entityDto.getId()))),
            privateEntities.stream())
        .sorted(
            Comparator.comparing(EntityResponseDto::getEntityType)
                .thenComparing(EntityResponseDto::getLastModified, Comparator.reverseOrder()))
        .collect(Collectors.toList());
  }

  public EntityNameResponseDto getEntityNames(
      final EntityNameRequestDto requestDto, final String locale) {
    final Optional<EntityNameResponseDto> entityNames =
        entitiesReader.getEntityNames(requestDto, locale);

    return entityNames.orElseThrow(
        () -> {
          final String reason =
              String.format("Could not get entity names search request %s", requestDto);
          return new NotFoundException(reason);
        });
  }

  // For dashboards and collections, we only check for authorization. For reports, we also check for
  // conflicts
  public boolean entitiesHaveConflicts(
      final EntitiesDeleteRequestDto entities, final String userId) {
    entities
        .getDashboards()
        .forEach(
            dashboardId -> {
              final DashboardDefinitionRestDto dashboardDefinitionRestDto =
                  dashboardService.getDashboardDefinitionAsService(dashboardId);
              if (dashboardDefinitionRestDto.getCollectionId() != null) {
                dashboardService.verifyUserHasAccessToDashboardCollection(
                    userId, dashboardService.getDashboardDefinitionAsService(dashboardId));
              }
            });
    entities
        .getCollections()
        .forEach(
            collectionId ->
                authorizedCollectionService.verifyUserAuthorizedToEditCollectionResources(
                    userId, collectionId));
    return reportsHaveConflicts(entities, userId);
  }

  public void bulkDeleteEntities(final EntitiesDeleteRequestDto entities, final String userId) {
    for (final String reportId : entities.getReports()) {
      try {
        reportService.deleteReportAsUser(userId, reportId, true);
      } catch (final NotFoundException | OptimizeRuntimeException e) {
        LOG.debug("The report with id {} could not be deleted: {}", reportId, e);
      }
    }

    for (final String dashboardId : entities.getDashboards()) {
      try {
        dashboardService.deleteDashboardAsUser(dashboardId, userId);
      } catch (final NotFoundException | OptimizeRuntimeException e) {
        LOG.debug("The dashboard with id {} could not be deleted: {}", dashboardId, e);
      }
    }

    for (final String collectionId : entities.getCollections()) {
      try {
        collectionService.deleteCollection(userId, collectionId, true);
      } catch (final NotFoundException | OptimizeRuntimeException e) {
        LOG.debug("The collection with id {} could not be deleted: {}", collectionId, e);
      }
    }
  }

  private boolean conflictingItemIsUndeletedDashboard(
      final ConflictedItemDto item, final EntitiesDeleteRequestDto entitiesDeleteRequestDto) {
    return item.getType().equals(ConflictedItemType.DASHBOARD)
        && !entitiesDeleteRequestDto.getDashboards().contains(item.getId());
  }

  private boolean conflictingItemIsUndeletedCombinedReport(
      final ConflictedItemDto item, final List<String> reportIds) {
    return item.getType().equals(ConflictedItemType.COMBINED_REPORT)
        && !reportIds.contains(item.getId());
  }

  private boolean reportsHaveConflicts(
      final EntitiesDeleteRequestDto entities, final String userId) {
    final List<String> reportIds = entities.getReports();
    return reportIds.stream()
        .anyMatch(
            entry -> {
              final Set<ConflictedItemDto> conflictedItemDtos =
                  reportService.getConflictedItemsFromReportDefinition(userId, entry);
              return conflictedItemDtos.stream()
                  .anyMatch(
                      conflictedItemDto ->
                          conflictingItemIsUndeletedDashboard(conflictedItemDto, entities)
                              || conflictedItemDto.getType().equals(ConflictedItemType.ALERT)
                              || conflictingItemIsUndeletedCombinedReport(
                                  conflictedItemDto, reportIds));
            });
  }
}
