/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableReportValuesRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.rest.GetVariableNamesForReportsRequestDto;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.variable.ProcessVariableService;
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
@Path(ProcessVariableRestService.PROCESS_VARIABLES_PATH)
@Component
public class ProcessVariableRestService {

  public static final String PROCESS_VARIABLES_PATH = "/variables";

  private final ProcessVariableService processVariableService;
  private final SessionService sessionService;

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<ProcessVariableNameResponseDto> getVariableNames(
    @Context final ContainerRequestContext requestContext,
    @Valid final List<ProcessVariableNameRequestDto> variableRequestDtos) {
    return processVariableService.getVariableNames(variableRequestDtos);
  }

  @POST
  @Path("/reports")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<ProcessVariableNameResponseDto> getVariableNamesForReports(@Context ContainerRequestContext requestContext,
                                                                         GetVariableNamesForReportsRequestDto requestDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return processVariableService.getVariableNamesForAuthorizedReports(userId, requestDto.getReportIds());
  }

  @POST
  @Path("/values")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<String> getVariableValues(@Context ContainerRequestContext requestContext,
                                        ProcessVariableValueRequestDto variableValueRequestDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return processVariableService.getVariableValues(userId, variableValueRequestDto);
  }

  @POST
  @Path("/values/reports")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<String> getVariableValuesForReports(@Context ContainerRequestContext requestContext,
                                                  ProcessVariableReportValuesRequestDto requestDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return processVariableService.getVariableValuesForReports(userId, requestDto);
  }

}
