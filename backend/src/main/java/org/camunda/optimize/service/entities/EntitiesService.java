/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.entities;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntitiesDeleteRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.es.reader.EntitiesReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.onboardinglistener.OnboardingDashboardCreationService;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.AuthorizedEntitiesService;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
@Component
@Slf4j
public class EntitiesService {

  private final CollectionService collectionService;
  private final AuthorizedEntitiesService authorizedEntitiesService;
  private final EntitiesReader entitiesReader;
  private final ReportService reportService;
  private final DashboardService dashboardService;
  private final AuthorizedCollectionService authorizedCollectionService;
  private OnboardingDashboardCreationService onboardingService;

  public List<EntityResponseDto> getAllEntities(final String userId) {
    final List<AuthorizedCollectionDefinitionDto> collectionDefinitions =
      collectionService.getAllCollectionDefinitions(userId);
    final Map<String, Map<EntityType, Long>> collectionEntityCounts = entitiesReader.countEntitiesForCollections(
      collectionDefinitions.stream()
        .map(AuthorizedCollectionDefinitionDto::getDefinitionDto)
        .collect(Collectors.toList())
    );
    final List<EntityResponseDto> privateEntities = authorizedEntitiesService.getAuthorizedPrivateEntities(userId);

    return Stream.concat(
      collectionDefinitions.stream()
        .map(AuthorizedCollectionDefinitionDto::toEntityDto)
        .peek(entityDto -> entityDto.getData().setSubEntityCounts(collectionEntityCounts.get(entityDto.getId()))),
      privateEntities.stream()
    )
      .sorted(
        Comparator.comparing(EntityResponseDto::getEntityType)
          .thenComparing(EntityResponseDto::getLastModified, Comparator.reverseOrder())
      ).collect(Collectors.toList());
  }

  public EntityNameResponseDto getEntityNames(final EntityNameRequestDto requestDto, final String userId) {

    Optional<EntityNameResponseDto> entityNames = entitiesReader.getEntityNames(requestDto);
    // If it's a click for a magic link
    if (requestDto.getCollectionId() != null && requestDto.getDashboardId() != null &&
        requestDto.getDashboardId().equals(requestDto.getCollectionId())) {
      if (entityNames.isEmpty()) {
        onboardingService.createNewDashboardForProcess(userId, requestDto.getDashboardId());
        entityNames = entitiesReader.getEntityNames(requestDto);
      } else {
        onboardingService.addUserAsEditorToAutomaticallyCreatedCollection(requestDto.getCollectionId(), userId);
      }
    }
    return entityNames.orElseThrow(() -> {
      String reason = String.format("Could not get entity names search request %s", requestDto);
      return new NotFoundException(reason);
    });
  }

  // For dashboards and collections, we only check for authorization. For reports, we also check for conflicts
  public boolean entitiesHaveConflicts(EntitiesDeleteRequestDto entities, String userId) {
    entities.getDashboards().forEach(dashboardId -> {
      DashboardDefinitionRestDto dashboardDefinitionRestDto = dashboardService.getDashboardDefinitionAsService(
        dashboardId);
      if (dashboardDefinitionRestDto.getCollectionId() != null) {
        dashboardService.verifyUserHasAccessToDashboardCollection(
          userId,
          dashboardService.getDashboardDefinitionAsService(dashboardId)
        );
      }
    });
    entities.getCollections()
      .forEach(collectionId -> authorizedCollectionService.verifyUserAuthorizedToEditCollectionResources(
        userId,
        collectionId
      ));
    return reportsHaveConflicts(entities, userId);
  }

  public void bulkDeleteEntities(EntitiesDeleteRequestDto entities, String userId) {
    for (String reportId : entities.getReports()) {
      try {
        reportService.deleteReportAsUser(userId, reportId, true);
      } catch (NotFoundException | OptimizeRuntimeException e) {
        log.debug("The report with id {} could not be deleted: {}", reportId, e);
      }
    }

    for (String dashboardId : entities.getDashboards()) {
      try {
        dashboardService.deleteDashboardAsUser(dashboardId, userId);
      } catch (NotFoundException | OptimizeRuntimeException e) {
        log.debug("The dashboard with id {} could not be deleted: {}", dashboardId, e);
      }
    }

    for (String collectionId : entities.getCollections()) {
      try {
        collectionService.deleteCollection(userId, collectionId, true);
      } catch (NotFoundException | OptimizeRuntimeException e) {
        log.debug("The collection with id {} could not be deleted: {}", collectionId, e);
      }
    }
  }

  private boolean conflictingItemIsUndeletedDashboard(ConflictedItemDto item,
                                                      EntitiesDeleteRequestDto entitiesDeleteRequestDto) {
    return item.getType().equals(ConflictedItemType.DASHBOARD) && !entitiesDeleteRequestDto.getDashboards()
      .contains(item.getId());
  }

  private boolean conflictingItemIsUndeletedCombinedReport(ConflictedItemDto item,
                                                           List<String> reportIds) {
    return item.getType().equals(ConflictedItemType.COMBINED_REPORT) && !reportIds.contains(
      item.getId());
  }

  private boolean reportsHaveConflicts(EntitiesDeleteRequestDto entities, String userId) {
    List<String> reportIds = entities.getReports();
    return reportIds.stream().anyMatch(entry -> {
      Set<ConflictedItemDto> conflictedItemDtos =
        reportService.getConflictedItemsFromReportDefinition(userId, entry);
      return conflictedItemDtos.stream()
        .anyMatch(conflictedItemDto -> conflictingItemIsUndeletedDashboard(conflictedItemDto, entities)
          || conflictedItemDto.getType().equals(ConflictedItemType.ALERT)
          || conflictingItemIsUndeletedCombinedReport(conflictedItemDto, reportIds));
    });
  }

}
