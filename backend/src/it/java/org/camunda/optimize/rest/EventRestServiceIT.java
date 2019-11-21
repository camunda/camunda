/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.EventCountDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

public class EventRestServiceIT extends AbstractIT {

  public static final Random RANDOM = new Random();

  @BeforeEach
  public void init() {
    ingestEvents(createEventsForIngestion());
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  @Test
  public void getAllEventCounts() {
    // when
    List<EventCountDto> eventCountDtos = createGetEventCountsQueryWithRequestDto(new EventCountRequestDto())
      .executeAndReturnList(EventCountDto.class, 200);

    // then all events are sorted using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(5)
      .extracting("group", "source", "eventName", "count")
      .containsExactly(
        tuple("backend", "ketchup", "signup-event", 4L),
        tuple("BACKEND", "mayonnaise", "ketchupevent", 2L),
        tuple("frontend", "mayonnaise", "registered_event", 2L),
        tuple("ketchup", "mayonnaise", "blacklisted_event", 2L),
        tuple("management", "BBQ_sauce", "onboarded_event", 1L)
      );
  }

  @Test
  public void getAllEventCountsWithoutAuthentication() {
    // when
    Response response = createGetEventCountsQueryWithRequestDto(new EventCountRequestDto())
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void getAllEventCountsWithSearchTerm() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder().searchTerm("etch").build();

    // when
    List<EventCountDto> eventCountDtos = createGetEventCountsQueryWithRequestDto(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then matching event counts are return using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .extracting("group", "source", "eventName", "count")
      .containsExactly(
        tuple("backend", "ketchup", "signup-event", 4L),
        tuple("BACKEND", "mayonnaise", "ketchupevent", 2L),
        tuple("ketchup", "mayonnaise", "blacklisted_event", 2L)
      );
  }

  @ParameterizedTest(name = "exact or prefix match are returned with search term {0}")
  @ValueSource(strings = { "registered_ev", "registered_event", "regISTERED_event" })
  public void getAllEventCountsWithSearchTermLongerThanNGramMax(String searchTerm) {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder().searchTerm(searchTerm).build();

    // when
    List<EventCountDto> eventCountDtos = createGetEventCountsQueryWithRequestDto(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then matching event counts are return using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(1)
      .extracting("group", "source", "eventName", "count")
      .containsExactly(
        tuple("frontend", "mayonnaise", "registered_event", 2L)
      );
  }

  @Test
  public void getAllEventCountsWithSortAndOrderParameters() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .orderBy("source")
      .sortOrder(SortOrder.DESC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createGetEventCountsQueryWithRequestDto(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then all matching event counts are return using sort and ordering provided
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(5)
      .extracting("group", "source", "eventName", "count")
      .containsExactly(
        tuple("ketchup", "mayonnaise", "blacklisted_event", 2L),
        tuple("frontend", "mayonnaise", "registered_event", 2L),
        tuple("BACKEND", "mayonnaise", "ketchupevent", 2L),
        tuple("backend", "ketchup", "signup-event", 4L),
        tuple("management", "BBQ_sauce", "onboarded_event", 1L)
      );
  }

  @Test
  public void getAllEventCountsWithSpecifiedParametersMatchingDefault() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .orderBy("group")
      .sortOrder(SortOrder.ASC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createGetEventCountsQueryWithRequestDto(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then all matching event counts are return using sort and ordering provided
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(5)
      .extracting("group", "source", "eventName", "count")
      .containsExactly(
        tuple("backend", "ketchup", "signup-event", 4L),
        tuple("BACKEND", "mayonnaise", "ketchupevent", 2L),
        tuple("frontend", "mayonnaise", "registered_event", 2L),
        tuple("ketchup", "mayonnaise", "blacklisted_event", 2L),
        tuple("management", "BBQ_sauce", "onboarded_event", 1L)
      );
  }

  @Test
  public void getAllEventCountsWithInvalidOrderByParameter() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .orderBy("notAField")
      .build();

    // when
    Response response = createGetEventCountsQueryWithRequestDto(eventCountRequestDto).execute();

    // then validation exception is thrown
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void getAllEventCountsWithSearchTermAndSortAndOrderParameters() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .searchTerm("etch")
      .orderBy("eventName")
      .sortOrder(SortOrder.DESC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createGetEventCountsQueryWithRequestDto(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then matching events are returned with ordering parameters respected
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .extracting("group", "source", "eventName", "count")
      .containsExactly(
        tuple("backend", "ketchup", "signup-event", 4L),
        tuple("BACKEND", "mayonnaise", "ketchupevent", 2L),
        tuple("ketchup", "mayonnaise", "blacklisted_event", 2L)
      );
  }

  private List<EventDto> createEventsForIngestion() {
    List<EventDto> eventDtos = new ArrayList<>();
    eventDtos.addAll(createEventDtoListWithProperties("backend", "ketchup", "signup-event", 4));
    eventDtos.addAll(createEventDtoListWithProperties("frontend", "mayonnaise", "registered_event", 2));
    eventDtos.addAll(createEventDtoListWithProperties("management", "BBQ_sauce", "onboarded_event", 1));
    eventDtos.addAll(createEventDtoListWithProperties("ketchup", "mayonnaise", "blacklisted_event", 2));
    eventDtos.addAll(createEventDtoListWithProperties("BACKEND", "mayonnaise", "ketchupevent", 2));
    return eventDtos;
  }

  private void ingestEvents(final List<EventDto> eventDtos) {
    embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestEventBatch(
        eventDtos,
        embeddedOptimizeExtension.getConfigurationService()
          .getIngestionConfiguration()
          .getApiSecret()
      )
      .execute();
  }

  private OptimizeRequestExecutor createGetEventCountsQueryWithRequestDto(EventCountRequestDto eventCountRequestDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventRequest(eventCountRequestDto);
  }

  private List<EventDto> createEventDtoListWithProperties(String group, String source, String eventName, int quantity) {
    return IntStream.range(0, quantity)
      .mapToObj(operand -> createRandomEventDtoPropertiesBuilder()
        .group(group)
        .source(source)
        .eventName(eventName)
        .build())
      .collect(toList());
  }

  private EventDto.EventDtoBuilder createRandomEventDtoPropertiesBuilder() {
    return EventDto.builder()
      .id(UUID.randomUUID().toString())
      .timestamp(System.currentTimeMillis())
      .traceId(RandomStringUtils.randomAlphabetic(10))
      .duration(Math.abs(RANDOM.nextLong()))
      .data(ImmutableMap.of(
        RandomStringUtils.randomAlphabetic(5), RANDOM.nextInt(),
        RandomStringUtils.randomAlphabetic(5), RANDOM.nextBoolean(),
        RandomStringUtils.randomAlphabetic(5), RandomStringUtils.randomAlphabetic(5)
      ));
  }

}
