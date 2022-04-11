/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.event.EventGroupRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.EventBasedProcessConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.naturalOrder;
import static org.assertj.core.api.Assertions.assertThat;

public class EventGroupsRestServiceIT extends AbstractEventRestServiceIT {

  @Test
  public void getEventGroups_noAuthentication() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .getEventGroupsRequest(new EventGroupRequestDto(null, 10))
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getEventCounts_noAuthorization() {
    // given
    final EventBasedProcessConfiguration eventProcessConfiguration =
      embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration();
    eventProcessConfiguration.getAuthorizedUserIds().clear();
    eventProcessConfiguration.getAuthorizedGroupIds().clear();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .getEventGroupsRequest(new EventGroupRequestDto(null, 10))
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private static Stream<String> emptySearchTerms() {
    return Stream.of("", null);
  }

  @ParameterizedTest
  @MethodSource("emptySearchTerms")
  public void getEventGroups(final String searchTerm) {
    // given
    final EventGroupRequestDto groupRequest = new EventGroupRequestDto(searchTerm, 10);

    // when
    final List<String> groups = requestExternalEventGroups(groupRequest);

    // then the groups exist only once in list and are sorted as expected
    assertThat(groups)
      .containsExactly(
        getGroupForEvent(nullGroupEvent), // null comes first
        getGroupForEvent(backendMayoEvent), // this backend group comes first as it is upper case
        getGroupForEvent(backendKetchupEvent),
        getGroupForEvent(frontendMayoEvent),
        getGroupForEvent(ketchupMayoEvent),
        getGroupForEvent(managementBbqEvent)
      ).isSortedAccordingTo(Comparator.nullsFirst(naturalOrder()));
  }

  @Test
  public void getEventGroups_excludesGroupsThatHaveBeenDeleted() {
    // given
    final String group = "management";
    deleteAllStoredEventsOfGroup(group);
    final EventGroupRequestDto groupRequest = new EventGroupRequestDto(null, 10);

    // when
    final List<String> groups = requestExternalEventGroups(groupRequest);

    // then the groups list does not include the deleted group
    assertThat(groups)
      .containsExactly(
        getGroupForEvent(nullGroupEvent),
        getGroupForEvent(backendMayoEvent),
        getGroupForEvent(backendKetchupEvent),
        getGroupForEvent(frontendMayoEvent),
        getGroupForEvent(ketchupMayoEvent)
      ).isSortedAccordingTo(Comparator.nullsFirst(naturalOrder()));
  }

  @Test
  public void getEventGroups_nullNotIncludedIfAllEventHaveGroups() {
    // given
    deleteAllStoredEventsOfGroup(null);
    final EventGroupRequestDto groupRequest = new EventGroupRequestDto(null, 10);

    // when
    final List<String> groups = requestExternalEventGroups(groupRequest);

    // then the groups list does not include the null entry
    assertThat(groups)
      .containsExactly(
        getGroupForEvent(backendMayoEvent),
        getGroupForEvent(backendKetchupEvent),
        getGroupForEvent(frontendMayoEvent),
        getGroupForEvent(ketchupMayoEvent),
        getGroupForEvent(managementBbqEvent)
      ).isSortedAccordingTo(Comparator.nullsFirst(naturalOrder()));
  }

  @Test
  public void getEventGroups_usingSearchTerm() {
    // given
    final EventGroupRequestDto groupRequest = new EventGroupRequestDto("backend", 10);

    // when
    final List<String> groups = requestExternalEventGroups(groupRequest);

    // then the groups exist only once in list and are sorted as expected
    assertThat(groups)
      .containsExactly(
        getGroupForEvent(backendMayoEvent), // this event matched case insensitively
        getGroupForEvent(backendKetchupEvent)
      ).isSortedAccordingTo(Comparator.nullsFirst(naturalOrder()));
  }

  @Test
  public void getEventGroups_usingSearchTermLongerThanMaxNGramMatchesOnlyOnExactPrefix() {
    // given
    final String longGroupName = "longGroupName";
    final EventDto longGroupNameEvent = ingestTestEventForGroup(longGroupName);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    EventGroupRequestDto groupRequest = new EventGroupRequestDto(longGroupName.substring(0, 11), 10);

    // when
    List<String> groups = requestExternalEventGroups(groupRequest);

    // then the group is returned as the exact prefix matches
    assertThat(groups).containsExactly(longGroupNameEvent.getGroup());

    // when
    groupRequest = new EventGroupRequestDto(longGroupName.substring(0, 11).toLowerCase(), 10);
    groups = requestExternalEventGroups(groupRequest);

    // then the group is not returned as the search term is lower case and doesn't match the prefix exactly
    assertThat(groups).isEmpty();

    // when
    groupRequest = new EventGroupRequestDto(longGroupName.substring(1, 12), 10);
    groups = requestExternalEventGroups(groupRequest);

    // then the group is not returned as the search term doesn't match the prefix exactly
    assertThat(groups).isEmpty();
  }

  @Test
  public void getEventGroups_pageLimitIsAppliedWhenMoreResultsExist() {
    // given
    final EventGroupRequestDto requestDto = new EventGroupRequestDto(null, 3);

    // when
    List<String> groups = requestExternalEventGroups(requestDto);

    // then the first page of groups is returned
    assertThat(groups)
      .containsExactly(
        getGroupForEvent(nullGroupEvent),
        getGroupForEvent(backendMayoEvent),
        getGroupForEvent(backendKetchupEvent)
      ).isSortedAccordingTo(Comparator.nullsFirst(naturalOrder()));
  }

  private List<String> requestExternalEventGroups(final EventGroupRequestDto groupRequest) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .getEventGroupsRequest(groupRequest)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());
  }

  private String getGroupForEvent(final CloudEventRequestDto eventDto) {
    return eventDto.getGroup().orElse(null);
  }

  private EventDto ingestTestEventForGroup(final String groupName) {
    final EventDto eventToIngest = EventDto.builder()
      .id(IdGenerator.getNextId())
      .eventName(IdGenerator.getNextId())
      .timestamp(OffsetDateTime.now().toInstant().toEpochMilli())
      .traceId(IdGenerator.getNextId())
      .group(groupName)
      .source(EXTERNAL_EVENT_SOURCE)
      .data(ImmutableMap.of(VARIABLE_ID, VARIABLE_VALUE))
      .build();
    embeddedOptimizeExtension.getEventService().saveEventBatch(Collections.singletonList(eventToIngest));
    return eventToIngest;
  }

  private void deleteAllStoredEventsOfGroup(final String group) {
    final List<String> eventIdsToDelete = getAllStoredEvents().stream()
      .filter(event -> StringUtils.equals(group, event.getGroup()))
      .map(EventDto::getId)
      .collect(Collectors.toList());
    embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

}
