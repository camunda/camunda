/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.CombinedProcessReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import org.camunda.optimize.service.entities.dashboard.DashboardImportService;
import org.camunda.optimize.service.entities.report.ReportImportService;
import org.camunda.optimize.service.exceptions.OptimizeImportFileInvalidException;
import org.camunda.optimize.service.identity.IdentityService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.dto.optimize.rest.export.ExportEntityType.COMBINED_REPORT;
import static org.camunda.optimize.dto.optimize.rest.export.ExportEntityType.DASHBOARD;
import static org.camunda.optimize.dto.optimize.rest.export.ExportEntityType.SINGLE_DECISION_REPORT;
import static org.camunda.optimize.dto.optimize.rest.export.ExportEntityType.SINGLE_PROCESS_REPORT;

@AllArgsConstructor
@Component
@Slf4j
public class EntityImportService {

  private final IdentityService identityService;
  private final ReportImportService reportImportService;
  private final DashboardImportService dashboardImportService;
  private final AuthorizedCollectionService collectionService;

  public List<IdResponseDto> importEntities(final String userId,
                                            final String collectionId,
                                            final Set<OptimizeEntityExportDto> entitiesToImport) {
    validateUserAuthorizedToImportEntitiesOrFail(userId);
    validateCollectionAccessOrFail(userId, collectionId);
    validateCompletenessOrFail(entitiesToImport);

    final List<ReportDefinitionExportDto> reportsToImport = retrieveAllReportsToImport(entitiesToImport);
    final List<DashboardDefinitionExportDto> dashboardsToImport = retrieveAllDashboardsToImport(entitiesToImport);

    reportImportService.validateAllReportsOrFail(userId, collectionId, reportsToImport);
    dashboardImportService.validateAllDashboardsOrFail(userId, dashboardsToImport);

    final Map<String, IdResponseDto> originalIdToNewIdMap = new HashMap<>();
    reportImportService.importReportsIntoCollection(userId, collectionId, reportsToImport, originalIdToNewIdMap);
    dashboardImportService.importDashboardsIntoCollection(
      userId,
      collectionId,
      dashboardsToImport,
      originalIdToNewIdMap
    );

    return new ArrayList<>(originalIdToNewIdMap.values());
  }

  private List<ReportDefinitionExportDto> retrieveAllReportsToImport(final Set<OptimizeEntityExportDto> entitiesToImport) {
    return entitiesToImport.stream()
      .filter(exportDto -> SINGLE_PROCESS_REPORT.equals(exportDto.getExportEntityType())
        || SINGLE_DECISION_REPORT.equals(exportDto.getExportEntityType())
        || COMBINED_REPORT.equals(exportDto.getExportEntityType()))
      .map(ReportDefinitionExportDto.class::cast)
      .collect(toList());
  }

  private List<DashboardDefinitionExportDto> retrieveAllDashboardsToImport(final Set<OptimizeEntityExportDto> entitiesToImport) {
    return entitiesToImport.stream()
      .filter(exportDto -> DASHBOARD.equals(exportDto.getExportEntityType()))
      .map(DashboardDefinitionExportDto.class::cast)
      .collect(toList());
  }

  private void validateCollectionAccessOrFail(final String userId,
                                              final String collectionId) {
    if (collectionId != null) {
      collectionService.verifyUserAuthorizedToEditCollectionResources(userId, collectionId);
    }
  }

  private void validateCompletenessOrFail(final Set<OptimizeEntityExportDto> entitiesToImport) {
    final Set<String> importEntityIds =
      entitiesToImport.stream().map(OptimizeEntityExportDto::getId).collect(toSet());
    final Set<String> requiredReportIds = new HashSet<>();

    entitiesToImport.forEach(entity -> {
      if (COMBINED_REPORT.equals(entity.getExportEntityType())) {
        requiredReportIds.addAll(((CombinedProcessReportDefinitionExportDto) entity).getData().getReportIds());

      } else if (DASHBOARD.equals(entity.getExportEntityType())) {
        requiredReportIds.addAll(((DashboardDefinitionExportDto) entity).getReportIds());
      }
    });

    if (!importEntityIds.containsAll(requiredReportIds)) {
      requiredReportIds.removeAll(importEntityIds);
      throw new OptimizeImportFileInvalidException(
        "Could not import entities because the file is incomplete, some reports required by a combined " +
          "report or dashboard are missing. The missing reports have IDs: " + requiredReportIds
      );
    }
  }

  private void validateUserAuthorizedToImportEntitiesOrFail(final String userId) {
    if (!identityService.isSuperUserIdentity(userId)) {
      throw new ForbiddenException(
        String.format(
          "User with ID [%s] is not authorized to import entities. Only superusers are authorized to import entities.",
          userId
        )
      );
    }
  }

}
