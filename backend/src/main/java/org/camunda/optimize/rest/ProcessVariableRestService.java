/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableReportValuesRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.rest.GetVariableNamesForReportsRequestDto;
import org.camunda.optimize.rest.util.TimeZoneUtil;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.variable.ProcessVariableLabelService;
import org.camunda.optimize.service.variable.ProcessVariableService;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Path(ProcessVariableRestService.PROCESS_VARIABLES_PATH)
@Component
public class ProcessVariableRestService {

  public static final String PROCESS_VARIABLES_PATH = "/variables";

  private final ProcessVariableService processVariableService;
  private final SessionService sessionService;
  private final ProcessVariableLabelService processVariableLabelService;

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<ProcessVariableNameResponseDto> getVariableNames(
      @Context final ContainerRequestContext requestContext,
      @Valid final ProcessVariableNameRequestDto variableRequestDto) {
    variableRequestDto.setTimezone(TimeZoneUtil.extractTimezone(requestContext));
    return processVariableService.getVariableNames(variableRequestDto);
  }

  @POST
  @Path("/reports")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<ProcessVariableNameResponseDto> getVariableNamesForReports(
      @Context ContainerRequestContext requestContext,
      GetVariableNamesForReportsRequestDto requestDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return processVariableService.getVariableNamesForAuthorizedReports(
        userId, requestDto.getReportIds());
  }

  @POST
  @Path("/values")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<String> getVariableValues(
      @Context ContainerRequestContext requestContext,
      ProcessVariableValueRequestDto variableValueRequestDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return processVariableService.getVariableValues(userId, variableValueRequestDto);
  }

  @POST
  @Path("/values/reports")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<String> getVariableValuesForReports(
      @Context ContainerRequestContext requestContext,
      ProcessVariableReportValuesRequestDto requestDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return processVariableService.getVariableValuesForReports(userId, requestDto);
  }

  @POST
  @Path("/labels")
  @Consumes(MediaType.APPLICATION_JSON)
  public void modifyVariableLabels(
      @Context ContainerRequestContext requestContext,
      @Valid DefinitionVariableLabelsDto definitionVariableLabelsDto) {
    processVariableLabelService.storeVariableLabels(definitionVariableLabelsDto);
  }
}
