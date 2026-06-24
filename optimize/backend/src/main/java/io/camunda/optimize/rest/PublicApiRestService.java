/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.SharingRestService.SHARE_PATH;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.SettingsDto;
import io.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import io.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableRequestDto;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.service.SettingsService;
import io.camunda.optimize.service.collection.CollectionScopeService;
import io.camunda.optimize.service.collection.CollectionService;
import io.camunda.optimize.service.dashboard.DashboardService;
import io.camunda.optimize.service.entities.EntityExportService;
import io.camunda.optimize.service.entities.EntityImportService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.export.JsonReportResultExportService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.variable.ProcessVariableLabelService;
import jakarta.validation.Valid;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(REST_API_PATH + PublicApiRestService.PUBLIC_PATH)
public class PublicApiRestService {

  public static final String PUBLIC_PATH = "/public";

  public static final String EXPORT_SUB_PATH = "/export";
  public static final String IMPORT_SUB_PATH = "/import";
  public static final String COLLECTION_SUB_PATH = "/collection";
  public static final String COLLECTION_BY_ID_PATH = COLLECTION_SUB_PATH + "/{collectionId}";
  public static final String COLLECTION_CREATE_SUB_PATH = COLLECTION_SUB_PATH + "/create";
  public static final String COLLECTION_SCOPE_SUB_PATH = COLLECTION_SUB_PATH + "/{id}/scope";
  public static final String REPORT_SUB_PATH = "/report";
  public static final String DASHBOARD_SUB_PATH = "/dashboard";
  public static final String LABELS_SUB_PATH = "/variables/labels";
  public static final String DASHBOARD_EXPORT_DEFINITION_SUB_PATH =
      EXPORT_SUB_PATH + DASHBOARD_SUB_PATH + "/definition/json";
  private static final String REPORT_EXPORT_PATH = EXPORT_SUB_PATH + REPORT_SUB_PATH;
  public static final String REPORT_EXPORT_DEFINITION_SUB_PATH =
      REPORT_EXPORT_PATH + "/definition/json";
  private static final String REPORT_BY_ID_PATH = REPORT_SUB_PATH + "/{reportId}";
  private static final String REPORT_EXPORT_BY_ID_PATH = EXPORT_SUB_PATH + REPORT_BY_ID_PATH;
  private static final String REPORT_EXPORT_DATA_SUB_PATH =
      REPORT_EXPORT_BY_ID_PATH + "/result/json";
  private static final String DASHBOARD_BY_ID_PATH = DASHBOARD_SUB_PATH + "/{dashboardId}";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(PublicApiRestService.class);

  private final JsonReportResultExportService jsonReportResultExportService;
  private final EntityExportService entityExportService;
  private final EntityImportService entityImportService;
  private final ReportService reportService;
  private final DashboardService dashboardService;
  private final ProcessVariableLabelService processVariableLabelService;
  private final SettingsService settingsService;
  private final CollectionService collectionService;
  private final CollectionScopeService collectionScopeService;

  public PublicApiRestService(
      final JsonReportResultExportService jsonReportResultExportService,
      final EntityExportService entityExportService,
      final EntityImportService entityImportService,
      final ReportService reportService,
      final DashboardService dashboardService,
      final ProcessVariableLabelService processVariableLabelService,
      final SettingsService settingsService,
      final CollectionService collectionService,
      final CollectionScopeService collectionScopeService) {
    this.jsonReportResultExportService = jsonReportResultExportService;
    this.entityExportService = entityExportService;
    this.entityImportService = entityImportService;
    this.reportService = reportService;
    this.dashboardService = dashboardService;
    this.processVariableLabelService = processVariableLabelService;
    this.settingsService = settingsService;
    this.collectionService = collectionService;
    this.collectionScopeService = collectionScopeService;
  }

  @GetMapping(REPORT_SUB_PATH)
  public List<IdResponseDto> getReportIds(
      final @RequestParam(name = "collectionId", required = false) String collectionId) {
    validateCollectionIdNotNull(collectionId);
    return reportService.getAllReportIdsInCollection(collectionId);
  }

  @GetMapping(DASHBOARD_SUB_PATH)
  public List<IdResponseDto> getDashboardIds(
      final @RequestParam(name = "collectionId", required = false) String collectionId) {
    validateCollectionIdNotNull(collectionId);
    return dashboardService.getAllDashboardIdsInCollection(collectionId);
  }

