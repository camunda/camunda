/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountServiceDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountSuggestionsRequestDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.events.ExternalEventService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import javax.ws.rs.BeanParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@AllArgsConstructor
@Path("/event")
@Component
@Secured
public class EventRestService {

  private final ExternalEventService eventService;

  @POST
  @Path("/count")
  @Produces(MediaType.APPLICATION_JSON)
  public List<EventCountDto> getEventCounts(@BeanParam EventCountRequestDto eventCountRequestDto,
                                            @Valid @RequestBody EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto) {
    return eventService.getEventCounts(new EventCountServiceDto(eventCountRequestDto, eventCountSuggestionsRequestDto));
  }

}
