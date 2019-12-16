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
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.reader.ElasticsearchHelper.mapHits;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_TRACE_STATE_INDEX_NAME;

public class EventStateProcessingServiceIT extends AbstractIT {

  private EventStateProcessingService eventStateProcessingService;

  private static final Random RANDOM = new Random();

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
    EventDto eventDtoTraceOne = createEventDtoWithProperties(
      "traceOne", "eventIdOne", "backend", "ketchup", "signup-event", 100L);
    EventDto eventDtoTraceTwo = createEventDtoWithProperties(
      "traceTwo", "eventIdTwo", "backend", "ketchup", "register-event", 200L);
    EventDto eventDtoTraceThree = createEventDtoWithProperties(
      "traceThree", "eventIdThree", "backend", "mayonnaise", "onboard-event", 300L
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
    EventDto eventDtoOne = createEventDtoWithProperties(
      traceId, "eventIdOne", "backend", "ketchup", "signup-event", 100L);
    EventDto eventDtoTwo = createEventDtoWithProperties(
      traceId, "eventIdTwo", "backend", "ketchup", "register-event", 200L);
    EventDto eventDtoThree = createEventDtoWithProperties(
      traceId, "eventIdThree", "backend", "mayonnaise", "onboard-event", 300L);
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
      traceIdOne, "eventIdOne", "backend", "ketchup", "signup-event", 100L
    );
    EventDto eventDtoTraceTwoEventOne = createEventDtoWithProperties(
      traceIdTwo, "eventIdTwo", "backend", "ketchup", "signup-event", 200L
    );
    EventDto eventDtoTraceThreeEventOne = createEventDtoWithProperties(
      traceIdThree, "eventIdThree", "backend", "ketchup", "signup-event", 300L
    );
    EventDto eventDtoTraceOneEventTwo = createEventDtoWithProperties(
      traceIdOne, "eventIdFour", "backend", "ketchup", "register-event", 400L
    );
    EventDto eventDtoTraceTwoEventTwo = createEventDtoWithProperties(
      traceIdTwo, "eventIdFive", "backend", "ketchup", "register-event", 500L
    );
    EventDto eventDtoTraceThreeEventTwo = createEventDtoWithProperties(
      traceIdThree, "eventIdSix", "backend", "ketchup", "onboarded-event", 600L
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
    EventDto eventOneTraceOne = createEventDtoWithProperties(
      traceIdOne, "eventIdOne", "backend", "ketchup", "signup-event", 100L);
    EventDto eventOneTraceTwo = createEventDtoWithProperties(
      traceIdTwo, "eventIdTwo", "backend", "ketchup", "signup-event", 200L);
    EventDto eventOneTraceThree = createEventDtoWithProperties(
      traceIdThree, "eventIdThree", "backend", "ketchup", "signup-event", 300L);
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
    EventDto eventTwoTraceOne = createEventDtoWithProperties(
      traceIdOne, "eventIdFour", "backend", "ketchup", "register-event", 400L);
    EventDto eventTwoTraceTwo = createEventDtoWithProperties(
      traceIdTwo, "eventIdFive", "backend", "ketchup", "register-event", 500L);
    EventDto eventThreeTraceOne = createEventDtoWithProperties(
      traceIdOne, "eventIdSix", "backend", "ketchup", "onboard-event", 600L);
    ingestEventBatch(Arrays.asList(eventTwoTraceOne, eventTwoTraceTwo, eventThreeTraceOne));
    eventStateProcessingService.processUncountedEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then trace state and sequence counts are correct after processing
    assertThat(getAllStoredEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceIdOne, Arrays.asList(
        TracedEventDto.fromEventDto(eventOneTraceOne),
        TracedEventDto.fromEventDto(eventTwoTraceOne),
        TracedEventDto.fromEventDto(eventThreeTraceOne)
      )),
      new EventTraceStateDto(
        traceIdTwo,
        Arrays.asList(
          TracedEventDto.fromEventDto(eventOneTraceTwo),
          TracedEventDto.fromEventDto(eventTwoTraceTwo)
        )
      ),
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
  public void processMultipleBatchesOfEventsWithEventTimestampModificationRequiringAdjustmentsForTraceEvent()
    throws IOException {
    // given
    String traceId = "traceId";
    EventDto eventDtoOne = createEventDtoWithProperties(
      traceId, "eventIdOne", "backend", "ketchup", "signup-event", 100L);
    EventDto eventDtoTwo = createEventDtoWithProperties(
      traceId, "eventIdTwo", "backend", "ketchup", "register-event", 200L);
    EventDto eventDtoThree = createEventDtoWithProperties(
      traceId, "eventIdThree", "backend", "ketchup", "onboarded-event", 300L);
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

    // when second batch includes already ingested event with a new timestamp (a modification)
    EventDto eventDtoThreeModified = createEventDtoWithProperties(
      traceId,
      eventDtoThree.getId(),
      eventDtoThree.getGroup(),
      eventDtoThree.getSource(),
      eventDtoThree.getEventName(),
      150L
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
    EventDto eventOne = createEventDtoWithProperties(
      traceId, "eventIdOne", "backend", "ketchup", "signup-event", 100L);
    EventDto eventTwo = createEventDtoWithProperties(
      traceId, "eventIdTwo", "backend", "ketchup", "register-event", 200L);
    EventDto eventThree = createEventDtoWithProperties
      (traceId, "eventIdThree", "backend", "ketchup", "onboarded-event", 300L);
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
    EventDto eventTwoRepeated = createEventDtoWithProperties(
      traceId, eventTwo.getId(), eventTwo.getGroup(), eventTwo.getSource(), eventTwo.getEventName(), 200L);

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

  @Test
  public void processMultipleBatchesOfEventsWithSingleTraceAndMultipleOccurringEventsWithDifferentIds() throws IOException {
    // given
    String traceId = "traceIdOne";
    EventDto eventTaskA = createEventDtoWithProperties(
      traceId, "eventIdOne", "backend", "ketchup", "signup-event", 100L);
    EventDto eventTaskB = createEventDtoWithProperties(
      traceId, "eventIdTwo", "backend", "ketchup", "register-event", 200L);
    EventDto eventTaskC = createEventDtoWithProperties(
      traceId, "eventIdThree", "backend", "ketchup", "onboarded-event", 300L);
    ingestEventBatch(Arrays.asList(eventTaskA, eventTaskB, eventTaskC));

    // when
    eventStateProcessingService.processUncountedEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then trace state and sequence counts are correct after initial batch
    assertThat(getAllStoredEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceId, Arrays.asList(
        TracedEventDto.fromEventDto(eventTaskA),
        TracedEventDto.fromEventDto(eventTaskB),
        TracedEventDto.fromEventDto(eventTaskC)
      ))
    );
    assertThat(getAllStoredEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventTaskA, eventTaskB, 1),
      createSequenceFromSourceAndTargetEvents(eventTaskB, eventTaskC, 1),
      createSequenceFromSourceAndTargetEvents(eventTaskC, null, 1)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());

    // when second batch adds the second occurrence of an event with a new event ID, reflecting a loop in the
    // BPMN model
    EventDto eventTaskD = createEventDtoWithProperties(
      traceId, "eventIdFour", "backend", "ketchup", "complained-event", 400L);
    EventDto eventTaskBSecondOccurrence = createEventDtoWithProperties(
      traceId, "eventIdFive", eventTaskB.getGroup(), eventTaskB.getSource(), eventTaskB.getEventName(), 500L
    );

    ingestEventBatch(Arrays.asList(eventTaskD, eventTaskBSecondOccurrence));
    eventStateProcessingService.processUncountedEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then trace state and sequence counts remain correct after processing and loop is correctly traced
    assertThat(getAllStoredEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceId, Arrays.asList(
        TracedEventDto.fromEventDto(eventTaskA),
        TracedEventDto.fromEventDto(eventTaskB),
        TracedEventDto.fromEventDto(eventTaskC),
        TracedEventDto.fromEventDto(eventTaskD),
        TracedEventDto.fromEventDto(eventTaskBSecondOccurrence)
      ))
    );
    assertThat(getAllStoredEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventTaskA, eventTaskB, 1),
      createSequenceFromSourceAndTargetEvents(eventTaskB, eventTaskC, 1),
      createSequenceFromSourceAndTargetEvents(eventTaskC, eventTaskD, 1),
      createSequenceFromSourceAndTargetEvents(eventTaskD, eventTaskB, 1),
      createSequenceFromSourceAndTargetEvents(eventTaskB, null, 1),
      createSequenceFromSourceAndTargetEvents(eventTaskC, null, 0)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());

