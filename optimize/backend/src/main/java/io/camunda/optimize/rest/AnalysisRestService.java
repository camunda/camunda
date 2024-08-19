/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.util.TimeZoneUtil.extractTimezone;

import io.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import io.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisResponseDto;
import io.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import io.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierVariableParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.OutlierAnalysisServiceParameters;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.service.BranchAnalysisService;
import io.camunda.optimize.service.OutlierAnalysisService;
import io.camunda.optimize.service.export.CSVUtils;
import io.camunda.optimize.service.security.SessionService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
@Path("/analysis")
public class AnalysisRestService {

  private final BranchAnalysisService branchAnalysisService;
  private final OutlierAnalysisService outlierAnalysisService;
  private final SessionService sessionService;

  public AnalysisRestService(
      final BranchAnalysisService branchAnalysisService,
      final OutlierAnalysisService outlierAnalysisService,
      final SessionService sessionService) {
    this.branchAnalysisService = branchAnalysisService;
    this.outlierAnalysisService = outlierAnalysisService;
    this.sessionService = sessionService;
  }

  @POST
  @Path("/correlation")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public BranchAnalysisResponseDto getBranchAnalysis(
      @Context final ContainerRequestContext requestContext,
      final BranchAnalysisRequestDto branchAnalysisDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final ZoneId timezone = extractTimezone(requestContext);
    return branchAnalysisService.branchAnalysis(userId, branchAnalysisDto, timezone);
  }

  @POST
  @Path("/flowNodeOutliers")
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, FindingsDto> getFlowNodeOutlierMap(
      @Context final ContainerRequestContext requestContext,
      final ProcessDefinitionParametersDto parameters) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateProvidedFilters(parameters.getFilters());
    final OutlierAnalysisServiceParameters<ProcessDefinitionParametersDto> outlierAnalysisParams =
        new OutlierAnalysisServiceParameters<>(parameters, extractTimezone(requestContext), userId);
    final Map<String, FindingsDto> flowNodeOutlierMap =
        outlierAnalysisService.getFlowNodeOutlierMap(outlierAnalysisParams);
    final List<Map.Entry<String, FindingsDto>> sortedFindings =
        flowNodeOutlierMap.entrySet().stream()
            .sorted(
                Comparator.comparing(
                    entry ->
                        entry
                            .getValue()
                            .getHigherOutlier()
                            .map(FindingsDto.Finding::getCount)
                            .orElse(0L),
                    Comparator.reverseOrder()))
            .toList();
    final LinkedHashMap<String, FindingsDto> descendingFindings = new LinkedHashMap<>();
    for (final Map.Entry<String, FindingsDto> finding : sortedFindings) {
      descendingFindings.put(finding.getKey(), finding.getValue());
    }
    return descendingFindings;
  }

  @POST
  @Path("/durationChart")
  @Produces(MediaType.APPLICATION_JSON)
  public List<DurationChartEntryDto> getCountByDurationChart(
      @Context final ContainerRequestContext requestContext,
      final FlowNodeOutlierParametersDto parameters) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateProvidedFilters(parameters.getFilters());
    final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams =
        new OutlierAnalysisServiceParameters<>(parameters, extractTimezone(requestContext), userId);
    return outlierAnalysisService.getCountByDurationChart(outlierAnalysisParams);
  }

  @POST
  @Path("/significantOutlierVariableTerms")
  @Produces(MediaType.APPLICATION_JSON)
  public List<VariableTermDto> getSignificantOutlierVariableTerms(
      @Context final ContainerRequestContext requestContext,
      final FlowNodeOutlierParametersDto parameters) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateProvidedFilters(parameters.getFilters());
    final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams =
        new OutlierAnalysisServiceParameters<>(parameters, extractTimezone(requestContext), userId);
    return outlierAnalysisService.getSignificantOutlierVariableTerms(outlierAnalysisParams);
  }

  @POST
  @Path("/significantOutlierVariableTerms/processInstanceIdsExport")
  // octet stream on success, json on potential error
  @Produces(value = {MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
  public Response getSignificantOutlierVariableTermsInstanceIds(
      @Context final ContainerRequestContext requestContext,
      @PathParam("fileName") final String fileName,
      final FlowNodeOutlierVariableParametersDto parameters) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateProvidedFilters(parameters.getFilters());
    final String resultFileName = fileName == null ? System.currentTimeMillis() + ".csv" : fileName;
    final OutlierAnalysisServiceParameters<FlowNodeOutlierVariableParametersDto>
        outlierAnalysisParams =
            new OutlierAnalysisServiceParameters<>(
                parameters, extractTimezone(requestContext), userId);
    final List<String[]> processInstanceIdsCsv =
        CSVUtils.mapIdList(
            outlierAnalysisService.getSignificantOutlierVariableTermsInstanceIds(
                outlierAnalysisParams));

    return Response.ok(
            CSVUtils.mapCsvLinesToCsvBytes(processInstanceIdsCsv, ','),
            MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=" + resultFileName)
        .build();
  }

  private void validateProvidedFilters(final List<ProcessFilterDto<?>> filters) {
    if (filters.stream()
        .anyMatch(filter -> FilterApplicationLevel.VIEW == filter.getFilterLevel())) {
      throw new BadRequestException("View level filters cannot be applied during analysis");
    }
  }
}
