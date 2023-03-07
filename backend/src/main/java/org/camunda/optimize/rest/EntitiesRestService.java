/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.entity.EntitiesDeleteRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.rest.sorting.EntitySorter;
import org.camunda.optimize.rest.mapper.EntityRestMapper;
import org.camunda.optimize.service.entities.EntitiesService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;

@AllArgsConstructor
@Path(EntitiesRestService.ENTITIES_PATH)
@Component
public class EntitiesRestService {

  public static final String ENTITIES_PATH = "/entities";

  private final EntitiesService entitiesService;
  private final SessionService sessionService;
  private final EntityRestMapper entityRestMapper;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<EntityResponseDto> getEntities(@Context ContainerRequestContext requestContext,
                                             @BeanParam final EntitySorter entitySorter) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    List<EntityResponseDto> entities = entitiesService.getAllEntities(userId);
    entities.forEach(entityRestMapper::prepareRestResponse);
    return entitySorter.applySort(entities);
  }

  @GET
  @Path("/names")
  @Produces(MediaType.APPLICATION_JSON)
  public EntityNameResponseDto getEntityNames(@Context ContainerRequestContext requestContext,
                                              @BeanParam EntityNameRequestDto requestDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return entitiesService.getEntityNames(requestDto, userId, requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE));
  }

  @POST
  @Path("/delete-conflicts")
  @Consumes(MediaType.APPLICATION_JSON)
  public boolean entitiesHaveDeleteConflicts(@Context ContainerRequestContext requestContext,
                                             @Valid @NotNull @RequestBody final EntitiesDeleteRequestDto entities) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return entitiesService.entitiesHaveConflicts(entities, userId);
  }

  @POST
  @Path("/delete")
  @Consumes(MediaType.APPLICATION_JSON)
  public void bulkDeleteEntities(@Context ContainerRequestContext requestContext,
                                 @Valid @NotNull @RequestBody final EntitiesDeleteRequestDto entities) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    entitiesService.bulkDeleteEntities(entities, userId);
  }

}