    // when third batch adds further occurrences of events with new IDs, reflecting further loops and events beyond
    // the loop in the BPMN model
    EventDto eventTaskCSecondOccurrence = createEventDtoWithProperties(
      traceId, "eventIdSix", eventTaskC.getGroup(), eventTaskC.getSource(), eventTaskC.getEventName(), 600L);
    EventDto eventTaskDSecondOccurrence = createEventDtoWithProperties(
      traceId, "eventIdSeven", eventTaskD.getGroup(), eventTaskD.getSource(), eventTaskD.getEventName(), 700L);
    EventDto eventTaskBThirdOccurrence = createEventDtoWithProperties(
      traceId, "eventIdEight", eventTaskB.getGroup(), eventTaskB.getSource(), eventTaskB.getEventName(), 800L);
    EventDto eventTaskE = createEventDtoWithProperties
      (traceId, "eventIdNine", "backend", "ketchup", "helped-event", 900L);

    ingestEventBatch(Arrays.asList(eventTaskCSecondOccurrence, eventTaskDSecondOccurrence, eventTaskBThirdOccurrence, eventTaskE));
    eventStateProcessingService.processUncountedEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then trace state and sequence counts remain correct after processing and loop is correct traced
    assertThat(getAllStoredEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceId, Arrays.asList(
        TracedEventDto.fromEventDto(eventTaskA),
        TracedEventDto.fromEventDto(eventTaskB),
        TracedEventDto.fromEventDto(eventTaskC),
        TracedEventDto.fromEventDto(eventTaskD),
        TracedEventDto.fromEventDto(eventTaskBSecondOccurrence),
        TracedEventDto.fromEventDto(eventTaskCSecondOccurrence),
        TracedEventDto.fromEventDto(eventTaskDSecondOccurrence),
        TracedEventDto.fromEventDto(eventTaskBThirdOccurrence),
        TracedEventDto.fromEventDto(eventTaskE)
      ))
    );
    assertThat(getAllStoredEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventTaskA, eventTaskB, 1),
      createSequenceFromSourceAndTargetEvents(eventTaskB, eventTaskC, 2),
      createSequenceFromSourceAndTargetEvents(eventTaskC, eventTaskD, 2),
      createSequenceFromSourceAndTargetEvents(eventTaskD, eventTaskB, 2),
      createSequenceFromSourceAndTargetEvents(eventTaskB, eventTaskE, 1),
      createSequenceFromSourceAndTargetEvents(eventTaskE, null, 1),
      createSequenceFromSourceAndTargetEvents(eventTaskB, null, 0),
      createSequenceFromSourceAndTargetEvents(eventTaskC, null, 0)
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
        embeddedOptimizeExtension.getConfigurationService().getEventIngestionConfiguration().getApiSecret()
      )
      .execute();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private EventDto createEventDtoWithProperties(String traceId, String eventId, String group,
                                                String source, String eventName, Long timestamp) {
    return createRandomEventDtoPropertiesBuilder()
      .id(eventId)
      .traceId(traceId)
      .group(group)
      .source(source)
      .eventName(eventName)
      .timestamp(timestamp)
      .build();
  }

  private EventDto.EventDtoBuilder createRandomEventDtoPropertiesBuilder() {
    return EventDto.builder()
      .id(IdGenerator.getNextId())
      .data(ImmutableMap.of(
        RandomStringUtils.randomAlphabetic(5), RANDOM.nextInt(),
        RandomStringUtils.randomAlphabetic(5), RANDOM.nextBoolean(),
        RandomStringUtils.randomAlphabetic(5), RandomStringUtils.randomAlphabetic(5)
      ));
  }

  private EventSequenceCountDto createSequenceFromSourceAndTargetEvents(EventDto sourceEventDto, EventDto targetEventDto, long count) {
    EventTypeDto sourceEvent = Optional.ofNullable(sourceEventDto)
      .map(source -> new EventTypeDto(source.getGroup(), source.getSource(), source.getEventName()))
      .orElse(null);
    EventTypeDto targetEvent = Optional.ofNullable(targetEventDto)
      .map(target -> new EventTypeDto(target.getGroup(), target.getSource(), target.getEventName()))
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
