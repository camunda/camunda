/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess;

import org.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.dto.optimize.rest.Page;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.sorting.SortRequestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.naturalOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.sorting.SortOrder.ASC;
import static org.camunda.optimize.dto.optimize.query.sorting.SortOrder.DESC;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class EventListRestServiceIT extends AbstractEventRestServiceIT {

  private static final String GROUP = DeletableEventDto.Fields.group;
  private static final String SOURCE = DeletableEventDto.Fields.source;
  private static final String EVENT_NAME = DeletableEventDto.Fields.eventName;
  private static final String TIMESTAMP = DeletableEventDto.Fields.timestamp;
  private static final String TRACE_ID = DeletableEventDto.Fields.traceId;

  @ParameterizedTest
  @MethodSource("validSortCriteria")
  public void getEventCounts_userAuthorizedAsUserWithSortCriteria(String sortField, SortOrder sortOrder) {
    // given
    EventSearchRequestDto requestDto = eventRequestDto(sortField, sortOrder, 0, 20);

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(requestDto)
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventsPage.getSortBy()).isEqualTo(sortField);
    assertThat(eventsPage.getSortOrder()).isEqualTo(sortOrder);
    assertThat(eventsPage.getTotal()).isEqualTo(allEventDtos.size());
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(getExpectedSortComparator(sortField, sortOrder))
      .hasSize(allEventDtos.size());
  }

  @ParameterizedTest
  @MethodSource("validSortCriteria")
  public void getEventCounts_userAuthorizedAsPartOfGroupWithSortCriteria(String sortField, SortOrder sortOrder) {
    // given
    removeAllUserEventProcessAuthorizations();
    final String authorizedGroup = "jedis";
    authorizationClient.createGroupAndAddUser(authorizedGroup, DEFAULT_USERNAME);
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessConfiguration().setAuthorizedGroupIds(Collections.singletonList(authorizedGroup));
    EventSearchRequestDto requestDto = eventRequestDto(sortField, sortOrder, 0, 20);

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(requestDto)
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventsPage.getSortBy()).isEqualTo(sortField);
    assertThat(eventsPage.getSortOrder()).isEqualTo(sortOrder);
    assertThat(eventsPage.getTotal()).isEqualTo(allEventDtos.size());
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(getExpectedSortComparator(sortField, sortOrder))
      .hasSize(allEventDtos.size());
  }

  @Test
  public void getEventCounts_userNotAuthorized() {
    // given
    removeAllUserEventProcessAuthorizations();
    EventSearchRequestDto requestDto = eventRequestDto(GROUP, ASC, 0, 20);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(requestDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getEventCounts_noAuthentication() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(eventRequestDto(GROUP, DESC, 0, 20))
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getEventCounts_defaultSortParamsAppliedIfMissing() {
    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(eventRequestDto(null, null, 0, 20))
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventsPage.getSortBy()).isEqualTo(TIMESTAMP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isEqualTo(allEventDtos.size());
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(getExpectedSortComparator(TIMESTAMP, DESC))
      .hasSize(allEventDtos.size());
  }

  @Test
  public void getEventCounts_defaultPaginationParamsAppliedIfMissing() {
    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(eventRequestDto(GROUP, DESC, null, null))
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventsPage.getLimit()).isEqualTo(EventSearchRequestDto.DEFAULT_LIMIT);
    assertThat(eventsPage.getOffset()).isEqualTo(EventSearchRequestDto.DEFAULT_OFFSET);
    assertThat(eventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isEqualTo(allEventDtos.size());
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(getExpectedSortComparator(GROUP, DESC))
      .hasSize(allEventDtos.size());
  }

  @Test
  public void getEventCounts_eventsSortedByDescendingTimestampIfCannotBeResolvedBySortParam() {
    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(eventRequestDto(GROUP, ASC, 0, 20))
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(ASC);
    assertThat(eventsPage.getTotal()).isEqualTo(allEventDtos.size());
    assertThat(eventsPage.getResults()).isSortedAccordingTo(
      getExpectedSortComparator(GROUP, ASC).thenComparing(getExpectedSortComparator(TIMESTAMP, DESC)))
      .hasSize(allEventDtos.size());
  }

  @Test
  public void getEventCounts_sortParamNotRecognised() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(eventRequestDto("someInvalidField", DESC, 0, 20))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getEventCounts_sortByNotSupplied() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(eventRequestDto(null, DESC, 0, 20))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getEventCounts_sortOrderNotSupplied() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(eventRequestDto(GROUP, null, 0, 20))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getEventCounts_moreEventsExistThanSpecifiedPageSize() {
    // when we get the first page of results
    final Page<DeletableEventDto> firstEventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(eventRequestDto(GROUP, DESC, 0, 10))
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(firstEventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(firstEventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(firstEventsPage.getTotal()).isEqualTo(allEventDtos.size());
    final List<DeletableEventDto> firstPageOfResults = firstEventsPage.getResults();
    assertThat(firstPageOfResults)
      .isSortedAccordingTo(getExpectedSortComparator(GROUP, DESC))
      .hasSize(10);

    // when we get the second page of results
    final Page<DeletableEventDto> secondEventPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(eventRequestDto(GROUP, DESC, 10, 10))
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then we get the remaining results
    assertThat(secondEventPage.getSortBy()).isEqualTo(GROUP);
    assertThat(secondEventPage.getSortOrder()).isEqualTo(DESC);
    assertThat(secondEventPage.getTotal()).isEqualTo(allEventDtos.size());
    final List<DeletableEventDto> secondPageOfResults = secondEventPage.getResults();
    assertThat(secondPageOfResults)
      .isSortedAccordingTo(getExpectedSortComparator(GROUP, DESC))
      .hasSize(4);
    // and all the events have been returned exactly once
    final List<String> allReturnedEventIds =
      Stream.concat(firstPageOfResults.stream(), secondPageOfResults.stream())
        .map(DeletableEventDto::getId)
        .collect(Collectors.toList());
    assertThat(allReturnedEventIds)
      .containsAll(allEventDtos.stream().map(CloudEventRequestDto::getId).collect(Collectors.toList()));
  }

  @Test
  public void getEventCounts_pageSizeLargerThanAllowed() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(eventRequestDto(GROUP, null, 0, ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT + 1))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getEventCounts_pageSizeNegativeValue() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(eventRequestDto(GROUP, null, 0, -1))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getEventCounts_offsetNegativeValue() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(eventRequestDto(GROUP, null, -1, 0))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getEventCounts_usingEmptySearchTerm() {
    // given
    EventSearchRequestDto requestDto = eventRequestDto("", GROUP, DESC, 0, 20);

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(requestDto)
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then all events are returned
    assertThat(eventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isEqualTo(allEventDtos.size());
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(getExpectedSortComparator(GROUP, DESC))
      .hasSize(allEventDtos.size())
      .extracting(DeletableEventDto.Fields.id)
      .containsExactlyInAnyOrderElementsOf(
        allEventDtos.stream().map(CloudEventRequestDto::getId).collect(Collectors.toList()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void getEventCounts_usingSearchTermMatchingTraceIdExactly(boolean caseInsensitive) {
    // given
    final String searchTerm = eventTraceOne.get(0).getTraceid();
    EventSearchRequestDto requestDto = eventRequestDto(
      caseInsensitive ? searchTerm.toUpperCase() : searchTerm,
      GROUP, DESC, 0, 20
    );

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(requestDto)
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then only events of the matching trace are returned
    assertThat(eventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isEqualTo(eventTraceOne.size());
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(getExpectedSortComparator(GROUP, DESC))
      .hasSize(eventTraceOne.size())
      .extracting(DeletableEventDto.Fields.id)
      .containsExactlyInAnyOrderElementsOf(
        eventTraceOne.stream().map(CloudEventRequestDto::getId).collect(Collectors.toList()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void getEventCounts_usingSearchTermMatchingGroupExactly(boolean caseInsensitive) {
    // given
    final String searchTerm = backendKetchupEvent.getGroup().get();
    EventSearchRequestDto requestDto = eventRequestDto(
      caseInsensitive ? searchTerm.toUpperCase() : searchTerm,
      GROUP, DESC, 0, 20
    );

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(requestDto)
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then only events which match the search term are returned
    final List<CloudEventRequestDto> expectedMatches = allEventDtos.stream()
      .filter(event -> event.getGroup().isPresent())
      .filter(event -> event.getGroup().get().equalsIgnoreCase(backendKetchupEvent.getGroup().get()))
      .collect(Collectors.toList());
    assertThat(eventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isEqualTo(expectedMatches.size());
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(getExpectedSortComparator(GROUP, DESC))
      .hasSize(expectedMatches.size())
      .extracting(DeletableEventDto.Fields.id)
      .containsExactlyInAnyOrderElementsOf(
        expectedMatches.stream().map(CloudEventRequestDto::getId).collect(Collectors.toList()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void getEventCounts_usingSearchTermMatchingSourceExactly(boolean caseInsensitive) {
    // given
    final String searchTerm = backendMayoEvent.getSource();
    EventSearchRequestDto requestDto = eventRequestDto(
      caseInsensitive ? searchTerm.toUpperCase() : searchTerm,
      GROUP, DESC, 0, 20
    );

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(requestDto)
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then only events which match the search term are returned
    final List<CloudEventRequestDto> expectedMatches = allEventDtos.stream()
      .filter(event -> event.getSource().equalsIgnoreCase(backendMayoEvent.getSource()))
      .collect(Collectors.toList());
    assertThat(eventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isEqualTo(expectedMatches.size());
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(getExpectedSortComparator(GROUP, DESC))
      .hasSize(expectedMatches.size())
      .extracting(DeletableEventDto.Fields.id)
      .containsExactlyInAnyOrderElementsOf(
        expectedMatches.stream().map(CloudEventRequestDto::getId).collect(Collectors.toList()));
  }

  @Test
  public void getEventCounts_usingSearchTermMatchingEventNameExactly() {
    // given
    EventSearchRequestDto requestDto = eventRequestDto(ketchupMayoEvent.getType(), GROUP, DESC, 0, 20);

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(requestDto)
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then only events which match the search term are returned
    final List<CloudEventRequestDto> expectedMatches = allEventDtos.stream()
      .filter(event -> event.getType().equalsIgnoreCase(ketchupMayoEvent.getType()))
      .collect(Collectors.toList());
    assertThat(eventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isEqualTo(expectedMatches.size());
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(getExpectedSortComparator(GROUP, DESC))
      .hasSize(expectedMatches.size())
      .extracting(DeletableEventDto.Fields.id)
      .containsExactlyInAnyOrderElementsOf(
        expectedMatches.stream().map(CloudEventRequestDto::getId).collect(Collectors.toList()));
  }

  // It cannot match if either the search term does not match a result, or is a long term and doesn't match any prefix
  @ParameterizedTest
  @ValueSource(strings = {"no matches", "no matches and this is long", "cklisted_event"})
  public void getEventCounts_nonMatchingScenarios(final String searchTerm) {
    // given
    EventSearchRequestDto requestDto = eventRequestDto(searchTerm, GROUP, DESC, 0, 20);

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(requestDto)
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isEqualTo(0L);
    assertThat(eventsPage.getResults()).isEmpty();
  }

  @Test
  public void getEventCounts_usingSearchTermWithLongTermMatchesPrefix() {
    // given a search term that is longer than the ngram threshold
    final String searchTerm = "blacklisted_e";
    EventSearchRequestDto requestDto = eventRequestDto(searchTerm, GROUP, DESC, 0, 20);

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(requestDto)
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then only events which match the search term are returned
    final List<CloudEventRequestDto> expectedMatches = allEventDtos.stream()
      .filter(event -> event.getType().equalsIgnoreCase(ketchupMayoEvent.getType()))
      .collect(Collectors.toList());
    assertThat(eventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isEqualTo(expectedMatches.size());
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(getExpectedSortComparator(GROUP, DESC))
      .hasSize(expectedMatches.size())
      .extracting(DeletableEventDto.Fields.id)
      .containsExactlyInAnyOrderElementsOf(
        expectedMatches.stream().map(CloudEventRequestDto::getId).collect(Collectors.toList()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void getEventCounts_usingSearchTermWhichMatchesMultipleFieldsOfDifferentEvents(boolean caseInsensitive) {
    // given
    final String searchTerm = "ketchup";
    EventSearchRequestDto requestDto = eventRequestDto(
      caseInsensitive ? searchTerm.toUpperCase() : searchTerm,
      GROUP, DESC, 0, 20
    );

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(requestDto)
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then events matching across all fields are returned
    final List<CloudEventRequestDto> expectedMatches = allEventDtos.stream()
      .filter(event -> (event.getGroup().isPresent() && event.getGroup().get().toLowerCase().contains(searchTerm)) ||
        event.getSource().toLowerCase().contains(searchTerm) ||
        event.getType().toLowerCase().contains(searchTerm) ||
        event.getTraceid().toLowerCase().contains(searchTerm))
      .collect(Collectors.toList());
    assertThat(eventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isEqualTo(expectedMatches.size());
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(getExpectedSortComparator(GROUP, DESC))
      .hasSize(expectedMatches.size())
      .extracting(DeletableEventDto.Fields.id)
      .containsExactlyInAnyOrderElementsOf(
        expectedMatches.stream().map(CloudEventRequestDto::getId).collect(Collectors.toList()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void getEventCounts_usingSearchTermWhichMatchesMultipleFieldsOfDifferentEventsAndPagination(boolean caseInsensitive) {
    // given
    final String searchTerm = "ketchup";
    EventSearchRequestDto requestDto = eventRequestDto(
      caseInsensitive ? searchTerm.toUpperCase() : searchTerm,
      GROUP, DESC, 0, 5
    );

    // when
    final Page<DeletableEventDto> eventsPage = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventListRequest(requestDto)
      .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode());

    // then events matching across all fields are returned
    final List<CloudEventRequestDto> expectedMatches = allEventDtos.stream()
      .filter(event -> (event.getGroup().isPresent() && event.getGroup().get().toLowerCase().contains(searchTerm)) ||
        event.getSource().toLowerCase().contains(searchTerm) ||
        event.getType().toLowerCase().contains(searchTerm) ||
        event.getTraceid().toLowerCase().contains(searchTerm))
      .collect(Collectors.toList());
    assertThat(eventsPage.getSortBy()).isEqualTo(GROUP);
    assertThat(eventsPage.getSortOrder()).isEqualTo(DESC);
    assertThat(eventsPage.getTotal()).isEqualTo(expectedMatches.size());
    assertThat(eventsPage.getResults())
      .isSortedAccordingTo(getExpectedSortComparator(GROUP, DESC))
      .hasSize(5);
  }

  private Comparator<DeletableEventDto> getExpectedSortComparator(final String sortField, final SortOrder sortOrder) {
    Comparator<DeletableEventDto> comparator;
    if (sortField.equalsIgnoreCase(DeletableEventDto.Fields.group)) {
      comparator = Comparator.comparing(DeletableEventDto::getGroup, Comparator.nullsFirst(naturalOrder()));
    } else if (sortField.equalsIgnoreCase(DeletableEventDto.Fields.source)) {
      comparator = Comparator.comparing(DeletableEventDto::getSource, Comparator.nullsFirst(naturalOrder()));
    } else if (sortField.equalsIgnoreCase(DeletableEventDto.Fields.eventName)) {
      comparator = Comparator.comparing(DeletableEventDto::getEventName, Comparator.nullsFirst(naturalOrder()));
    } else if (sortField.equalsIgnoreCase(DeletableEventDto.Fields.timestamp)) {
      comparator = Comparator.comparing(DeletableEventDto::getTimestamp, Comparator.nullsFirst(naturalOrder()));
    } else if (sortField.equalsIgnoreCase(DeletableEventDto.Fields.traceId)) {
      comparator = Comparator.comparing(DeletableEventDto::getTraceId, Comparator.nullsFirst(naturalOrder()));
    } else {
      throw new OptimizeIntegrationTestException("Unsupported sort field");
    }
    return DESC.equals(sortOrder) ? comparator.reversed() : comparator;
  }

  private static Stream<Arguments> validSortCriteria() {
    return Stream.of(
      Arguments.of(GROUP, DESC),
      Arguments.of(GROUP, ASC),
      Arguments.of(SOURCE, DESC),
      Arguments.of(SOURCE, ASC),
      Arguments.of(EVENT_NAME, DESC),
      Arguments.of(EVENT_NAME, ASC),
      Arguments.of(TIMESTAMP, DESC),
      Arguments.of(TIMESTAMP, ASC),
      Arguments.of(TRACE_ID, DESC),
      Arguments.of(TRACE_ID, ASC)
    );
  }

  private static EventSearchRequestDto eventRequestDto(final String searchTerm, final String sortBy,
                                                       final SortOrder sortOrder, final Integer offset,
                                                       final Integer limit) {
    return new EventSearchRequestDto(
      searchTerm,
      new SortRequestDto(sortBy, sortOrder),
      new PaginationRequestDto(limit, offset)
    );
  }

  private static EventSearchRequestDto eventRequestDto(final String sortBy, final SortOrder sortOrder,
                                                       final Integer offset, final Integer limit) {
    return eventRequestDto(null, sortBy, sortOrder, offset, limit);
  }

}
