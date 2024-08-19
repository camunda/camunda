/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.variable.DecisionVariableService;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.springframework.stereotype.Component;

@Path(DecisionVariablesRestService.DECISION_VARIABLES_PATH)
@Component
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

  @POST
  @Path(DECISION_INPUTS_NAMES_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<DecisionVariableNameResponseDto> getInputVariableNames(
      @Valid final List<DecisionVariableNameRequestDto> variableRequestDto) {
    return decisionVariableService.getInputVariableNames(variableRequestDto);
  }

  @POST
  @Path(DECISION_OUTPUTS_NAMES_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<DecisionVariableNameResponseDto> getOutputVariableNames(
      @Valid final List<DecisionVariableNameRequestDto> variableRequestDto) {
    return decisionVariableService.getOutputVariableNames(variableRequestDto);
  }

  @POST
  @Path("/inputs/values")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<String> getInputValues(
      @Context final ContainerRequestContext requestContext,
      final DecisionVariableValueRequestDto requestDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return decisionVariableService.getInputVariableValues(userId, requestDto);
  }

  @POST
  @Path("/outputs/values")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<String> getOutputValues(
      @Context final ContainerRequestContext requestContext,
      final DecisionVariableValueRequestDto requestDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return decisionVariableService.getOutputVariableValues(userId, requestDto);
  }
}
