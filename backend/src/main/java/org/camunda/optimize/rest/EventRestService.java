/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.EventCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountRequestDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.EventService;
import org.springframework.stereotype.Component;

import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@AllArgsConstructor
@Path("/event")
@Component
@Secured
public class EventRestService {

  private final EventService eventService;

  @GET
  @Path("/count")
  @Produces(MediaType.APPLICATION_JSON)
  public List<EventCountDto> getEventCounts(@BeanParam EventCountRequestDto eventCountRequestDto) {
    return eventService.getEventCounts(eventCountRequestDto);
  }

}
