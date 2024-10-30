/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.SharingRestService.SHARE_PATH;

import io.camunda.optimize.dto.optimize.SettingsDto;
import io.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableRequestDto;
import io.camunda.optimize.service.SettingsService;
import io.camunda.optimize.service.dashboard.DashboardService;
import io.camunda.optimize.service.entities.EntityExportService;
import io.camunda.optimize.service.entities.EntityImportService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.export.JsonReportResultExportService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.variable.ProcessVariableLabelService;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

@Path(PublicApiRestService.PUBLIC_PATH)
@Component
public class PublicApiRestService {

  public static final String PUBLIC_PATH = "/public";

  public static final String EXPORT_SUB_PATH = "/export";
  public static final String IMPORT_SUB_PATH = "/import";
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

  public PublicApiRestService(
      final JsonReportResultExportService jsonReportResultExportService,
      final EntityExportService entityExportService,
      final EntityImportService entityImportService,
      final ReportService reportService,
      final DashboardService dashboardService,
      final ProcessVariableLabelService processVariableLabelService,
      final SettingsService settingsService) {
    this.jsonReportResultExportService = jsonReportResultExportService;
    this.entityExportService = entityExportService;
    this.entityImportService = entityImportService;
    this.reportService = reportService;
    this.dashboardService = dashboardService;
    this.processVariableLabelService = processVariableLabelService;
    this.settingsService = settingsService;
  }

  @GET
  @Path(REPORT_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public List<IdResponseDto> getReportIds(
      final @Context ContainerRequestContext requestContext,
      final @QueryParam("collectionId") String collectionId) {
    validateCollectionIdNotNull(collectionId);
    return reportService.getAllReportIdsInCollection(collectionId);
  }

  @GET
  @Path(DASHBOARD_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public List<IdResponseDto> getDashboardIds(
      final @Context ContainerRequestContext requestContext,
      final @QueryParam("collectionId") String collectionId) {
    validateCollectionIdNotNull(collectionId);
    return dashboardService.getAllDashboardIdsInCollection(collectionId);
  }

  @GET
  @Path(REPORT_EXPORT_DATA_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public PaginatedDataExportDto exportReportData(
      @Context final ContainerRequestContext requestContext,
      @SuppressWarnings("UnresolvedRestParam") @PathParam("reportId") final String reportId,
      @BeanParam @Valid final PaginationScrollableRequestDto paginationRequestDto) {
    final ZoneId timezone = ZoneId.of("UTC");
    try {
      return jsonReportResultExportService.getJsonForEvaluatedReportResult(
          reportId, timezone, PaginationScrollableDto.fromPaginationRequest(paginationRequestDto));
    } catch (final Exception ex) {
      throw new OptimizeRuntimeException(ex);
    }
  }

  @POST
  @Path(REPORT_EXPORT_DEFINITION_SUB_PATH)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<ReportDefinitionExportDto> exportReportDefinition(
      final @Context ContainerRequestContext requestContext,
      final @RequestBody Set<String> reportIds) {
    return entityExportService.getReportExportDtos(
        Optional.ofNullable(reportIds).orElse(Collections.emptySet()));
  }

  @POST
  @Path(DASHBOARD_EXPORT_DEFINITION_SUB_PATH)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<OptimizeEntityExportDto> exportDashboardDefinition(
      final @Context ContainerRequestContext requestContext,
      final @RequestBody Set<String> dashboardIds) {
    return entityExportService.getDashboardExportDtos(
        Optional.ofNullable(dashboardIds).orElse(Collections.emptySet()));
  }

  @POST
  @Path(IMPORT_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<EntityIdResponseDto> importEntities(
      @Context final ContainerRequestContext requestContext,
      @QueryParam("collectionId") final String collectionId,
      final String exportedDtoJson) {
    validateCollectionIdNotNull(collectionId);
    final Set<OptimizeEntityExportDto> exportDtos =
        entityImportService.readExportDtoOrFailIfInvalid(exportedDtoJson);
    return entityImportService.importEntities(collectionId, exportDtos);
  }

  @DELETE
  @Path(REPORT_BY_ID_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteReportDefinition(
      final @Context ContainerRequestContext requestContext,
      final @PathParam("reportId") String reportId) {
    reportService.deleteReport(reportId);
  }

  @DELETE
  @Path(DASHBOARD_BY_ID_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteDashboardDefinition(
      final @Context ContainerRequestContext requestContext,
      final @PathParam("dashboardId") String dashboardId) {
    dashboardService.deleteDashboard(dashboardId);
  }

  @POST
  @Path(LABELS_SUB_PATH)
  @Consumes(MediaType.APPLICATION_JSON)
  public void modifyVariableLabels(
      @Context final ContainerRequestContext requestContext,
      @Valid final DefinitionVariableLabelsDto definitionVariableLabelsDto) {
    processVariableLabelService.storeVariableLabels(definitionVariableLabelsDto);
  }

  private void validateCollectionIdNotNull(final String collectionId) {
    if (collectionId == null) {
      throw new BadRequestException("Must specify a collection ID for this request.");
    }
  }

  @POST
  @Path(SHARE_PATH + "/enable")
  public void enableShare() {
    final SettingsDto settings = SettingsDto.builder().sharingEnabled(true).build();
    settingsService.setSettings(settings);
  }

  @POST
  @Path(SHARE_PATH + "/disable")
  public void disableShare() {
    final SettingsDto settings = SettingsDto.builder().sharingEnabled(false).build();
    settingsService.setSettings(settings);
  }
}
