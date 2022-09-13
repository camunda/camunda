/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableRequestDto;
import org.camunda.optimize.service.SettingsService;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.entities.EntityExportService;
import org.camunda.optimize.service.entities.EntityImportService;
import org.camunda.optimize.service.export.JsonReportResultExportService;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.variable.ProcessVariableLabelService;
import org.elasticsearch.ElasticsearchStatusException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.rest.SharingRestService.SHARE_PATH;

@AllArgsConstructor
@Slf4j
@Path(PublicApiRestService.PUBLIC_PATH)
@Component
public class PublicApiRestService {
  public static final String PUBLIC_PATH = "/public";

  public static final String EXPORT_SUB_PATH = "/export";
  public static final String IMPORT_SUB_PATH = "/import";
  public static final String REPORT_SUB_PATH = "/report";
  public static final String DASHBOARD_SUB_PATH = "/dashboard";
  public static final String LABELS_SUB_PATH = "/variables/labels";
  private static final String REPORT_EXPORT_PATH = EXPORT_SUB_PATH + REPORT_SUB_PATH;
  private static final String REPORT_BY_ID_PATH = REPORT_SUB_PATH + "/{reportId}";
  private static final String DASHBOARD_BY_ID_PATH = DASHBOARD_SUB_PATH + "/{dashboardId}";
  private static final String REPORT_EXPORT_BY_ID_PATH = EXPORT_SUB_PATH + REPORT_BY_ID_PATH;
  private static final String REPORT_EXPORT_DATA_SUB_PATH = REPORT_EXPORT_BY_ID_PATH + "/result/json";
  public static final String REPORT_EXPORT_DEFINITION_SUB_PATH = REPORT_EXPORT_PATH + "/definition/json";
  public static final String DASHBOARD_EXPORT_DEFINITION_SUB_PATH = EXPORT_SUB_PATH + DASHBOARD_SUB_PATH +
    "/definition/json";

  private final JsonReportResultExportService jsonReportResultExportService;
  private final EntityExportService entityExportService;
  private final EntityImportService entityImportService;
  private final ReportService reportService;
  private final DashboardService dashboardService;
  private final ProcessVariableLabelService processVariableLabelService;
  private final SettingsService settingsService;

  @GET
  @Path(REPORT_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @SneakyThrows
  public List<IdResponseDto> getReportIds(final @Context ContainerRequestContext requestContext,
                                          final @QueryParam("collectionId") String collectionId) {
    validateCollectionIdNotNull(collectionId);
    return reportService.getAllReportIdsInCollection(collectionId);
  }

  @GET
  @Path(DASHBOARD_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @SneakyThrows
  public List<IdResponseDto> getDashboardIds(final @Context ContainerRequestContext requestContext,
                                             final @QueryParam("collectionId") String collectionId) {
    validateCollectionIdNotNull(collectionId);
    return dashboardService.getAllDashboardIdsInCollection(collectionId);
  }

  @GET
  @Path(REPORT_EXPORT_DATA_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @SneakyThrows
  public PaginatedDataExportDto exportReportData(@Context ContainerRequestContext requestContext,
                                                 @SuppressWarnings("UnresolvedRestParam") @PathParam("reportId") String reportId,
                                                 @BeanParam @Valid final PaginationScrollableRequestDto paginationRequestDto) {
    final ZoneId timezone = ZoneId.of("UTC");
    try {
      return jsonReportResultExportService.getJsonForEvaluatedReportResult(
        reportId,
        timezone,
        PaginationScrollableDto.fromPaginationRequest(paginationRequestDto)
      );
    } catch (ElasticsearchStatusException e) {
      // In case the user provides a parsable but invalid scroll id (e.g. scroll id was earlier valid, but now
      // expired) the message from ElasticSearch is a bit cryptic. Therefore, we extract the useful information so
      // that the user gets an appropriate response.
      throw Optional.ofNullable(e.getCause())
        .filter(pag -> pag.getMessage().contains("search_context_missing_exception"))
        .map(pag -> (Exception) new BadRequestException(pag.getMessage()))
        // In case the exception happened for another reason, just re-throw it as is
        .orElse(e);
    }
  }

  @POST
  @Path(REPORT_EXPORT_DEFINITION_SUB_PATH)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<ReportDefinitionExportDto> exportReportDefinition(final @Context ContainerRequestContext requestContext,
                                                                final @RequestBody Set<String> reportIds) {
    return entityExportService.getReportExportDtos(Optional.ofNullable(reportIds).orElse(Collections.emptySet()));
  }

  @POST
  @Path(DASHBOARD_EXPORT_DEFINITION_SUB_PATH)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<OptimizeEntityExportDto> exportDashboardDefinition(final @Context ContainerRequestContext requestContext,
                                                                 final @RequestBody Set<String> dashboardIds) {
    return entityExportService.getDashboardExportDtos(Optional.ofNullable(dashboardIds).orElse(Collections.emptySet()));
  }

  @POST
  @Path(IMPORT_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<EntityIdResponseDto> importEntities(@Context final ContainerRequestContext requestContext,
                                                  @QueryParam("collectionId") String collectionId,
                                                  final String exportedDtoJson) {
    validateCollectionIdNotNull(collectionId);
    final Set<OptimizeEntityExportDto> exportDtos = entityImportService.readExportDtoOrFailIfInvalid(exportedDtoJson);
    return entityImportService.importEntities(collectionId, exportDtos);
  }

  @DELETE
  @Path(REPORT_BY_ID_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteReportDefinition(final @Context ContainerRequestContext requestContext,
                                     final @PathParam("reportId") String reportId) {
    reportService.deleteReport(reportId);
  }

  @DELETE
  @Path(DASHBOARD_BY_ID_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteDashboardDefinition(final @Context ContainerRequestContext requestContext,
                                        final @PathParam("dashboardId") String dashboardId) {
    dashboardService.deleteDashboard(dashboardId);
  }

  @POST
  @Path(LABELS_SUB_PATH)
  @Consumes(MediaType.APPLICATION_JSON)
  public void modifyVariableLabels(@Context ContainerRequestContext requestContext,
                                   @Valid DefinitionVariableLabelsDto definitionVariableLabelsDto) {
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
    SettingsResponseDto settings = SettingsResponseDto.builder().sharingEnabled(true).build();
    settingsService.setSettings(settings);
  }

  @POST
  @Path(SHARE_PATH + "/disable")
  public void disableShare() {
    SettingsResponseDto settings = SettingsResponseDto.builder().sharingEnabled(false).build();
    settingsService.setSettings(settings);
  }
}
