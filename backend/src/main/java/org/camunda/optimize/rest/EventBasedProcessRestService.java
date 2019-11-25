/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.event.EventBasedProcessDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.EventBasedProcessService;
import org.springframework.stereotype.Component;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@AllArgsConstructor
@Path("/eventBasedProcess")
@Component
@Secured
public class EventBasedProcessRestService {

  private final EventBasedProcessService eventBasedProcessService;

  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public EventBasedProcessDto getEventBasedProcess(@PathParam("id") String eventBasedProcessId) {
    return eventBasedProcessService.getEventBasedProcess(eventBasedProcessId);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<EventBasedProcessDto> getAllEventBasedProcessesOmitXml() {
    return eventBasedProcessService.getAllEventBasedProcessOmitXml();
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdDto createEventBasedProcess(@Valid EventBasedProcessDto eventBasedProcessDto) {
    return eventBasedProcessService.createEventBasedProcess(eventBasedProcessDto);
  }

  @PUT
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateEventBasedProcess(@PathParam("id") String eventBasedProcessId,
                                      @Valid EventBasedProcessDto eventBasedProcessDto) {
    eventBasedProcessDto.setId(eventBasedProcessId);
    eventBasedProcessService.updateEventBasedProcess(eventBasedProcessDto);
  }

  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteEventBasedProcess(@PathParam("id") String eventBasedProcessId) {
    eventBasedProcessService.deleteEventBasedProcess(eventBasedProcessId);
  }
}
