/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.entities.dashboard;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.dashboard.DashboardService;
import io.camunda.optimize.service.entities.report.ReportExportService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class DashboardExportService {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(DashboardExportService.class);
  private final DashboardService dashboardService;
  private final ReportExportService reportExportService;
  private final AuthorizedCollectionService collectionService;

  public DashboardExportService(
      final DashboardService dashboardService,
      final ReportExportService reportExportService,
      final AuthorizedCollectionService collectionService) {
    this.dashboardService = dashboardService;
    this.reportExportService = reportExportService;
    this.collectionService = collectionService;
  }

  public List<OptimizeEntityExportDto> getCompleteDashboardExport(final Set<String> dashboardIds) {
    LOG.debug("Exporting dashboards with IDs {} via API.", dashboardIds);
    final List<DashboardDefinitionRestDto> dashboards =
        retrieveDashboardDefinitionsOrFailIfMissing(dashboardIds);
    final List<ReportDefinitionDto<?>> reports =
        retrieveRelevantReportDefinitionsOrFailIfMissing(dashboards);

    final List<OptimizeEntityExportDto> exportDtos =
        reports.stream()
            .map(ReportDefinitionExportDto::mapReportDefinitionToExportDto)
            .collect(toList());
    exportDtos.addAll(dashboards.stream().map(DashboardDefinitionExportDto::new).toList());

    return exportDtos;
  }

  public List<OptimizeEntityExportDto> getCompleteDashboardExport(
      final String userId, final String dashboardId) {
    LOG.debug("Exporting dashboard with ID {} as user {}.", dashboardId, userId);
    final List<DashboardDefinitionRestDto> dashboards =
        retrieveDashboardDefinitionsOrFailIfMissing(Set.of(dashboardId));
    validateUserAuthorizedToAccessDashboardsOrFail(userId, dashboards);

    final List<ReportDefinitionDto<?>> reports =
        retrieveRelevantReportDefinitionsOrFailIfMissing(dashboards);
    reportExportService.validateReportAuthorizationsOrFail(userId, reports);

    final List<OptimizeEntityExportDto> exportDtos =
        reports.stream()
            .map(ReportDefinitionExportDto::mapReportDefinitionToExportDto)
            .collect(toList());
    exportDtos.addAll(dashboards.stream().map(DashboardDefinitionExportDto::new).toList());

    return exportDtos;
  }

  private List<DashboardDefinitionRestDto> retrieveDashboardDefinitionsOrFailIfMissing(
      final Set<String> dashboardIds) {
    final List<DashboardDefinitionRestDto> dashboardDefinitions =
        dashboardService.getDashboardDefinitionsAsService(dashboardIds);

    if (dashboardDefinitions.size() != dashboardIds.size()) {
      final List<String> foundIds =
          dashboardDefinitions.stream().map(DashboardDefinitionRestDto::getId).toList();
      final Set<String> missingDashboardIds = new HashSet<>(dashboardIds);
      foundIds.forEach(missingDashboardIds::remove);
      throw new NotFoundException("Could not find dashboards with IDs " + missingDashboardIds);
    }

    return dashboardDefinitions;
  }

  private List<ReportDefinitionDto<?>> retrieveRelevantReportDefinitionsOrFailIfMissing(
      final List<DashboardDefinitionRestDto> dashboards) {
    final Set<String> reportIds =
        dashboards.stream().flatMap(d -> d.getTileIds().stream()).collect(toSet());
    try {
      return reportExportService.retrieveReportDefinitionsOrFailIfMissing(reportIds);
    } catch (final NotFoundException e) {
      throw new OptimizeRuntimeException(
          "Could not retrieve some reports required by this dashboard.");
    }
  }

  private void validateUserAuthorizedToAccessDashboardsOrFail(
      final String userId, final List<DashboardDefinitionRestDto> dashboards) {
    final Set<String> unauthorizedCollectionIds = new HashSet<>();
    dashboards.stream()
        .map(DashboardDefinitionRestDto::getCollectionId)
        .distinct()
        .forEach(
            collectionId -> {
              try {
                collectionService.verifyUserAuthorizedToEditCollectionResources(
                    userId, collectionId);
              } catch (final ForbiddenException e) {
                unauthorizedCollectionIds.add(collectionId);
              }
            });

    if (!unauthorizedCollectionIds.isEmpty()) {
      throw new ForbiddenException(
          String.format(
              "The user with ID %s is not authorized to access collections with IDs %s.",
              userId, unauthorizedCollectionIds));
    }
  }
}
