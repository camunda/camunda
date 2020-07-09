/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.rest.ProcessRawDataCsvExportRequestDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.export.ExportService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.validation.Valid;
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
import java.util.Optional;

import static org.camunda.optimize.rest.util.TimeZoneUtil.extractTimezone;

@AllArgsConstructor
@Path("/export")
@Secured
@Component
public class ExportRestService {

  private final ExportService exportService;
  private final SessionService sessionService;

  @GET
  // octet stream on success, json on potential error
  @Produces(value = {MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
  @Path("csv/{reportId}/{fileName}")
  public Response getCsvReport(@Context ContainerRequestContext requestContext,
                               @PathParam("reportId") String reportId,
                               @PathParam("fileName") String fileName) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final ZoneId timezone = extractTimezone(requestContext);

    final Optional<byte[]> csvForReport =
      exportService.getCsvBytesForEvaluatedReportResult(userId, reportId, timezone);

    return csvForReport
      .map(csvBytes -> createOctetStreamResponse(fileName, csvBytes))
      .orElse(Response.status(Response.Status.NOT_FOUND).build());
  }

  @POST
  // octet stream on success, json on potential error
  @Produces(value = {MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
  @Path("csv/process/rawData/{fileName}")
  public Response getRawDataCsv(@Context final ContainerRequestContext requestContext,
                                @PathParam("fileName") final String fileName,
                                @Valid final ProcessRawDataCsvExportRequestDto request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final ZoneId timezone = extractTimezone(requestContext);

    final SingleProcessReportDefinitionDto reportDefinitionDto = SingleProcessReportDefinitionDto.builder()
      .reportType(ReportType.PROCESS)
      .combined(false)
      .data(
        ProcessReportDataDto.builder()
          .processDefinitionKey(request.getProcessDefinitionKey())
          .processDefinitionVersions(request.getProcessDefinitionVersions())
          .tenantIds(request.getTenantIds())
          .filter(request.getFilter())
          .configuration(SingleReportConfigurationDto.builder().includedColumns(request.getIncludedColumns()).build())
          .view(new ProcessViewDto(ProcessViewProperty.RAW_DATA))
          .groupBy(new NoneGroupByDto())
          .visualization(ProcessVisualization.TABLE)
          .build()
      )
      .build();

    return createOctetStreamResponse(
      fileName,
      exportService.getCsvBytesForEvaluatedReportResult(userId, reportDefinitionDto, timezone)
    );
  }

  private Response createOctetStreamResponse(final String fileName,
                                             final byte[] csvBytesForEvaluatedReportResult) {
    return Response
      .ok(
        csvBytesForEvaluatedReportResult,
        MediaType.APPLICATION_OCTET_STREAM
      )
      .header("Content-Disposition", "attachment; filename=" + createFileName(fileName))
      .build();
  }

  private String createFileName(final String fileName) {
    return fileName == null ? System.currentTimeMillis() + ".csv" : fileName;
  }
}
