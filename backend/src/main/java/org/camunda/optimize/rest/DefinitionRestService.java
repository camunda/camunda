/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@AllArgsConstructor
@Slf4j
@Secured
@Path("/definition")
@Component
public class DefinitionRestService {

  private final SessionService sessionService;
  private final DefinitionService definitionService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/")
  public List<DefinitionWithTenantsDto> getDefinitions(@Context ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return definitionService.getDefinitions(userId);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/_groupByTenant")
  public List<TenantWithDefinitionsDto> getDefinitionsGroupedByTenant(@Context ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return definitionService.getDefinitionsGroupedByTenant(userId);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}/{key}")
  public DefinitionWithTenantsDto getDefinition(@Context ContainerRequestContext requestContext,
                                                @PathParam("type") DefinitionType type,
                                                @PathParam("key") String key) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return definitionService.getDefinition(type, key, userId)
      .orElseThrow(() -> {
        final String reason = String.format("Was not able to find definition for type [%s] and key [%s].", type, key);
        log.error(reason);
        return new NotFoundException(reason);
      });
  }

}
