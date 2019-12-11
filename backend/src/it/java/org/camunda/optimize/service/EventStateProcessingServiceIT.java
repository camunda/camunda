/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventTraceStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.TracedEventDto;
import org.camunda.optimize.service.events.stateprocessing.EventStateProcessingService;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.reader.ElasticsearchHelper.mapHits;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_TRACE_STATE_INDEX_NAME;

public class EventStateProcessingServiceIT extends AbstractIT {

  private EventStateProcessingService eventStateProcessingService;

  public static final Random RANDOM = new Random();

  @BeforeEach
  public void setup() {
    eventStateProcessingService = embeddedOptimizeExtension.getEventStateProcessingService();
  }

  @Test
  public void noEventsToProcess() {
    // when
    eventStateProcessingService.processUncountedEvents();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(EVENT_TRACE_STATE_INDEX_NAME)).isEqualTo(0);
    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(EVENT_SEQUENCE_COUNT_INDEX_NAME)).isEqualTo(0);
  }

  @Test
  public void processSingleBatchOfEventsNewUniqueTraceIds() throws IOException {
    // given
    EventDto eventDtoTraceOne = createEventDtoWithProperties("traceOne", "backend", "ketchup", "signup-event", 100L);
    EventDto eventDtoTraceTwo = createEventDtoWithProperties("traceTwo", "backend", "ketchup", "register-event", 200L);
    EventDto eventDtoTraceThree = createEventDtoWithProperties(
      "traceThree", "backend", "mayonnaise", "onboard-event", 300L
    );
    ingestEventBatch(Arrays.asList(eventDtoTraceOne, eventDtoTraceTwo, eventDtoTraceThree));

    // when
    eventStateProcessingService.processUncountedEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then trace states are stored and sequence counts reflect traces
    assertThat(getAllStoredEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(
        eventDtoTraceOne.getTraceId(),
        Collections.singletonList(TracedEventDto.fromEventDto(eventDtoTraceOne))
      ),
      new EventTraceStateDto(
        eventDtoTraceTwo.getTraceId(),
        Collections.singletonList(TracedEventDto.fromEventDto(eventDtoTraceTwo))
      ),
      new EventTraceStateDto(
        eventDtoTraceThree.getTraceId(),
        Collections.singletonList(TracedEventDto.fromEventDto(eventDtoTraceThree))
      )
    );
    assertThat(getAllStoredEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventDtoTraceOne, null, 1),
      createSequenceFromSourceAndTargetEvents(eventDtoTraceTwo, null, 1),
      createSequenceFromSourceAndTargetEvents(eventDtoTraceThree, null, 1)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());
  }

  @Test
  public void processSingleBatchOfEventsIncludingSharedTraceIds() throws IOException {
    // given
    String traceId = "someTraceId";
    EventDto eventDtoOne = createEventDtoWithProperties(traceId, "backend", "ketchup", "signup-event", 100L);
    EventDto eventDtoTwo = createEventDtoWithProperties(traceId, "backend", "ketchup", "register-event", 200L);
    EventDto eventDtoThree = createEventDtoWithProperties(traceId, "backend", "mayonnaise", "onboard-event", 300L);
    ingestEventBatch(Arrays.asList(eventDtoOne, eventDtoTwo, eventDtoThree));

    // when
    eventStateProcessingService.processUncountedEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then trace state is stored in correct order and sequence counts reflect traces
    assertThat(getAllStoredEventTraceStates())
      .containsExactly(
        new EventTraceStateDto(traceId, Arrays.asList(
          TracedEventDto.fromEventDto(eventDtoOne),
          TracedEventDto.fromEventDto(eventDtoTwo),
          TracedEventDto.fromEventDto(eventDtoThree)
        ))
      );
    assertThat(getAllStoredEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventDtoOne, eventDtoTwo, 1),
      createSequenceFromSourceAndTargetEvents(eventDtoTwo, eventDtoThree, 1),
      createSequenceFromSourceAndTargetEvents(eventDtoThree, null, 1)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());
  }

  @Test
  public void processSingleBatchOfEventsWithRepeatedSequencesAcrossTraces() throws IOException {
    // given
    String traceIdOne = "traceIdOne";
    String traceIdTwo = "traceIdTwo";
    String traceIdThree = "traceIdThree";
    EventDto eventDtoTraceOneEventOne = createEventDtoWithProperties(
      traceIdOne, "backend", "ketchup", "signup-event", 100L
    );
    EventDto eventDtoTraceTwoEventOne = createEventDtoWithProperties(
      traceIdTwo, "backend", "ketchup", "signup-event", 200L
    );
    EventDto eventDtoTraceThreeEventOne = createEventDtoWithProperties(
      traceIdThree, "backend", "ketchup", "signup-event", 300L
    );
    EventDto eventDtoTraceOneEventTwo = createEventDtoWithProperties(
      traceIdOne, "backend", "ketchup", "register-event", 400L
    );
    EventDto eventDtoTraceTwoEventTwo = createEventDtoWithProperties(
      traceIdTwo, "backend", "ketchup", "register-event", 500L
    );
    EventDto eventDtoTraceThreeEventTwo = createEventDtoWithProperties(
      traceIdThree, "backend", "ketchup", "onboarded-event", 600L
    );
    ingestEventBatch(Arrays.asList(eventDtoTraceOneEventOne, eventDtoTraceTwoEventOne, eventDtoTraceThreeEventOne,
                                   eventDtoTraceOneEventTwo, eventDtoTraceTwoEventTwo, eventDtoTraceThreeEventTwo
    ));

    // when
    eventStateProcessingService.processUncountedEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then trace state and sequence counts are correct
    assertThat(getAllStoredEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceIdOne, Arrays.asList(
        TracedEventDto.fromEventDto(eventDtoTraceOneEventOne),
        TracedEventDto.fromEventDto(eventDtoTraceOneEventTwo)
      )),
      new EventTraceStateDto(traceIdTwo, Arrays.asList(
        TracedEventDto.fromEventDto(eventDtoTraceTwoEventOne),
        TracedEventDto.fromEventDto(eventDtoTraceTwoEventTwo)
      )),
      new EventTraceStateDto(traceIdThree, Arrays.asList(
        TracedEventDto.fromEventDto(eventDtoTraceThreeEventOne),
        TracedEventDto.fromEventDto(eventDtoTraceThreeEventTwo)
      ))
    );
    assertThat(getAllStoredEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventDtoTraceOneEventOne, eventDtoTraceOneEventTwo, 2),
      createSequenceFromSourceAndTargetEvents(eventDtoTraceOneEventTwo, null, 2),
      createSequenceFromSourceAndTargetEvents(eventDtoTraceThreeEventOne, eventDtoTraceThreeEventTwo, 1),
      createSequenceFromSourceAndTargetEvents(eventDtoTraceThreeEventTwo, null, 1)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());
  }

  @Test
  public void processMultipleBatchesOfEventsWithMultipleTracesNoTimestampModifications() throws IOException {
    // given
    String traceIdOne = "traceIdOne";
    String traceIdTwo = "traceIdTwo";
    String traceIdThree = "traceIdThree";
    EventDto eventOneTraceOne = createEventDtoWithProperties(traceIdOne, "backend", "ketchup", "signup-event", 100L);
    EventDto eventOneTraceTwo = createEventDtoWithProperties(traceIdTwo, "backend", "ketchup", "signup-event", 200L);
    EventDto eventOneTraceThree = createEventDtoWithProperties(traceIdThree, "backend", "ketchup", "signup-event", 300L);
    ingestEventBatch(Arrays.asList(eventOneTraceOne, eventOneTraceTwo, eventOneTraceThree));

    // when
    eventStateProcessingService.processUncountedEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then trace state and sequence counts are correct after initial batch
    assertThat(getAllStoredEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceIdOne, Collections.singletonList(TracedEventDto.fromEventDto(eventOneTraceOne))),
      new EventTraceStateDto(traceIdTwo, Collections.singletonList(TracedEventDto.fromEventDto(eventOneTraceTwo))),
      new EventTraceStateDto(traceIdThree, Collections.singletonList(TracedEventDto.fromEventDto(eventOneTraceThree)))
    );
    assertThat(getAllStoredEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventOneTraceOne, null, 3)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());

    // when second batch adds further trace events
    EventDto eventTwoTraceOne = createEventDtoWithProperties(traceIdOne, "backend", "ketchup", "register-event", 400L);
    EventDto eventTwoTraceTwo = createEventDtoWithProperties(traceIdTwo, "backend", "ketchup", "register-event", 500L);
    EventDto eventThreeTraceOne = createEventDtoWithProperties(traceIdOne, "backend", "ketchup", "onboard-event", 600L);
    ingestEventBatch(Arrays.asList(eventTwoTraceOne, eventTwoTraceTwo, eventThreeTraceOne));
    eventStateProcessingService.processUncountedEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then trace state and sequence counts are correct after processing
    assertThat(getAllStoredEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceIdOne, Arrays.asList(
        TracedEventDto.fromEventDto(eventOneTraceOne), TracedEventDto.fromEventDto(eventTwoTraceOne), TracedEventDto.fromEventDto(eventThreeTraceOne))),
      new EventTraceStateDto(traceIdTwo, Arrays.asList(TracedEventDto.fromEventDto(eventOneTraceTwo), TracedEventDto.fromEventDto(eventTwoTraceTwo))),
      new EventTraceStateDto(traceIdThree, Collections.singletonList(TracedEventDto.fromEventDto(eventOneTraceThree)))
    );
    assertThat(getAllStoredEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventOneTraceOne, eventTwoTraceOne, 2),
      createSequenceFromSourceAndTargetEvents(eventTwoTraceOne, eventThreeTraceOne, 1),
      createSequenceFromSourceAndTargetEvents(eventThreeTraceOne, null, 1),
      createSequenceFromSourceAndTargetEvents(eventTwoTraceTwo, null, 1),
      createSequenceFromSourceAndTargetEvents(eventOneTraceThree, null, 1)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());
  }

  @Test
  public void processMultipleBatchesOfEventsWithEventTimestampModificationRequiringAdjustmentsForTraceEvent() throws
                                                                                                              IOException {
    // given
    String traceId = "traceId";
    EventDto eventDtoOne = createEventDtoWithProperties(traceId, "backend", "ketchup", "signup-event", 100L);
    EventDto eventDtoTwo = createEventDtoWithProperties(traceId, "backend", "ketchup", "register-event", 200L);
    EventDto eventDtoThree = createEventDtoWithProperties(traceId, "backend", "ketchup", "onboarded-event", 300L);
    ingestEventBatch(Arrays.asList(eventDtoOne, eventDtoTwo, eventDtoThree));

    // when
    eventStateProcessingService.processUncountedEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then trace state and sequence counts are correct after initial batch
    assertThat(getAllStoredEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceId, Arrays.asList(
        TracedEventDto.fromEventDto(eventDtoOne),
        TracedEventDto.fromEventDto(eventDtoTwo),
        TracedEventDto.fromEventDto(eventDtoThree)
      ))
    );
    assertThat(getAllStoredEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventDtoOne, eventDtoTwo, 1),
      createSequenceFromSourceAndTargetEvents(eventDtoTwo, eventDtoThree, 1),
      createSequenceFromSourceAndTargetEvents(eventDtoThree, null, 1)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());

    // when second batch includes timestamp modification
    EventDto eventDtoThreeModified = createEventDtoWithProperties(
      traceId, "backend", "ketchup", "onboarded-event", 150L
    );
    ingestEventBatch(Collections.singletonList(eventDtoThreeModified));
    eventStateProcessingService.processUncountedEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then trace state and sequence counts are correct after modification
    assertThat(getAllStoredEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceId, Arrays.asList(
        TracedEventDto.fromEventDto(eventDtoOne),
        TracedEventDto.fromEventDto(eventDtoThreeModified),
        TracedEventDto.fromEventDto(eventDtoTwo)
      ))
    );
    assertThat(getAllStoredEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventDtoOne, eventDtoTwo, 0),
      createSequenceFromSourceAndTargetEvents(eventDtoTwo, eventDtoThree, 0),
      createSequenceFromSourceAndTargetEvents(eventDtoThree, null, 0),
      createSequenceFromSourceAndTargetEvents(eventDtoOne, eventDtoThreeModified, 1),
      createSequenceFromSourceAndTargetEvents(eventDtoThreeModified, eventDtoTwo, 1),
      createSequenceFromSourceAndTargetEvents(eventDtoTwo, null, 1)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());
  }

  @Test
  public void processMultipleBatchesOfEventsWithSingleTraceDuplicateEventsAcrossBatches() throws IOException {
    // given
    String traceId = "traceIdOne";
    EventDto eventOne = createEventDtoWithProperties(traceId, "backend", "ketchup", "signup-event", 100L);
    EventDto eventTwo = createEventDtoWithProperties(traceId, "backend", "ketchup", "register-event", 200L);
    EventDto eventThree = createEventDtoWithProperties(traceId, "backend", "ketchup", "onboarded-event", 300L);
    ingestEventBatch(Arrays.asList(eventOne, eventTwo, eventThree));

    // when
    eventStateProcessingService.processUncountedEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then trace state and sequence counts are correct after initial batch
    assertThat(getAllStoredEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceId, Arrays.asList(
        TracedEventDto.fromEventDto(eventOne),
        TracedEventDto.fromEventDto(eventTwo),
        TracedEventDto.fromEventDto(eventThree)
      ))
    );
    assertThat(getAllStoredEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventOne, eventTwo, 1),
      createSequenceFromSourceAndTargetEvents(eventTwo, eventThree, 1),
      createSequenceFromSourceAndTargetEvents(eventThree, null, 1)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());

    // when second batch adds repeated event trace already processes
    EventDto eventTwoRepeated = createEventDtoWithProperties(traceId, "backend", "ketchup", "register-event", 200L);
    ingestEventBatch(Collections.singletonList(eventTwoRepeated));
    eventStateProcessingService.processUncountedEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then trace state and sequence counts remain correct after processing
    assertThat(getAllStoredEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceId, Arrays.asList(
        TracedEventDto.fromEventDto(eventOne),
        TracedEventDto.fromEventDto(eventTwo),
        TracedEventDto.fromEventDto(eventThree)
      ))
    );
    assertThat(getAllStoredEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventOne, eventTwo, 1),
      createSequenceFromSourceAndTargetEvents(eventTwo, eventThree, 1),
      createSequenceFromSourceAndTargetEvents(eventThree, null, 1)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());
  }

  private Long getLastProcessedEntityTimestampFromElasticsearch() throws IOException {
    return elasticSearchIntegrationTestExtension.getLastProcessedEventTimestamp().toInstant().toEpochMilli();
  }

  private Long findMostRecentEventTimestamp() {
    return getAllStoredEvents().stream()
      .map(EventDto::getIngestionTimestamp)
      .mapToLong(e -> e).max().getAsLong();
  }

  private List<EventTraceStateDto> getAllStoredEventTraceStates() {
    return getAllStoredDocumentsForIndexAsClass(EVENT_TRACE_STATE_INDEX_NAME, EventTraceStateDto.class);
  }

  private List<EventSequenceCountDto> getAllStoredEventSequenceCounts() {
    return getAllStoredDocumentsForIndexAsClass(EVENT_SEQUENCE_COUNT_INDEX_NAME, EventSequenceCountDto.class);
  }

  private List<EventDto> getAllStoredEvents() {
    return getAllStoredDocumentsForIndexAsClass(EVENT_INDEX_NAME, EventDto.class);
  }

  private <T> List<T> getAllStoredDocumentsForIndexAsClass(String indexName, Class<T> dtoClass) {
    SearchResponse response = elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(indexName);
    return mapHits(response.getHits(), dtoClass, embeddedOptimizeExtension.getObjectMapper()
    );
  }

  private void ingestEventBatch(final List<EventDto> eventDtos) {
    embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestEventBatch(
        eventDtos,
        embeddedOptimizeExtension.getConfigurationService()
          .getIngestionConfiguration()
          .getApiSecret()
      )
      .execute();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private EventDto createEventDtoWithProperties(String traceId, String group, String source, String eventName,
                                                Long timestamp) {
    return createRandomEventDtoPropertiesBuilder()
      .traceId(traceId)
      .group(group)
      .source(source)
      .eventName(eventName)
      .timestamp(timestamp)
      .build();
  }

  private EventDto.EventDtoBuilder createRandomEventDtoPropertiesBuilder() {
    return EventDto.builder()
      .id(UUID.randomUUID().toString())
      .duration(Math.abs(RANDOM.nextLong()))
      .data(ImmutableMap.of(
        RandomStringUtils.randomAlphabetic(5), RANDOM.nextInt(),
        RandomStringUtils.randomAlphabetic(5), RANDOM.nextBoolean(),
        RandomStringUtils.randomAlphabetic(5), RandomStringUtils.randomAlphabetic(5)
      ));
  }

  private EventSequenceCountDto createSequenceFromSourceAndTargetEvents(EventDto source, EventDto target, long count) {
    EventTypeDto sourceEvent = Optional.ofNullable(source)
      .map(sou -> new EventTypeDto(sou.getGroup(), sou.getSource(), sou.getEventName()))
      .orElse(null);
    EventTypeDto targetEvent = Optional.ofNullable(target)
      .map(tar -> new EventTypeDto(tar.getGroup(), tar.getSource(), tar.getEventName()))
      .orElse(null);
    EventSequenceCountDto eventSequenceCountDto = EventSequenceCountDto.builder()
      .sourceEvent(sourceEvent)
      .targetEvent(targetEvent)
      .count(count)
      .build();
    eventSequenceCountDto.generateIdForEventSequenceCountDto();
    return eventSequenceCountDto;
  }

}
