/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.entities.dashboard;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.entities.report.ReportExportService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@AllArgsConstructor
@Component
@Slf4j
public class DashboardExportService {

  private final DashboardService dashboardService;
  private final ReportExportService reportExportService;
  private final AuthorizedCollectionService collectionService;

  public List<OptimizeEntityExportDto> getCompleteDashboardExport(final Set<String> dashboardIds) {
    log.debug("Exporting dashboards with IDs {} via API.", dashboardIds);
    final List<DashboardDefinitionRestDto> dashboards = retrieveDashboardDefinitionsOrFailIfMissing(dashboardIds);
    final List<ReportDefinitionDto<?>> reports = retrieveRelevantReportDefinitionsOrFailIfMissing(dashboards);

    final List<OptimizeEntityExportDto> exportDtos = reports.stream()
      .map(ReportDefinitionExportDto::mapReportDefinitionToExportDto)
      .collect(toList());
    exportDtos.addAll(dashboards.stream().map(DashboardDefinitionExportDto::new).collect(toList()));

    return exportDtos;
  }

  public List<OptimizeEntityExportDto> getCompleteDashboardExport(final String userId,
                                                                  final String dashboardId) {
    log.debug("Exporting dashboard with ID {} as user {}.", dashboardId, userId);
    final List<DashboardDefinitionRestDto> dashboards = retrieveDashboardDefinitionsOrFailIfMissing(Set.of(dashboardId));
    validateUserAuthorizedToAccessDashboardsOrFail(userId, dashboards);

    final List<ReportDefinitionDto<?>> reports = retrieveRelevantReportDefinitionsOrFailIfMissing(dashboards);
    reportExportService.validateReportAuthorizationsOrFail(userId, reports);

    final List<OptimizeEntityExportDto> exportDtos = reports.stream()
      .map(ReportDefinitionExportDto::mapReportDefinitionToExportDto)
      .collect(toList());
    exportDtos.addAll(dashboards.stream().map(DashboardDefinitionExportDto::new).collect(toList()));

    return exportDtos;
  }

  private List<DashboardDefinitionRestDto> retrieveDashboardDefinitionsOrFailIfMissing(final Set<String> dashboardIds) {
    final List<DashboardDefinitionRestDto> dashboardDefinitions =
      dashboardService.getDashboardDefinitionsAsService(dashboardIds);

    if (dashboardDefinitions.size() != dashboardIds.size()) {
      final List<String> foundIds = dashboardDefinitions.stream()
        .map(DashboardDefinitionRestDto::getId)
        .collect(toList());
      final Set<String> missingDashboardIds = new HashSet<>(dashboardIds);
      foundIds.forEach(missingDashboardIds::remove);
      throw new NotFoundException("Could not find dashboards with IDs " + missingDashboardIds);
    }

    return dashboardDefinitions;
  }

  private List<ReportDefinitionDto<?>> retrieveRelevantReportDefinitionsOrFailIfMissing(
    final List<DashboardDefinitionRestDto> dashboards) {
    final Set<String> reportIds = dashboards.stream()
      .flatMap(d -> d.getReportIds().stream())
      .collect(toSet());
    try {
      return reportExportService.retrieveReportDefinitionsOrFailIfMissing(reportIds);
    } catch (NotFoundException e) {
      throw new OptimizeRuntimeException(
        "Could not retrieve some reports required by this dashboard."
      );
    }
  }

  private void validateUserAuthorizedToAccessDashboardsOrFail(final String userId,
                                                              final List<DashboardDefinitionRestDto> dashboards) {
    final Set<String> unauthorizedCollectionIds = new HashSet<>();
    dashboards.stream()
      .map(DashboardDefinitionRestDto::getCollectionId)
      .distinct()
      .forEach(collectionId -> {
        try {
          collectionService.verifyUserAuthorizedToEditCollectionResources(userId, collectionId);
        } catch (ForbiddenException e) {
          unauthorizedCollectionIds.add(collectionId);
        }
      });

    if (!unauthorizedCollectionIds.isEmpty()) {
      throw new ForbiddenException(
        String.format(
          "The user with ID %s is not authorized to access collections with IDs %s.",
          userId,
          unauthorizedCollectionIds
        )
      );
    }
  }
}
