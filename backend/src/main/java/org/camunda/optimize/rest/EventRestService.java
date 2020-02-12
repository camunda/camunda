/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.events.EventCountService;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import javax.ws.rs.BeanParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

@AllArgsConstructor
@Path("/event")
@Component
@Secured
public class EventRestService {
  private static final Comparator<EventCountDto> SUGGESTED_COMPARATOR =
    Comparator.comparing(EventCountDto::isSuggested, nullsFirst(naturalOrder())).reversed();
  private static final Comparator<EventCountDto> DEFAULT_COMPARATOR = nullsFirst(
    Comparator.comparing(EventCountDto::getGroup, nullsFirst(String.CASE_INSENSITIVE_ORDER))
      .thenComparing(EventCountDto::getSource, nullsFirst(String.CASE_INSENSITIVE_ORDER))
      .thenComparing(EventCountDto::getEventName, nullsFirst(String.CASE_INSENSITIVE_ORDER)));

  private final EventCountService eventCountService;
  private final SessionService sessionService;

  @POST
  @Path("/count")
  @Produces(MediaType.APPLICATION_JSON)
  public List<EventCountDto> getEventCounts(@Context final ContainerRequestContext requestContext,
                                            @BeanParam final EventCountSearchRequestDto eventCountSearchRequestDto,
                                            @Valid @RequestBody final EventCountRequestDto eventCountRequestDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final List<EventCountDto> eventCounts = eventCountService.getEventCounts(
      userId, eventCountSearchRequestDto.getSearchTerm(), eventCountRequestDto
    );
    return sortEventCountsUsingWithRequestParameters(eventCountSearchRequestDto, eventCounts);
  }

  private List<EventCountDto> sortEventCountsUsingWithRequestParameters(final EventCountSearchRequestDto eventCountRequestDto,
                                                                        final List<EventCountDto> eventCountDtos) {
    SortOrder sortOrder = eventCountRequestDto.getSortOrder();
    boolean isAscending = sortOrder == null || sortOrder.equals(SortOrder.ASC);
    Comparator<EventCountDto> secondaryComparator = Optional.ofNullable(eventCountRequestDto.getOrderBy())
      .map(orderBy -> sortOrderedComparator(isAscending, getCustomComparator(orderBy))
        .thenComparing(sortOrderedComparator(isAscending, DEFAULT_COMPARATOR)))
      .orElseGet(() -> sortOrderedComparator(isAscending, DEFAULT_COMPARATOR));
    eventCountDtos.sort(SUGGESTED_COMPARATOR.thenComparing(secondaryComparator));
    return eventCountDtos;
  }

  private Comparator<EventCountDto> sortOrderedComparator(final boolean isAscending,
                                                          final Comparator<EventCountDto> comparator) {
    return isAscending ? comparator : comparator.reversed();
  }

  private Comparator<EventCountDto> getCustomComparator(final String orderBy) {
    if (orderBy.equalsIgnoreCase(EventCountDto.Fields.group)) {
      return Comparator.comparing(EventCountDto::getGroup, nullsFirst(String.CASE_INSENSITIVE_ORDER));
    } else if (orderBy.equalsIgnoreCase(EventCountDto.Fields.source)) {
      return Comparator.comparing(EventCountDto::getSource, nullsFirst(String.CASE_INSENSITIVE_ORDER));
    } else if (orderBy.equalsIgnoreCase(EventCountDto.Fields.eventName)) {
      return Comparator.comparing(EventCountDto::getEventName, nullsFirst(String.CASE_INSENSITIVE_ORDER));
    } else {
      throw new OptimizeValidationException("invalid orderBy field");
    }
  }

}
