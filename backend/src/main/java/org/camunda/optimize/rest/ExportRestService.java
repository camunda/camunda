/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizationType;
import org.camunda.optimize.dto.optimize.rest.ProcessRawDataCsvExportRequestDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import org.camunda.optimize.service.entities.EntityExportService;
import org.camunda.optimize.service.export.CsvExportService;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.validation.Valid;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.rest.util.TimeZoneUtil.extractTimezone;
import static org.camunda.optimize.service.export.CSVUtils.extractAllProcessInstanceDtoFieldKeys;

@AllArgsConstructor
@Path("/export")
@Component
public class ExportRestService {

  private final CsvExportService csvExportService;
  private final EntityExportService entityExportService;
  private final SessionService sessionService;
  private final AbstractIdentityService identityService;

  @GET
  @Produces(value = {MediaType.APPLICATION_JSON})
  @Path("report/json/{reportId}/{fileName}")
  public Response getJsonReport(@Context ContainerRequestContext requestContext,
                                @PathParam("reportId") String reportId,
                                @PathParam("fileName") String fileName) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);

    final List<ReportDefinitionExportDto> jsonReports =
      entityExportService.getReportExportDtosAsUser(userId, Sets.newHashSet(reportId));

    return createJsonResponse(fileName, jsonReports);
  }

  @GET
  @Produces(value = {MediaType.APPLICATION_JSON})
  @Path("dashboard/json/{dashboardId}/{fileName}")
  public Response getJsonDashboard(@Context ContainerRequestContext requestContext,
                                   @PathParam("dashboardId") String dashboardId,
                                   @PathParam("fileName") String fileName) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);

    final List<OptimizeEntityExportDto> jsonDashboards =
      entityExportService.getCompleteDashboardExportAsUser(userId, dashboardId);

    return createJsonResponse(fileName, jsonDashboards);
  }

  @GET
  // octet stream on success, json on potential error
  @Produces(value = {MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
  @Path("csv/{reportId}/{fileName}")
  public Response getCsvReport(@Context ContainerRequestContext requestContext,
                               @PathParam("reportId") String reportId,
                               @PathParam("fileName") String fileName) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateUserAuthorization(userId);
    final ZoneId timezone = extractTimezone(requestContext);

    final Optional<byte[]> csvForReport =
      csvExportService.getCsvBytesForEvaluatedReportResult(userId, reportId, timezone);

    return csvForReport
      .map(csvBytes -> createOctetStreamResponse(fileName, csvBytes))
      .orElse(Response.status(Response.Status.NOT_FOUND).build());
  }

  /**
   * This endpoint returns only the columns specified in the includedColumns list in the request.
   * All other columns (dto fields, new and existing variables not in includedColumns) are to be excluded.
   * It is used for example to return process instance Ids in the branch analysis export.
   */
  @POST
  // octet stream on success, json on potential error
  @Produces(value = {MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
  @Path("csv/process/rawData/{fileName}")
  public Response getRawDataCsv(@Context final ContainerRequestContext requestContext,
                                @PathParam("fileName") final String fileName,
                                @Valid final ProcessRawDataCsvExportRequestDto request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateUserAuthorization(userId);
    final ZoneId timezone = extractTimezone(requestContext);

    final SingleProcessReportDefinitionRequestDto reportDefinitionDto =
      SingleProcessReportDefinitionRequestDto.builder()
        .reportType(ReportType.PROCESS)
        .combined(false)
        .data(
          ProcessReportDataDto.builder()
            .definitions(List.of(
              new ReportDataDefinitionDto(
                request.getProcessDefinitionKey(),
                request.getProcessDefinitionVersions(),
                request.getTenantIds()
              )
            ))
            .filter(request.getFilter())
            .configuration(SingleReportConfigurationDto.builder()
                             .tableColumns(TableColumnDto.builder()
                                             .includeNewVariables(false)
                                             .excludedColumns(getAllExcludedDtoFields(request))
                                             .includedColumns(request.getIncludedColumns())
                                             .build())
                             .build())
            .view(new ProcessViewDto(ViewProperty.RAW_DATA))
            .groupBy(new NoneGroupByDto())
            .visualization(ProcessVisualization.TABLE)
            .build()
        )
        .build();

    return createOctetStreamResponse(
      fileName,
      csvExportService.getCsvBytesForEvaluatedReportResult(userId, reportDefinitionDto, timezone)
    );
  }

  private void validateUserAuthorization(final String userId) {
    if (!identityService.getUserAuthorizations(userId).contains(AuthorizationType.CSV_EXPORT)) {
      throw new ForbiddenException("User not authorized to export CSVs");
    }
  }

  private List<String> getAllExcludedDtoFields(final ProcessRawDataCsvExportRequestDto request) {
    final List<String> dtoFields = extractAllProcessInstanceDtoFieldKeys();
    dtoFields.removeAll(request.getIncludedColumns());
    return dtoFields;
  }

  private Response createOctetStreamResponse(final String fileName,
                                             final byte[] csvBytesForEvaluatedReportResult) {
    return Response
      .ok(
        csvBytesForEvaluatedReportResult,
        MediaType.APPLICATION_OCTET_STREAM
      )
      .header("Content-Disposition", "attachment; filename=" + createFileName(fileName, ".csv"))
      .build();
  }

  private Response createJsonResponse(final String fileName,
                                      final List<? extends OptimizeEntityExportDto> jsonEntities) {
    return Response
      .ok(
        jsonEntities,
        MediaType.APPLICATION_JSON
      )
      .header("Content-Disposition", "attachment; filename=" + createFileName(fileName, ".json"))
      .build();
  }

  private String createFileName(final String fileName,
                                final String extension) {
    return fileName == null ? System.currentTimeMillis() + extension : fileName;
  }
}
