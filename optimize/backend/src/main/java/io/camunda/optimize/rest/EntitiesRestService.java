/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;

import io.camunda.optimize.dto.optimize.query.entity.EntitiesDeleteRequestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import io.camunda.optimize.dto.optimize.rest.sorting.EntitySorter;
import io.camunda.optimize.rest.mapper.EntityRestMapper;
import io.camunda.optimize.service.entities.EntitiesService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

@Path(EntitiesRestService.ENTITIES_PATH)
@Component
public class EntitiesRestService {

  public static final String ENTITIES_PATH = "/entities";

  private final EntitiesService entitiesService;
  private final SessionService sessionService;
  private final EntityRestMapper entityRestMapper;

  public EntitiesRestService(
      final EntitiesService entitiesService,
      final SessionService sessionService,
      final EntityRestMapper entityRestMapper) {
    this.entitiesService = entitiesService;
    this.sessionService = sessionService;
    this.entityRestMapper = entityRestMapper;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<EntityResponseDto> getEntities(
      @Context final ContainerRequestContext requestContext,
      @BeanParam final EntitySorter entitySorter) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final List<EntityResponseDto> entities = entitiesService.getAllEntities(userId);
    entities.forEach(entityRestMapper::prepareRestResponse);
    return entitySorter.applySort(entities);
  }

  @GET
  @Path("/names")
  @Produces(MediaType.APPLICATION_JSON)
  public EntityNameResponseDto getEntityNames(
      @Context final ContainerRequestContext requestContext,
      @BeanParam final EntityNameRequestDto requestDto) {
    return entitiesService.getEntityNames(
        requestDto, requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE));
  }

  @POST
  @Path("/delete-conflicts")
  @Consumes(MediaType.APPLICATION_JSON)
  public boolean entitiesHaveDeleteConflicts(
      @Context final ContainerRequestContext requestContext,
      @Valid @NotNull @RequestBody final EntitiesDeleteRequestDto entities) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return entitiesService.entitiesHaveConflicts(entities, userId);
  }

  @POST
  @Path("/delete")
  @Consumes(MediaType.APPLICATION_JSON)
  public void bulkDeleteEntities(
      @Context final ContainerRequestContext requestContext,
      @Valid @NotNull @RequestBody final EntitiesDeleteRequestDto entities) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    entitiesService.bulkDeleteEntities(entities, userId);
  }
}
