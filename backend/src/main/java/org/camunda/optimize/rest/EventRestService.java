/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventGroupRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto;
import org.camunda.optimize.dto.optimize.rest.Page;
import org.camunda.optimize.dto.optimize.rest.sorting.EventCountSorter;
import org.camunda.optimize.service.events.EventCountService;
import org.camunda.optimize.service.events.ExternalEventService;
import org.camunda.optimize.service.exceptions.EventProcessManagementForbiddenException;
import org.camunda.optimize.service.security.EventProcessAuthorizationService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@AllArgsConstructor
@Path("/event")
@Component
@Slf4j
public class EventRestService {

  private final EventCountService eventCountService;
  private final ExternalEventService externalEventService;
  private final SessionService sessionService;
  private final EventProcessAuthorizationService eventProcessAuthorizationService;

  @POST
  @Path("/count")
  @Produces(MediaType.APPLICATION_JSON)
  public List<EventCountResponseDto> getEventCounts(@Context final ContainerRequestContext requestContext,
                                                    @Valid @RequestBody final EventCountRequestDto eventCountRequestDto,
                                                    @BeanParam final EventCountSorter eventCountSorter,
                                                    @QueryParam("searchTerm") final String searchTerm) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final List<EventCountResponseDto> eventCounts = eventCountService.getEventCounts(
      userId, searchTerm, eventCountRequestDto
    );
    return eventCountSorter.applySort(eventCounts);
  }

  @GET
  @Path("/groups")
  @Produces(MediaType.APPLICATION_JSON)
  public List<String> getExternalEventGroups(@Context final ContainerRequestContext requestContext,
                                             @BeanParam @Valid final EventGroupRequestDto groupRequestDto) {
    validateEventProcessManagementAuthorization(requestContext);
    groupRequestDto.validateRequest();
    return externalEventService.getEventGroups(groupRequestDto);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Page<DeletableEventDto> getEvents(@Context final ContainerRequestContext requestContext,
                                           @BeanParam @Valid final EventSearchRequestDto eventSearchRequestDto) {
    validateEventProcessManagementAuthorization(requestContext);
    eventSearchRequestDto.validateRequest();
    return externalEventService.getEventsForRequest(eventSearchRequestDto);
  }

  @POST
  @Path("/delete")
  @Consumes(MediaType.APPLICATION_JSON)
  public void deleteEvents(@Context final ContainerRequestContext requestContext,
                           @RequestBody @Valid @NotNull @Size(min = 1, max = 1000) List<String> eventIdsToDelete) {
    validateEventProcessManagementAuthorization(requestContext);
    externalEventService.deleteEvents(eventIdsToDelete);
  }

  private void validateEventProcessManagementAuthorization(@Context final ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    if (!eventProcessAuthorizationService.hasEventProcessManagementAccess(userId)) {
      throw new EventProcessManagementForbiddenException(userId);
    }
  }

}
