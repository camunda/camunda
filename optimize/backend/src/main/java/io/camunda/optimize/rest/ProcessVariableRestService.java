/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

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

@Path(ProcessVariableRestService.PROCESS_VARIABLES_PATH)
@Component
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
      @Context final ContainerRequestContext requestContext,
      final GetVariableNamesForReportsRequestDto requestDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return processVariableService.getVariableNamesForAuthorizedReports(
        userId, requestDto.getReportIds());
  }

  @POST
  @Path("/values")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<String> getVariableValues(
      @Context final ContainerRequestContext requestContext,
      final ProcessVariableValueRequestDto variableValueRequestDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return processVariableService.getVariableValues(userId, variableValueRequestDto);
  }

  @POST
  @Path("/values/reports")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<String> getVariableValuesForReports(
      @Context final ContainerRequestContext requestContext,
      final ProcessVariableReportValuesRequestDto requestDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return processVariableService.getVariableValuesForReports(userId, requestDto);
  }

  @POST
  @Path("/labels")
  @Consumes(MediaType.APPLICATION_JSON)
  public void modifyVariableLabels(
      @Context final ContainerRequestContext requestContext,
      @Valid final DefinitionVariableLabelsDto definitionVariableLabelsDto) {
    processVariableLabelService.storeVariableLabels(definitionVariableLabelsDto);
  }
}
