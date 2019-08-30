/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.EntitiesService;
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
@Secured
@Path("/entities")
@Component
public class EntitiesRestService {

  private final EntitiesService entitiesService;
  private final SessionService sessionService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<EntityDto> getEntities(@Context ContainerRequestContext requestContext) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return entitiesService.getAllEntities(userId);
  }
}
