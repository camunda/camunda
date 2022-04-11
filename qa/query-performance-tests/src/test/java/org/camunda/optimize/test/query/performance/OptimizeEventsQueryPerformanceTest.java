/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.query.performance;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.sorting.EventCountSorter;
import org.camunda.optimize.dto.optimize.rest.sorting.SortRequestDto;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto.Fields.eventName;
import static org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto.Fields.group;
import static org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto.Fields.source;
import static org.camunda.optimize.dto.optimize.query.sorting.SortOrder.ASC;
import static org.camunda.optimize.dto.optimize.query.sorting.SortOrder.DESC;
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
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessAccessUserIds().add(DEFAULT_USER);
  }

  @ParameterizedTest
  @MethodSource("eventCountSorterAndSearchTerm")
  public void testQueryPerformance_getEventCounts(EventCountSorter eventCountSorter, String searchTerm) {
    // given
    final int numberOfDifferentEvents = getNumberOfEventsToIngest();
    addEventSequenceCountsToOptimize(numberOfDifferentEvents);

    EventCountRequestDto countRequest = EventCountRequestDto.builder()
      .eventSources(Collections.singletonList(
        ExternalEventSourceEntryDto.builder()
          .configuration(ExternalEventSourceConfigDto.builder()
                           .eventScope(Collections.singletonList(EventScopeType.ALL))
                           .includeAllGroups(true).build())
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

  @ParameterizedTest
  @MethodSource("eventListSearchParameters")
  public void testQueryPerformance_getEvents(final String searchTerm, final SortRequestDto sortRequestDto) {
    // given
    final int numberOfDifferentEvents = getNumberOfEventsToIngest();
    addEventsToOptimize(numberOfDifferentEvents);

    EventSearchRequestDto searchRequestDto = new EventSearchRequestDto(
      searchTerm,
      sortRequestDto,
      new PaginationRequestDto(getNumberOfEvents(), 0)
    );

    // when & then
    assertThatListEndpointMaxAllowedQueryTimeIsMet(
      getNumberOfEvents(),
      () -> embeddedOptimizeExtension.getRequestExecutor()
        .buildGetEventListRequest(searchRequestDto)
        .executeAndGetPage(DeletableEventDto.class, Response.Status.OK.getStatusCode()).getResults()
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

  private void addEventsToOptimize(final int numberOfDifferentEvents) {
    final Map<String, Object> eventsById = IntStream.range(0, numberOfDifferentEvents)
      .mapToObj(index -> EventDto.builder()
        .timestamp(Instant.now().toEpochMilli())
        .ingestionTimestamp(Instant.now().toEpochMilli())
        .traceId("traceId_" + index % 4)
        .eventName("eventName_" + index % 4)
        .group("group_" + index % 5)
        .source("source_" + index % 6)
        .id(IdGenerator.getNextId())
        .build())
      .collect(Collectors.toMap(EventDto::getId, event -> event));
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(
      new EventIndex().getIndexName(), eventsById
    );
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private static Stream<Arguments> eventCountSorterAndSearchTerm() {
    return Stream.of(
      Arguments.of(null, null),
      Arguments.of(null, "a"),
      Arguments.of(new EventCountSorter(eventName, ASC), "a"),
      Arguments.of(new EventCountSorter(eventName, ASC), null),
      Arguments.of(new EventCountSorter(eventName, SortOrder.DESC), null),
      Arguments.of(new EventCountSorter(group, ASC), "a"),
      Arguments.of(new EventCountSorter(group, ASC), null),
      Arguments.of(new EventCountSorter(group, SortOrder.DESC), null),
      Arguments.of(new EventCountSorter(source, ASC), "a"),
      Arguments.of(new EventCountSorter(source, ASC), null),
      Arguments.of(new EventCountSorter(source, SortOrder.DESC), null)
    );
  }

  private static Stream<Arguments> eventListSearchParameters() {
    return EventSearchRequestDto.sortableFields.stream()
      .flatMap(sortableField -> Stream.of(
        new SortRequestDto(sortableField, ASC),
        new SortRequestDto(sortableField, DESC)
      ).flatMap(sort -> Stream.of(
        Arguments.of(null, sort),
        Arguments.of("eventName_1", sort),
        Arguments.of("traceId_1", sort),
        Arguments.of("group_1", sort),
        Arguments.of("source_1", sort)
      )));
  }

}