  @GetMapping(REPORT_EXPORT_DATA_SUB_PATH)
  public PaginatedDataExportDto exportReportData(
      @PathVariable("reportId") final String reportId,
      @Valid final PaginationScrollableRequestDto paginationRequestDto) {
    final ZoneId timezone = ZoneId.of("UTC");
    try {
      return jsonReportResultExportService.getJsonForEvaluatedReportResult(
          reportId, timezone, PaginationScrollableDto.fromPaginationRequest(paginationRequestDto));
    } catch (final Exception ex) {
      throw new OptimizeRuntimeException(ex);
    }
  }

  @PostMapping(REPORT_EXPORT_DEFINITION_SUB_PATH)
  public List<ReportDefinitionExportDto> exportReportDefinition(
      final @RequestBody Set<String> reportIds) {
    return entityExportService.getReportExportDtos(
        Optional.ofNullable(reportIds).orElse(Collections.emptySet()));
  }

  @PostMapping(DASHBOARD_EXPORT_DEFINITION_SUB_PATH)
  public List<OptimizeEntityExportDto> exportDashboardDefinition(
      final @RequestBody Set<String> dashboardIds) {
    return entityExportService.getDashboardExportDtos(
        Optional.ofNullable(dashboardIds).orElse(Collections.emptySet()));
  }

  @PostMapping(IMPORT_SUB_PATH)
  public List<EntityIdResponseDto> importEntities(
      @RequestParam(name = "collectionId", required = false) final String collectionId,
      final @RequestBody String exportedDtoJson) {
    validateCollectionIdNotNull(collectionId);
    final Set<OptimizeEntityExportDto> exportDtos =
        entityImportService.readExportDtoOrFailIfInvalid(exportedDtoJson);
    return entityImportService.importEntities(collectionId, exportDtos);
  }

  @DeleteMapping(REPORT_BY_ID_PATH)
  public void deleteReportDefinition(final @PathVariable("reportId") String reportId) {
    reportService.deleteReport(reportId);
  }

  @DeleteMapping(DASHBOARD_BY_ID_PATH)
  public void deleteDashboardDefinition(final @PathVariable("dashboardId") String dashboardId) {
    dashboardService.deleteDashboard(dashboardId);
  }

  @PostMapping(LABELS_SUB_PATH)
  public void modifyVariableLabels(
      @Valid final DefinitionVariableLabelsDto definitionVariableLabelsDto) {
    processVariableLabelService.storeVariableLabels(definitionVariableLabelsDto);
  }

  private void validateCollectionIdNotNull(final String collectionId) {
    if (collectionId == null) {
      throw new BadRequestException("Must specify a collection ID for this request.");
    }
  }

  @PostMapping(SHARE_PATH + "/enable")
  public void enableShare() {
    final SettingsDto settings = SettingsDto.builder().sharingEnabled(true).build();
    settingsService.setSettings(settings);
  }

  @PostMapping(SHARE_PATH + "/disable")
  public void disableShare() {
    final SettingsDto settings = SettingsDto.builder().sharingEnabled(false).build();
    settingsService.setSettings(settings);
  }

  // Undocumented API used in consulting to programmatically list collections
  @GetMapping(COLLECTION_SUB_PATH)
  public List<IdResponseDto> getCollectionIds() {
    return collectionService.getAllCollections().stream()
        .map(collectionDef -> new IdResponseDto(collectionDef.getId()))
        .collect(Collectors.toList());
  }

  // Undocumented API used in consulting to read a collection
  @GetMapping(COLLECTION_BY_ID_PATH)
  public CollectionDefinitionDto getCollection(
      final @PathVariable("collectionId") String collectionId) {
    return collectionService.getCollectionDefinition(collectionId);
  }

  // Undocumented API used in consulting to programmatically set up/"import" collections
  @PostMapping(COLLECTION_CREATE_SUB_PATH)
  public IdResponseDto createCollection(
      final @RequestBody PartialCollectionDefinitionRequestDto
              partialCollectionDefinitionCreationRequestDto) {
    return collectionService.createNewCollectionAndReturnId(
        partialCollectionDefinitionCreationRequestDto.getOwnerId(),
        new PartialCollectionDefinitionRequestDto(
            partialCollectionDefinitionCreationRequestDto.getName()));
  }

  // Undocumented API used in consulting to programmatically set up/"import" collections
  @PutMapping(COLLECTION_SCOPE_SUB_PATH)
  public void addScopeEntriesToCollection(
      final @PathVariable("id") String collectionId,
      final @RequestBody List<CollectionScopeEntryDto> scopeEntries) {
    collectionScopeService.addScopeEntriesToCollectionAsAService(collectionId, scopeEntries);
  }
}
