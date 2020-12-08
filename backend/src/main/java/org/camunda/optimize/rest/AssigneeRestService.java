/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeRequestDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.AssigneeCandidateGroupService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@AllArgsConstructor
@Path("/assignee")
@Component
@Secured
@Slf4j
public class AssigneeRestService {

  private final SessionService sessionService;
  private final AssigneeCandidateGroupService assigneeCandidateGroupService;

  @POST
  @Path("/values")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<String> getAssigneesForProcessDefinition(@Context ContainerRequestContext requestContext,
                                                       AssigneeRequestDto requestDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return assigneeCandidateGroupService.getAllAssigneeIdsForProcess(userId, requestDto);
  }
}
