/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import io.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import io.camunda.optimize.dto.optimize.query.event.EventGroupRequestDto;
import io.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventCountRequestDto;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto;
import io.camunda.optimize.dto.optimize.rest.Page;
import io.camunda.optimize.dto.optimize.rest.sorting.EventCountSorter;
import io.camunda.optimize.service.events.EventCountService;
import io.camunda.optimize.service.events.ExternalEventService;
import io.camunda.optimize.service.exceptions.EventProcessManagementForbiddenException;
import io.camunda.optimize.service.security.EventProcessAuthorizationService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

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
  public List<EventCountResponseDto> getEventCounts(
      @Context final ContainerRequestContext requestContext,
      @Valid @RequestBody final EventCountRequestDto eventCountRequestDto,
      @BeanParam final EventCountSorter eventCountSorter,
      @QueryParam("searchTerm") final String searchTerm) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final List<EventCountResponseDto> eventCounts =
        eventCountService.getEventCounts(userId, searchTerm, eventCountRequestDto);
    return eventCountSorter.applySort(eventCounts);
  }

  @GET
  @Path("/groups")
  @Produces(MediaType.APPLICATION_JSON)
  public List<String> getExternalEventGroups(
      @Context final ContainerRequestContext requestContext,
      @BeanParam @Valid final EventGroupRequestDto groupRequestDto) {
    validateEventProcessManagementAuthorization(requestContext);
    groupRequestDto.validateRequest();
    return externalEventService.getEventGroups(groupRequestDto);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Page<DeletableEventDto> getEvents(
      @Context final ContainerRequestContext requestContext,
      @BeanParam @Valid final EventSearchRequestDto eventSearchRequestDto) {
    validateEventProcessManagementAuthorization(requestContext);
    eventSearchRequestDto.validateRequest();
    return externalEventService.getEventsForRequest(eventSearchRequestDto);
  }

  @POST
  @Path("/delete")
  @Consumes(MediaType.APPLICATION_JSON)
  public void deleteEvents(
      @Context final ContainerRequestContext requestContext,
      @RequestBody @Valid @NotNull @Size(min = 1, max = 1000) List<String> eventIdsToDelete) {
    validateEventProcessManagementAuthorization(requestContext);
    externalEventService.deleteEvents(eventIdsToDelete);
  }

  private void validateEventProcessManagementAuthorization(
      @Context final ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    if (!eventProcessAuthorizationService.hasEventProcessManagementAccess(userId)) {
      throw new EventProcessManagementForbiddenException(userId);
    }
  }
}
