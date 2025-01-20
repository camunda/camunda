/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.variable.DecisionVariableService;
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
@RequestMapping(REST_API_PATH + DecisionVariablesRestService.DECISION_VARIABLES_PATH)
public class DecisionVariablesRestService {

  public static final String DECISION_VARIABLES_PATH = "/decision-variables";
  public static final String DECISION_INPUTS_NAMES_PATH = "/inputs/names";
  public static final String DECISION_OUTPUTS_NAMES_PATH = "/outputs/names";

  private final DecisionVariableService decisionVariableService;
  private final SessionService sessionService;

  public DecisionVariablesRestService(
      final DecisionVariableService decisionVariableService, final SessionService sessionService) {
    this.decisionVariableService = decisionVariableService;
    this.sessionService = sessionService;
  }

  @PostMapping(DECISION_INPUTS_NAMES_PATH)
  public List<DecisionVariableNameResponseDto> getInputVariableNames(
      @Valid @RequestBody final List<DecisionVariableNameRequestDto> variableRequestDto) {
    return decisionVariableService.getInputVariableNames(variableRequestDto);
  }

  @PostMapping(DECISION_OUTPUTS_NAMES_PATH)
  public List<DecisionVariableNameResponseDto> getOutputVariableNames(
      @Valid @RequestBody final List<DecisionVariableNameRequestDto> variableRequestDto) {
    return decisionVariableService.getOutputVariableNames(variableRequestDto);
  }

  @PostMapping("/inputs/values")
  public List<String> getInputValues(
      @RequestBody final DecisionVariableValueRequestDto requestDto,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return decisionVariableService.getInputVariableValues(userId, requestDto);
  }

  @PostMapping("/outputs/values")
  public List<String> getOutputValues(
      @RequestBody final DecisionVariableValueRequestDto requestDto,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return decisionVariableService.getOutputVariableValues(userId, requestDto);
  }
}
