/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableReportValuesRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import io.camunda.optimize.dto.optimize.rest.GetVariableNamesForReportsRequestDto;
import io.camunda.optimize.rest.util.TimeZoneUtil;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.variable.ProcessVariableLabelService;
import io.camunda.optimize.service.variable.ProcessVariableService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(REST_API_PATH + ProcessVariableRestService.PROCESS_VARIABLES_PATH)
public class ProcessVariableRestService {

  public static final String PROCESS_VARIABLES_PATH = "/variables";

  private final ProcessVariableService processVariableService;
  private final SessionService sessionService;
  private final ProcessVariableLabelService processVariableLabelService;

  public ProcessVariableRestService(
      final ProcessVariableService processVariableService,
      final SessionService sessionService,
      final ProcessVariableLabelService processVariableLabelService) {
    this.processVariableService = processVariableService;
    this.sessionService = sessionService;
    this.processVariableLabelService = processVariableLabelService;
  }

  @PostMapping
  public List<ProcessVariableNameResponseDto> getVariableNames(
      @Valid @RequestBody final ProcessVariableNameRequestDto variableRequestDto,
      final HttpServletRequest request) {
    variableRequestDto.setTimezone(TimeZoneUtil.extractTimezone(request));
    return processVariableService.getVariableNames(variableRequestDto);
  }

  @PostMapping("/reports")
  public List<ProcessVariableNameResponseDto> getVariableNamesForReports(
      @RequestBody final GetVariableNamesForReportsRequestDto requestDto,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return processVariableService.getVariableNamesForAuthorizedReports(
        userId, requestDto.getReportIds());
  }

  @PostMapping("/values")
  public List<String> getVariableValues(
      @RequestBody final ProcessVariableValueRequestDto variableValueRequestDto,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return processVariableService.getVariableValues(userId, variableValueRequestDto);
  }

  @PostMapping("/values/reports")
  public List<String> getVariableValuesForReports(
      @RequestBody final ProcessVariableReportValuesRequestDto requestDto,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return processVariableService.getVariableValuesForReports(userId, requestDto);
  }

  @PostMapping("/labels")
  public void modifyVariableLabels(
      @Valid @RequestBody final DefinitionVariableLabelsDto definitionVariableLabelsDto) {
    processVariableLabelService.storeVariableLabels(definitionVariableLabelsDto);
  }
}
