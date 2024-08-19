/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.entities.dashboard;

import static io.camunda.optimize.dto.optimize.ReportConstants.API_IMPORT_OWNER_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;

import io.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import io.camunda.optimize.dto.optimize.rest.ImportIndexMismatchDto;
import io.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import io.camunda.optimize.service.dashboard.DashboardService;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.index.DashboardIndex;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.exceptions.OptimizeImportFileInvalidException;
import io.camunda.optimize.service.exceptions.OptimizeImportIncorrectIndexVersionException;
import io.camunda.optimize.service.util.DataUtil;
import io.camunda.optimize.service.util.IdGenerator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class DashboardImportService {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(DashboardImportService.class);
  private final DashboardWriter dashboardWriter;
  private final DashboardService dashboardService;
  private final OptimizeIndexNameService optimizeIndexNameService;

  public DashboardImportService(
      final DashboardWriter dashboardWriter,
      final DashboardService dashboardService,
      final OptimizeIndexNameService optimizeIndexNameService) {
    this.dashboardWriter = dashboardWriter;
    this.dashboardService = dashboardService;
    this.optimizeIndexNameService = optimizeIndexNameService;
  }

  public void validateAllDashboardsOrFail(
      final List<DashboardDefinitionExportDto> dashboardsToImport) {
    validateAllDashboardsOrFail(null, dashboardsToImport);
  }

  public void validateAllDashboardsOrFail(
      final String userId, final List<DashboardDefinitionExportDto> dashboardsToImport) {
    final Set<ImportIndexMismatchDto> indexMismatches = new HashSet<>();

    dashboardsToImport.stream()
        .mapToInt(DashboardDefinitionExportDto::getSourceIndexVersion)
        .distinct()
        .forEach(
            indexVersion -> {
              try {
                validateIndexVersionOrFail(indexVersion);
              } catch (final OptimizeImportIncorrectIndexVersionException e) {
                indexMismatches.addAll(e.getMismatchingIndices());
              }
            });

    dashboardsToImport.forEach(
        exportedDto -> {
          dashboardService.validateDashboardDescription(exportedDto.getDescription());
          validateDashboardFiltersOrFail(userId, exportedDto);
        });

    if (!indexMismatches.isEmpty()) {
      throw new OptimizeImportIncorrectIndexVersionException(
          "Could not import because source and target index versions do not match for at least one dashboard.",
          indexMismatches);
    }
  }

  public void importDashboardsIntoCollection(
      final String collectionId,
      final List<DashboardDefinitionExportDto> dashboardsToImport,
      final Map<String, EntityIdResponseDto> originalIdToNewIdMap) {
    importDashboardsIntoCollection(null, collectionId, dashboardsToImport, originalIdToNewIdMap);
  }

  public void importDashboardsIntoCollection(
      final String userId,
      final String collectionId,
      final List<DashboardDefinitionExportDto> dashboardsToImport,
      final Map<String, EntityIdResponseDto> originalIdToNewIdMap) {
    dashboardsToImport.forEach(
        exportedDto ->
            importDashboardIntoCollection(userId, collectionId, exportedDto, originalIdToNewIdMap));
  }

  private void importDashboardIntoCollection(
      final String userId,
      final String collectionId,
      final DashboardDefinitionExportDto dashboardToImport,
      final Map<String, EntityIdResponseDto> originalIdToNewIdMap) {
    // adjust the ID of each report resource within dashboard, filtering out external resources
    // where the ID is a URL
    dashboardToImport.getTiles().stream()
        .filter(reportLocationDto -> IdGenerator.isValidId(reportLocationDto.getId()))
        .forEach(
            reportLocationDto ->
                reportLocationDto.setId(
                    originalIdToNewIdMap.get(reportLocationDto.getId()).getId()));
    final IdResponseDto idResponse =
        dashboardWriter.createNewDashboard(
            Optional.ofNullable(userId).orElse(API_IMPORT_OWNER_NAME),
            createDashboardDefinition(collectionId, dashboardToImport));
    originalIdToNewIdMap.put(
        dashboardToImport.getId(),
        new EntityIdResponseDto(idResponse.getId(), EntityType.DASHBOARD));
  }

  private void validateIndexVersionOrFail(final Integer sourceIndexVersion) {
    final int targetIndexVersion = DashboardIndex.VERSION;
    if (targetIndexVersion != sourceIndexVersion) {
      throw new OptimizeImportIncorrectIndexVersionException(
          "Could not import because source and target index versions do not match",
          DataUtil.newHashSet(
              ImportIndexMismatchDto.builder()
                  .indexName(
                      OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion(
                          optimizeIndexNameService.getOptimizeIndexAliasForIndex(
                              DASHBOARD_INDEX_NAME),
                          String.valueOf(targetIndexVersion)))
                  .sourceIndexVersion(sourceIndexVersion)
                  .targetIndexVersion(targetIndexVersion)
                  .build()));
    }
  }

  private void validateDashboardFiltersOrFail(
      final String userId, final DashboardDefinitionExportDto dashboardToImport) {
    try {
      dashboardService.validateDashboardFilters(
          userId, dashboardToImport.getAvailableFilters(), dashboardToImport.getTiles());
    } catch (final Exception e) {
      throw new OptimizeImportFileInvalidException(
          "The provided file includes at least one dashboard with invalid filters. Error: "
              + e.getMessage());
    }
  }

  private DashboardDefinitionRestDto createDashboardDefinition(
      final String collectionId, final DashboardDefinitionExportDto dashboardToImport) {
    final DashboardDefinitionRestDto dashboardDefinition = new DashboardDefinitionRestDto();
    dashboardDefinition.setName(dashboardToImport.getName());
    dashboardDefinition.setDescription(dashboardToImport.getDescription());
    dashboardDefinition.setCollectionId(collectionId);
    dashboardDefinition.setAvailableFilters(dashboardToImport.getAvailableFilters());
    dashboardDefinition.setTiles(dashboardToImport.getTiles());
    dashboardDefinition.setManagementDashboard(false);
    dashboardDefinition.setInstantPreviewDashboard(dashboardToImport.isInstantPreviewDashboard());
    return dashboardDefinition;
  }
}
