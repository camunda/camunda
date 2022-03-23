/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsResponseDto;
import org.camunda.optimize.dto.optimize.rest.sorting.ProcessGoalSorter;
import org.camunda.optimize.service.ProcessGoalsService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@AllArgsConstructor
@Path("/process")
@Component
public class ProcessGoalsRestService {

  private final SessionService sessionService;
  private final ProcessGoalsService processGoalsService;

  @GET
  @Path("/goals")
  @Produces(MediaType.APPLICATION_JSON)
  public List<ProcessGoalsResponseDto> getProcessDefinitionsGoals(@Context final ContainerRequestContext requestContext,
                                                                  @BeanParam final ProcessGoalSorter processGoalSorter) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return processGoalSorter.applySort(processGoalsService.getProcessDefinitionGoals(userId));
  }

  @PUT
  @Path("/{processDefinitionKey}/goals")
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateProcessGoals(@Context final ContainerRequestContext requestContext,
                                 @PathParam("processDefinitionKey") final String processDefKey,
                                 @NotNull @Valid List<ProcessDurationGoalDto> goals) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    processGoalsService.updateProcessGoals(userId, processDefKey, goals);
  }

}
