/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.query.performance;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.camunda.optimize.dto.optimize.query.event.process.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.sorting.EventCountSorter;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto.Fields.eventName;
import static org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto.Fields.group;
import static org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto.Fields.source;
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

    // when & then
    assertThatListEndpointMaxAllowedQueryTimeIsMet(
      numberOfDifferentEvents,
      () -> embeddedOptimizeExtension.getRequestExecutor()
        .buildPostEventCountRequest(eventCountSorter, searchTerm, countRequest)
        .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode())
    );
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
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(
      new EventSequenceCountIndex(EXTERNAL_EVENTS_INDEX_SUFFIX).getIndexName(), sequencesById
    );
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private static Stream<Arguments> eventCountSorterAndSearchTerm() {
    return Stream.of(
      Arguments.of(null, null),
      Arguments.of(null, "a"),
      Arguments.of(new EventCountSorter(eventName, SortOrder.ASC), "a"),
      Arguments.of(new EventCountSorter(eventName, SortOrder.ASC), null),
      Arguments.of(new EventCountSorter(eventName, SortOrder.DESC), null),
      Arguments.of(new EventCountSorter(group, SortOrder.ASC), "a"),
      Arguments.of(new EventCountSorter(group, SortOrder.ASC), null),
      Arguments.of(new EventCountSorter(group, SortOrder.DESC), null),
      Arguments.of(new EventCountSorter(source, SortOrder.ASC), "a"),
      Arguments.of(new EventCountSorter(source, SortOrder.ASC), null),
      Arguments.of(new EventCountSorter(source, SortOrder.DESC), null)
    );
  }

}
