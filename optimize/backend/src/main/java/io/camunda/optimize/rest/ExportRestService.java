/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.util.TimeZoneUtil.extractTimezone;
import static io.camunda.optimize.service.export.CSVUtils.extractAllPrefixedCountKeys;
import static io.camunda.optimize.service.export.CSVUtils.extractAllProcessInstanceDtoFieldKeys;

import com.google.common.collect.Sets;
import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizationType;
import io.camunda.optimize.dto.optimize.rest.ProcessRawDataCsvExportRequestDto;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import io.camunda.optimize.service.entities.EntityExportService;
import io.camunda.optimize.service.export.CsvExportService;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.validation.Valid;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Path("/export")
@Component
public class ExportRestService {

  private final CsvExportService csvExportService;
  private final EntityExportService entityExportService;
  private final SessionService sessionService;
  private final AbstractIdentityService identityService;

  public ExportRestService(
      final CsvExportService csvExportService,
      final EntityExportService entityExportService,
      final SessionService sessionService,
      final AbstractIdentityService identityService) {
    this.csvExportService = csvExportService;
    this.entityExportService = entityExportService;
    this.sessionService = sessionService;
    this.identityService = identityService;
  }

  @GET
  @Produces(value = {MediaType.APPLICATION_JSON})
  @Path("report/json/{reportId}/{fileName}")
  public Response getJsonReport(
      @Context final ContainerRequestContext requestContext,
      @PathParam("reportId") final String reportId,
      @PathParam("fileName") final String fileName) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);

    final List<ReportDefinitionExportDto> jsonReports =
        entityExportService.getReportExportDtosAsUser(userId, Sets.newHashSet(reportId));

    return createJsonResponse(fileName, jsonReports);
  }

  @GET
  @Produces(value = {MediaType.APPLICATION_JSON})
  @Path("dashboard/json/{dashboardId}/{fileName}")
  public Response getJsonDashboard(
      @Context final ContainerRequestContext requestContext,
      @PathParam("dashboardId") final String dashboardId,
      @PathParam("fileName") final String fileName) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);

    final List<OptimizeEntityExportDto> jsonDashboards =
        entityExportService.getCompleteDashboardExportAsUser(userId, dashboardId);

    return createJsonResponse(fileName, jsonDashboards);
  }

  @GET
  // octet stream on success, json on potential error
  @Produces(value = {MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
  @Path("csv/{reportId}/{fileName}")
  public Response getCsvReport(
      @Context final ContainerRequestContext requestContext,
      @PathParam("reportId") final String reportId,
      @PathParam("fileName") final String fileName) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateAuthorization();
    final ZoneId timezone = extractTimezone(requestContext);

    final Optional<byte[]> csvForReport =
        csvExportService.getCsvBytesForEvaluatedReportResult(userId, reportId, timezone);

    return csvForReport
        .map(csvBytes -> createOctetStreamResponse(fileName, csvBytes))
        .orElse(Response.status(Response.Status.NOT_FOUND).build());
  }

  /**
   * This endpoint returns only the columns specified in the includedColumns list in the request.
   * All other columns (dto fields, new and existing variables not in includedColumns) are to be
   * excluded. It is used for example to return process instance Ids in the branch analysis export.
   */
  @POST
  // octet stream on success, json on potential error
  @Produces(value = {MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
  @Path("csv/process/rawData/{fileName}")
  public Response getRawDataCsv(
      @Context final ContainerRequestContext requestContext,
      @PathParam("fileName") final String fileName,
      @Valid final ProcessRawDataCsvExportRequestDto request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateAuthorization();
    final ZoneId timezone = extractTimezone(requestContext);

    final SingleProcessReportDefinitionRequestDto reportDefinitionDto =
        SingleProcessReportDefinitionRequestDto.builder()
            .reportType(ReportType.PROCESS)
            .combined(false)
            .data(
                ProcessReportDataDto.builder()
                    .definitions(
                        List.of(
                            new ReportDataDefinitionDto(
                                request.getProcessDefinitionKey(),
                                request.getProcessDefinitionVersions(),
                                request.getTenantIds())))
                    .filter(request.getFilter())
                    .configuration(
                        SingleReportConfigurationDto.builder()
                            .tableColumns(
                                TableColumnDto.builder()
                                    .includeNewVariables(false)
                                    .excludedColumns(getAllExcludedFields(request))
                                    .includedColumns(request.getIncludedColumns())
                                    .build())
                            .build())
                    .view(new ProcessViewDto(ViewProperty.RAW_DATA))
                    .groupBy(new NoneGroupByDto())
                    .visualization(ProcessVisualization.TABLE)
                    .build())
            .build();

    return createOctetStreamResponse(
        fileName,
        csvExportService.getCsvBytesForEvaluatedReportResult(
            userId, reportDefinitionDto, timezone));
  }

  private void validateAuthorization() {
    if (!identityService.getEnabledAuthorizations().contains(AuthorizationType.CSV_EXPORT)) {
      throw new ForbiddenException("User not authorized to export CSVs");
    }
  }

  private List<String> getAllExcludedFields(final ProcessRawDataCsvExportRequestDto request) {
    final List<String> excludedFields =
        Stream.concat(
                extractAllProcessInstanceDtoFieldKeys().stream(),
                extractAllPrefixedCountKeys().stream())
            .collect(Collectors.toList());
    excludedFields.removeAll(request.getIncludedColumns());
    return excludedFields;
  }

  private Response createOctetStreamResponse(
      final String fileName, final byte[] csvBytesForEvaluatedReportResult) {
    return Response.ok(csvBytesForEvaluatedReportResult, MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=" + createFileName(fileName, ".csv"))
        .build();
  }

  private Response createJsonResponse(
      final String fileName, final List<? extends OptimizeEntityExportDto> jsonEntities) {
    return Response.ok(jsonEntities, MediaType.APPLICATION_JSON)
        .header("Content-Disposition", "attachment; filename=" + createFileName(fileName, ".json"))
        .build();
  }

  private String createFileName(final String fileName, final String extension) {
    return fileName == null ? System.currentTimeMillis() + extension : fileName;
  }
}
