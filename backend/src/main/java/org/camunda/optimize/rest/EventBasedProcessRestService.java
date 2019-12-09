/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.EventProcessService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@AllArgsConstructor
@Path("/eventBasedProcess")
@Component
@Secured
public class EventBasedProcessRestService {

  private final EventProcessService eventProcessService;
  private final SessionService sessionService;

  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public EventProcessMappingDto getEventProcessMapping(@PathParam("id") String eventProcessId) {
    return eventProcessService.getEventProcessMapping(eventProcessId);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<EventProcessMappingDto> getAllEventProcessMappingsOmitXml() {
    return eventProcessService.getAllEventProcessMappingsOmitXml();
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdDto createEventProcessMapping(@Context final ContainerRequestContext requestContext,
                                         @Valid final EventProcessMappingDto eventProcessMappingDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    eventProcessMappingDto.setLastModifier(userId);
    return eventProcessService.createEventProcessMapping(eventProcessMappingDto);
  }

  @PUT
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateEventProcessMapping(@PathParam("id") String eventProcessId,
                                        @Context final ContainerRequestContext requestContext,
                                        @Valid final EventProcessMappingDto eventProcessMappingDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    eventProcessMappingDto.setId(eventProcessId);
    eventProcessMappingDto.setLastModifier(userId);
    eventProcessService.updateEventProcessMapping(eventProcessMappingDto);
  }

  @POST
  @Path("/{id}/_publish")
  @Produces(MediaType.APPLICATION_JSON)
  public void publishEventProcessMapping(@PathParam("id") String eventProcessId,
                                         @Context final ContainerRequestContext requestContext) {
    eventProcessService.publishEventProcessMapping(eventProcessId);
  }

  @POST
  @Path("/{id}/_cancelPublish")
  @Produces(MediaType.APPLICATION_JSON)
  public void cancelEventProcessPublish(@PathParam("id") String eventProcessId,
                                        @Context final ContainerRequestContext requestContext) {
    eventProcessService.cancelPublish(eventProcessId);
  }

  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteEventProcess(@PathParam("id") String eventProcessId) {
    final boolean wasFoundAndDeleted = eventProcessService.deleteEventProcessMapping(eventProcessId);

    if (!wasFoundAndDeleted) {
      final String errorMessage = String.format(
        "Could not delete event based process with id [%s]. Event based process does not exist." +
          "Maybe it was already deleted by someone else?",
        eventProcessId
      );
      throw new NotFoundException(errorMessage);
    }
  }

}
