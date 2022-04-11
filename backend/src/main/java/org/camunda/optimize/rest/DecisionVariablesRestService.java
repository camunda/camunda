/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.variable.DecisionVariableService;
import org.springframework.stereotype.Component;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@AllArgsConstructor
@Path(DecisionVariablesRestService.DECISION_VARIABLES_PATH)
@Component
public class DecisionVariablesRestService {

  public static final String DECISION_VARIABLES_PATH = "/decision-variables";
  public static final String DECISION_INPUTS_NAMES_PATH = "/inputs/names";
  public static final String DECISION_OUTPUTS_NAMES_PATH = "/outputs/names";

  private final DecisionVariableService decisionVariableService;
  private final SessionService sessionService;

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
  public List<String> getInputValues(@Context final ContainerRequestContext requestContext,
                                     final DecisionVariableValueRequestDto requestDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return decisionVariableService.getInputVariableValues(userId, requestDto);
  }

  @POST
  @Path("/outputs/values")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<String> getOutputValues(@Context final ContainerRequestContext requestContext,
                                      final DecisionVariableValueRequestDto requestDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return decisionVariableService.getOutputVariableValues(userId, requestDto);
  }

}
