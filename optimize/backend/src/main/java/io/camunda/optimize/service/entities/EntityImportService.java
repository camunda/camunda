/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.entities;

import static io.camunda.optimize.dto.optimize.rest.export.ExportEntityType.COMBINED_REPORT;
import static io.camunda.optimize.dto.optimize.rest.export.ExportEntityType.DASHBOARD;
import static io.camunda.optimize.dto.optimize.rest.export.ExportEntityType.SINGLE_DECISION_REPORT;
import static io.camunda.optimize.dto.optimize.rest.export.ExportEntityType.SINGLE_PROCESS_REPORT;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.CombinedProcessReportDefinitionExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import io.camunda.optimize.service.collection.CollectionService;
import io.camunda.optimize.service.entities.dashboard.DashboardImportService;
import io.camunda.optimize.service.entities.report.ReportImportService;
import io.camunda.optimize.service.exceptions.OptimizeImportFileInvalidException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import io.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import io.camunda.optimize.service.util.mapper.OptimizeDateTimeFormatterFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class EntityImportService {

  private final ReportImportService reportImportService;
  private final DashboardImportService dashboardImportService;
  private final AuthorizedCollectionService authorizedCollectionService;
  private final CollectionService collectionService;

  public EntityImportService(
      final ReportImportService reportImportService,
      final DashboardImportService dashboardImportService,
      final AuthorizedCollectionService authorizedCollectionService,
      final CollectionService collectionService) {
    this.reportImportService = reportImportService;
    this.dashboardImportService = dashboardImportService;
    this.authorizedCollectionService = authorizedCollectionService;
    this.collectionService = collectionService;
  }

  public List<EntityIdResponseDto> importEntities(
      final String collectionId, final Set<OptimizeEntityExportDto> entitiesToImport) {
    validateCompletenessOrFail(entitiesToImport);
    validateNoInstantPreviewEntities(entitiesToImport);
    return importValidatedEntities(collectionId, entitiesToImport);
  }

  public List<EntityIdResponseDto> importInstantPreviewEntities(
      final String collectionId, final Set<OptimizeEntityExportDto> entitiesToImport) {
    validateCompletenessOrFail(entitiesToImport);
    return importValidatedEntities(collectionId, entitiesToImport);
  }

  private List<EntityIdResponseDto> importValidatedEntities(
      final String collectionId, final Set<OptimizeEntityExportDto> entitiesToImport) {
    final List<ReportDefinitionExportDto> reportsToImport =
        retrieveAllReportsToImport(entitiesToImport);
    final List<DashboardDefinitionExportDto> dashboardsToImport =
        retrieveAllDashboardsToImport(entitiesToImport);

    final CollectionDefinitionDto collection =
        getAndValidateCollectionExistsAndIsAccessibleOrFail(null, collectionId);
    reportImportService.validateAllReportsOrFail(collection, reportsToImport);
    dashboardImportService.validateAllDashboardsOrFail(dashboardsToImport);

    final Map<String, EntityIdResponseDto> originalIdToNewIdMap = new HashMap<>();
    reportImportService.importReportsIntoCollection(
        collectionId, reportsToImport, originalIdToNewIdMap);
    dashboardImportService.importDashboardsIntoCollection(
        collectionId, dashboardsToImport, originalIdToNewIdMap);

    return new ArrayList<>(originalIdToNewIdMap.values());
  }

  public List<EntityIdResponseDto> importEntitiesAsUser(
      final String userId,
      final String collectionId,
      final Set<OptimizeEntityExportDto> entitiesToImport) {
    final CollectionDefinitionDto collection =
        getAndValidateCollectionExistsAndIsAccessibleOrFail(userId, collectionId);
    validateCompletenessOrFail(entitiesToImport);

    final List<ReportDefinitionExportDto> reportsToImport =
        retrieveAllReportsToImport(entitiesToImport);
    final List<DashboardDefinitionExportDto> dashboardsToImport =
        retrieveAllDashboardsToImport(entitiesToImport);

    reportImportService.validateAllReportsOrFail(userId, collection, reportsToImport);
    dashboardImportService.validateAllDashboardsOrFail(userId, dashboardsToImport);

    final Map<String, EntityIdResponseDto> originalIdToNewIdMap = new HashMap<>();
    reportImportService.importReportsIntoCollection(
        userId, collectionId, reportsToImport, originalIdToNewIdMap);
    dashboardImportService.importDashboardsIntoCollection(
        userId, collectionId, dashboardsToImport, originalIdToNewIdMap);

    return new ArrayList<>(originalIdToNewIdMap.values());
  }

  public Set<OptimizeEntityExportDto> readExportDtoOrFailIfInvalid(final String exportedDtoJson) {
    if (StringUtils.isEmpty(exportedDtoJson)) {
      throw new OptimizeImportFileInvalidException(
          "Could not import entity because the provided file is null or empty.");
    }

    final ObjectMapper objectMapper =
        new ObjectMapperFactory(new OptimizeDateTimeFormatterFactory().getObject())
            .createOptimizeMapper();

    try {
      // @formatter:off
      final Set<OptimizeEntityExportDto> exportDtos =
          objectMapper.readValue(exportedDtoJson, new TypeReference<>() {});
      // @formatter:on
      final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
      final Set<ConstraintViolation<OptimizeEntityExportDto>> violations = new HashSet<>();
      exportDtos.forEach(exportDto -> violations.addAll(validator.validate(exportDto)));
      if (!violations.isEmpty()) {
        throw new OptimizeImportFileInvalidException(
            String.format(
                "Could not import entities because the provided file contains invalid OptimizeExportDtos. "
                    + "Errors: %s",
                violations.stream()
                    .map(c -> c.getPropertyPath() + " " + c.getMessage())
                    .collect(joining(","))));
      }
      return exportDtos;
    } catch (final JsonProcessingException e) {
      throw new OptimizeImportFileInvalidException(
          "Could not import entities because the provided file is not a valid list of OptimizeEntityExportDtos."
              + " Error:"
              + e.getMessage());
    }
  }

  private List<ReportDefinitionExportDto> retrieveAllReportsToImport(
      final Set<OptimizeEntityExportDto> entitiesToImport) {
    return entitiesToImport.stream()
        .filter(
            entityToImport ->
                SINGLE_PROCESS_REPORT.equals(entityToImport.getExportEntityType())
                    || SINGLE_DECISION_REPORT.equals(entityToImport.getExportEntityType())
                    || COMBINED_REPORT.equals(entityToImport.getExportEntityType()))
        .map(
            reportToImport -> {
              if (reportToImport instanceof SingleProcessReportDefinitionExportDto
                  && ((SingleProcessReportDefinitionExportDto) reportToImport)
                      .getData()
                      .isManagementReport()) {
                throw new OptimizeValidationException("Cannot import management reports");
              }
              return (ReportDefinitionExportDto) reportToImport;
            })
        .toList();
  }

  private List<DashboardDefinitionExportDto> retrieveAllDashboardsToImport(
      final Set<OptimizeEntityExportDto> entitiesToImport) {
    return entitiesToImport.stream()
        .filter(exportDto -> DASHBOARD.equals(exportDto.getExportEntityType()))
        .map(DashboardDefinitionExportDto.class::cast)
        .toList();
  }

  private CollectionDefinitionDto getAndValidateCollectionExistsAndIsAccessibleOrFail(
      final String userId, final String collectionId) {
    return Optional.ofNullable(collectionId)
        .map(
            collId ->
                Optional.ofNullable(userId)
                    .map(
                        user ->
                            authorizedCollectionService
                                .getAuthorizedCollectionDefinitionOrFail(user, collId)
                                .getDefinitionDto())
                    .orElse(collectionService.getCollectionDefinition(collId)))
        .orElse(null);
  }

  private void validateCompletenessOrFail(final Set<OptimizeEntityExportDto> entitiesToImport) {
    final Set<String> importEntityIds =
        entitiesToImport.stream().map(OptimizeEntityExportDto::getId).collect(toSet());
    final Set<String> requiredReportIds = new HashSet<>();

    entitiesToImport.forEach(
        entity -> {
          if (COMBINED_REPORT.equals(entity.getExportEntityType())) {
            requiredReportIds.addAll(
                ((CombinedProcessReportDefinitionExportDto) entity).getData().getReportIds());

          } else if (DASHBOARD.equals(entity.getExportEntityType())) {
            requiredReportIds.addAll(((DashboardDefinitionExportDto) entity).getTileIds());
          }
        });

    if (!importEntityIds.containsAll(requiredReportIds)) {
      requiredReportIds.removeAll(importEntityIds);
      throw new OptimizeImportFileInvalidException(
          "Could not import entities because the file is incomplete, some reports required by a combined "
              + "report or dashboard are missing. The missing reports have IDs: "
              + requiredReportIds);
    }
  }

  private void validateNoInstantPreviewEntities(
      final Set<OptimizeEntityExportDto> entitiesToImport) {
    if (entitiesToImport.stream()
        .anyMatch(
            exportDto ->
                (DASHBOARD.equals(exportDto.getExportEntityType())
                        && ((DashboardDefinitionExportDto) exportDto).isInstantPreviewDashboard())
                    || (SINGLE_PROCESS_REPORT.equals(exportDto.getExportEntityType())
                        && ((SingleProcessReportDefinitionExportDto) exportDto)
                            .getData()
                            .isInstantPreviewReport()))) {
      throw new OptimizeValidationException(
          "Cannot import Instant preview dashboards and reports.");
    }
  }
}
