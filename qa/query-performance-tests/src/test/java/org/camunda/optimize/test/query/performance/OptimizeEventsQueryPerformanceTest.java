/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.query.performance;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.camunda.optimize.dto.optimize.query.event.EventCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.sorting.EventCountSorter;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.event.EventCountDto.Fields.eventName;
import static org.camunda.optimize.dto.optimize.query.event.EventCountDto.Fields.group;
import static org.camunda.optimize.dto.optimize.query.event.EventCountDto.Fields.source;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

@Slf4j
public class OptimizeEventsQueryPerformanceTest extends AbstractQueryPerformanceTest {

  @BeforeEach
  public void init() {
    embeddedOptimizeExtension.getElasticSearchSchemaManager()
      .createIndexIfMissing(
        embeddedOptimizeExtension.getOptimizeElasticClient(),
        new EventSequenceCountIndex(EXTERNAL_EVENTS_INDEX_SUFFIX)
      );
  }

  @ParameterizedTest
  @MethodSource("eventCountSorterAndSearchTerm")
  public void testQueryPerformance_getEventCounts(EventCountSorter eventCountSorter, String searchTerm) {
    // given
    final int numberOfDifferentEvents = getNumberOfEvents();

    addEventSequenceCountsToOptimize(numberOfDifferentEvents);
    EventCountRequestDto countRequest = EventCountRequestDto.builder()
      .eventSources(Collections.singletonList(
        EventSourceEntryDto.builder()
          .type(EventSourceType.EXTERNAL)
          .eventScope(Collections.singletonList(EventScopeType.ALL))
          .build()))
      .build();

    // when
    final Instant start = Instant.now();
    final List<EventCountDto> eventCounts = embeddedOptimizeExtension.getRequestExecutor()
      .buildPostEventCountRequest(eventCountSorter, searchTerm, countRequest)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());
    final Instant finish = Instant.now();
    long responseTimeMs = Duration.between(start, finish).toMillis();

    // then
    assertThat(eventCounts).hasSize(numberOfDifferentEvents);
    log.info("{} query response time: {}", getTestDisplayName(), responseTimeMs);
    assertThat(responseTimeMs).isLessThanOrEqualTo(getMaxAllowedQueryTime());
  }

  private void addEventSequenceCountsToOptimize(final int numberOfDifferentEvents) {
    final Map<String, Object> sequencesById = IntStream.range(0, numberOfDifferentEvents)
      .mapToObj(index -> EventTypeDto.builder()
        .eventName("eventName_" + index)
        .group("group")
        .source("source")
        .build())
      .map(sourceEvent -> EventSequenceCountDto.builder()
        .id(IdGenerator.getNextId())
        .sourceEvent(sourceEvent)
        .count(RandomUtils.nextLong(0, 1000))
        .build())
      .collect(Collectors.toMap(EventSequenceCountDto::getId, seq -> seq));
    addToElasticsearch(
      new EventSequenceCountIndex(EXTERNAL_EVENTS_INDEX_SUFFIX).getIndexName(),
      sequencesById
    );
  }

  private static Stream<Arguments> eventCountSorterAndSearchTerm() {
    return Stream.of(
      Arguments.of(null, null),
      Arguments.of(null, "a"),
      Arguments.of(eventCountSorter(eventName, SortOrder.ASC), "a"),
      Arguments.of(eventCountSorter(eventName, SortOrder.ASC), null),
      Arguments.of(eventCountSorter(eventName, SortOrder.DESC), null),
      Arguments.of(eventCountSorter(group, SortOrder.ASC), "a"),
      Arguments.of(eventCountSorter(group, SortOrder.ASC), null),
      Arguments.of(eventCountSorter(group, SortOrder.DESC), null),
      Arguments.of(eventCountSorter(source, SortOrder.ASC), "a"),
      Arguments.of(eventCountSorter(source, SortOrder.ASC), null),
      Arguments.of(eventCountSorter(source, SortOrder.DESC), null)
    );
  }

  private static EventCountSorter eventCountSorter(final String sortBy, final SortOrder sortOrder) {
    EventCountSorter sorter = new EventCountSorter();
    sorter.setSortBy(sortBy);
    sorter.setSortOrder(sortOrder);
    return sorter;
  }

}
