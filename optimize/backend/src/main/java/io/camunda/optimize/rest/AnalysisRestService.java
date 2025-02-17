/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.util.TimeZoneUtil.extractTimezone;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

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
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.service.BranchAnalysisService;
import io.camunda.optimize.service.OutlierAnalysisService;
import io.camunda.optimize.service.export.CSVUtils;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(REST_API_PATH + AnalysisRestService.ANALYSIS_PATH)
public class AnalysisRestService {

  public static final String ANALYSIS_PATH = "/analysis";

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

  @PostMapping("/correlation")
  public BranchAnalysisResponseDto getBranchAnalysis(
      @RequestBody final BranchAnalysisRequestDto branchAnalysisDto,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final ZoneId timezone = extractTimezone(request);
    return branchAnalysisService.branchAnalysis(userId, branchAnalysisDto, timezone);
  }

  @PostMapping("/flowNodeOutliers")
  public Map<String, FindingsDto> getFlowNodeOutlierMap(
      @RequestBody final ProcessDefinitionParametersDto parameters,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    validateProvidedFilters(parameters.getFilters());
    final OutlierAnalysisServiceParameters<ProcessDefinitionParametersDto> outlierAnalysisParams =
        new OutlierAnalysisServiceParameters<>(parameters, extractTimezone(request), userId);
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

  @PostMapping("/durationChart")
  public List<DurationChartEntryDto> getCountByDurationChart(
      @RequestBody final FlowNodeOutlierParametersDto parameters,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    validateProvidedFilters(parameters.getFilters());
    final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams =
        new OutlierAnalysisServiceParameters<>(parameters, extractTimezone(request), userId);
    return outlierAnalysisService.getCountByDurationChart(outlierAnalysisParams);
  }

  @PostMapping("/significantOutlierVariableTerms")
  public List<VariableTermDto> getSignificantOutlierVariableTerms(
      @RequestBody final FlowNodeOutlierParametersDto parameters,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    validateProvidedFilters(parameters.getFilters());
    final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams =
        new OutlierAnalysisServiceParameters<>(parameters, extractTimezone(request), userId);
    return outlierAnalysisService.getSignificantOutlierVariableTerms(outlierAnalysisParams);
  }

  @PostMapping(
      path = "/significantOutlierVariableTerms/processInstanceIdsExport",
      produces = {MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
  @ResponseBody
  // Returns octet stream on success, json on potential error
  public ResponseEntity<byte[]> getSignificantOutlierVariableTermsInstanceIds(
      @PathVariable("fileName") final String fileName,
      @RequestBody final FlowNodeOutlierVariableParametersDto parameters,
      final HttpServletRequest request,
      final HttpServletResponse response) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    validateProvidedFilters(parameters.getFilters());
    final String resultFileName = fileName == null ? System.currentTimeMillis() + ".csv" : fileName;
    final OutlierAnalysisServiceParameters<FlowNodeOutlierVariableParametersDto>
        outlierAnalysisParams =
            new OutlierAnalysisServiceParameters<>(parameters, extractTimezone(request), userId);
    final List<String[]> processInstanceIdsCsv =
        CSVUtils.mapIdList(
            outlierAnalysisService.getSignificantOutlierVariableTermsInstanceIds(
                outlierAnalysisParams));

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=" + resultFileName)
        .body(CSVUtils.mapCsvLinesToCsvBytes(processInstanceIdsCsv, ','));
  }

  private void validateProvidedFilters(final List<ProcessFilterDto<?>> filters) {
    if (filters.stream()
        .anyMatch(filter -> FilterApplicationLevel.VIEW == filter.getFilterLevel())) {
      throw new BadRequestException("View level filters cannot be applied during analysis");
    }
  }
}
