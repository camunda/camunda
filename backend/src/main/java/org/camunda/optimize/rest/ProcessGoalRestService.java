/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.ProcessGoalDto;
import org.camunda.optimize.service.ProcessGoalService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@AllArgsConstructor
@Path("/process")
@Component
public class ProcessGoalRestService {

  private final SessionService sessionService;
  private final ProcessGoalService processGoalService;

  @GET
  @Path("/goals")
  @Produces(MediaType.APPLICATION_JSON)
  public List<ProcessGoalDto> getProcessDefinitionsGoals(@Context final ContainerRequestContext requestContext) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return processGoalService.getProcessDefinitionGoals(userId);
  }
}
