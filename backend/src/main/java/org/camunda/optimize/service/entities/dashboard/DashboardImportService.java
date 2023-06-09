/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.entities.dashboard;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.dto.optimize.rest.ImportIndexMismatchDto;
import org.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.writer.DashboardWriter;
import org.camunda.optimize.service.exceptions.OptimizeImportFileInvalidException;
import org.camunda.optimize.service.exceptions.OptimizeImportIncorrectIndexVersionException;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.common.util.set.Sets;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.dto.optimize.ReportConstants.API_IMPORT_OWNER_NAME;

@AllArgsConstructor
@Component
@Slf4j
public class DashboardImportService {

  private final OptimizeIndexNameService optimizeIndexNameService;
  private final DashboardWriter dashboardWriter;
  private final DashboardService dashboardService;

  public void validateAllDashboardsOrFail(final List<DashboardDefinitionExportDto> dashboardsToImport) {
    validateAllDashboardsOrFail(null, dashboardsToImport);
  }

  public void validateAllDashboardsOrFail(final String userId,
                                          final List<DashboardDefinitionExportDto> dashboardsToImport) {
    final Set<ImportIndexMismatchDto> indexMismatches = new HashSet<>();

    dashboardsToImport.stream()
      .mapToInt(DashboardDefinitionExportDto::getSourceIndexVersion)
      .distinct()
      .forEach(indexVersion -> {
        try {
          validateIndexVersionOrFail(indexVersion);
        } catch (OptimizeImportIncorrectIndexVersionException e) {
          indexMismatches.addAll(e.getMismatchingIndices());
        }
      });

    dashboardsToImport.forEach(exportedDto -> {
      dashboardService.validateDashboardDescription(exportedDto.getDescription());
      validateDashboardFiltersOrFail(userId, exportedDto);
    });

    if (!indexMismatches.isEmpty()) {
      throw new OptimizeImportIncorrectIndexVersionException(
        "Could not import because source and target index versions do not match for at least one dashboard.",
        indexMismatches
      );
    }
  }

  public void importDashboardsIntoCollection(final String collectionId,
                                             final List<DashboardDefinitionExportDto> dashboardsToImport,
                                             final Map<String, EntityIdResponseDto> originalIdToNewIdMap) {
    importDashboardsIntoCollection(null, collectionId, dashboardsToImport, originalIdToNewIdMap);
  }

  public void importDashboardsIntoCollection(final String userId,
                                             final String collectionId,
                                             final List<DashboardDefinitionExportDto> dashboardsToImport,
                                             final Map<String, EntityIdResponseDto> originalIdToNewIdMap) {
    dashboardsToImport.forEach(exportedDto -> importDashboardIntoCollection(
      userId,
      collectionId,
      exportedDto,
      originalIdToNewIdMap
    ));
  }

  private void importDashboardIntoCollection(final String userId,
                                             final String collectionId,
                                             final DashboardDefinitionExportDto dashboardToImport,
                                             final Map<String, EntityIdResponseDto> originalIdToNewIdMap) {
    // adjust the ID of each report resource within dashboard, filtering out external resources where the ID is a URL
    dashboardToImport.getTiles().stream()
      .filter(reportLocationDto -> IdGenerator.isValidId(reportLocationDto.getId()))
      .forEach(
        reportLocationDto -> reportLocationDto.setId(originalIdToNewIdMap.get(reportLocationDto.getId()).getId())
      );
    final IdResponseDto idResponse = dashboardWriter.createNewDashboard(
      Optional.ofNullable(userId).orElse(API_IMPORT_OWNER_NAME),
      createDashboardDefinition(collectionId, dashboardToImport)
    );
    originalIdToNewIdMap.put(
      dashboardToImport.getId(),
      new EntityIdResponseDto(idResponse.getId(), EntityType.DASHBOARD)
    );
  }

  private void validateIndexVersionOrFail(final Integer sourceIndexVersion) {
    final IndexMappingCreator targetIndex = new DashboardIndex();
    if (targetIndex.getVersion() != sourceIndexVersion) {
      throw new OptimizeImportIncorrectIndexVersionException(
        "Could not import because source and target index versions do not match",
        Sets.newHashSet(
          ImportIndexMismatchDto.builder()
            .indexName(optimizeIndexNameService.getOptimizeIndexNameWithVersion(targetIndex))
            .sourceIndexVersion(sourceIndexVersion)
            .targetIndexVersion(targetIndex.getVersion())
            .build()
        )
      );
    }
  }

  private void validateDashboardFiltersOrFail(final String userId,
                                              final DashboardDefinitionExportDto dashboardToImport) {
    try {
      dashboardService.validateDashboardFilters(
        userId,
        dashboardToImport.getAvailableFilters(),
        dashboardToImport.getTiles()
      );
    } catch (Exception e) {
      throw new OptimizeImportFileInvalidException(
        "The provided file includes at least one dashboard with invalid filters. Error: " + e.getMessage()
      );
    }
  }

  private DashboardDefinitionRestDto createDashboardDefinition(final String collectionId,
                                                               final DashboardDefinitionExportDto dashboardToImport) {
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
