/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventTraceStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.TracedEventDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventDto;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.reader.ElasticsearchHelper.mapHits;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_TRACE_STATE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

public class ExternalEventTraceStateImportIT extends AbstractIT {

  @BeforeEach
  public void init() {
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().setEnabled(true);
  }

  @Test
  public void noEventsToProcess() {
    // when
    processEventCountAndTraces();

    // then
    assertThat(
      elasticSearchIntegrationTestExtension
        .getDocumentCountOf(EVENT_TRACE_STATE_INDEX_PREFIX + EXTERNAL_EVENTS_INDEX_SUFFIX)
    ).isEqualTo(0);
    assertThat(
      elasticSearchIntegrationTestExtension
        .getDocumentCountOf(EVENT_SEQUENCE_COUNT_INDEX_PREFIX + EXTERNAL_EVENTS_INDEX_SUFFIX)
    ).isEqualTo(0);
  }

  @Test
  public void processSingleBatchOfEventsNewUniqueTraceIds() throws IOException {
    // given
    CloudEventDto eventDtoTraceOne = createCloudEventDtoWithProperties(
      "traceOne", "eventIdOne", "backend", "ketchup", "signup-event", 100L);
    CloudEventDto eventDtoTraceTwo = createCloudEventDtoWithProperties(
      "traceTwo", "eventIdTwo", "backend", "mayonnaise", "register-event", 200L);
    CloudEventDto eventDtoTraceThree = createCloudEventDtoWithProperties(
      "traceThree", "eventIdThree", null, "mayonnaise", "onboard-event", 300L
    );
    eventClient.ingestEventBatch(Arrays.asList(eventDtoTraceOne, eventDtoTraceTwo, eventDtoTraceThree));

    // when
    processEventCountAndTraces();

    // then trace states are stored and sequence counts reflect traces
    assertThat(getAllStoredExternalEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(
        eventDtoTraceOne.getTraceid(),
        Collections.singletonList(mapToTracedEventDto(eventDtoTraceOne))
      ),
      new EventTraceStateDto(
        eventDtoTraceTwo.getTraceid(),
        Collections.singletonList(mapToTracedEventDto(eventDtoTraceTwo))
      ),
      new EventTraceStateDto(
        eventDtoTraceThree.getTraceid(),
        Collections.singletonList(mapToTracedEventDto(eventDtoTraceThree))
      )
    );
    assertThat(getAllStoredExternalEventSequenceCounts()).containsExactlyInAnyOrder(
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
    CloudEventDto eventDtoOne = createCloudEventDtoWithProperties(
      traceId, "eventIdOne", "backend", "ketchup", "signup-event", 100L
    );
    CloudEventDto eventDtoTwo = createCloudEventDtoWithProperties(
      traceId, "eventIdTwo", "backend", "ketchup", "register-event", 200L
    );
    CloudEventDto eventDtoThree = createCloudEventDtoWithProperties(
      traceId, "eventIdThree", null, "mayonnaise", "onboard-event", 300L
    );
    eventClient.ingestEventBatch(Arrays.asList(eventDtoOne, eventDtoTwo, eventDtoThree));

    // when
    processEventCountAndTraces();

    // then trace state is stored in correct order and sequence counts reflect traces
    assertThat(getAllStoredExternalEventTraceStates())
      .containsExactly(
        new EventTraceStateDto(traceId, Arrays.asList(
          mapToTracedEventDto(eventDtoOne),
          mapToTracedEventDto(eventDtoTwo),
          mapToTracedEventDto(eventDtoThree)
        ))
      );
    assertThat(getAllStoredExternalEventSequenceCounts()).containsExactlyInAnyOrder(
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
    CloudEventDto eventDtoTraceOneEventOne = createCloudEventDtoWithProperties(
      traceIdOne, "eventIdOne", "backend", "ketchup", "signup-event", 100L
    );
    CloudEventDto eventDtoTraceTwoEventOne = createCloudEventDtoWithProperties(
      traceIdTwo, "eventIdTwo", "backend", "ketchup", "signup-event", 200L
    );
    CloudEventDto eventDtoTraceThreeEventOne = createCloudEventDtoWithProperties(
      traceIdThree, "eventIdThree", "backend", "ketchup", "signup-event", 300L
    );
    CloudEventDto eventDtoTraceOneEventTwo = createCloudEventDtoWithProperties(
      traceIdOne, "eventIdFour", "backend", "ketchup", "register-event", 400L
    );
    CloudEventDto eventDtoTraceTwoEventTwo = createCloudEventDtoWithProperties(
      traceIdTwo, "eventIdFive", "backend", "ketchup", "register-event", 500L
    );
    CloudEventDto eventDtoTraceThreeEventTwo = createCloudEventDtoWithProperties(
      traceIdThree, "eventIdSix", "backend", "ketchup", "onboarded-event", 600L
    );
    eventClient.ingestEventBatch(Arrays.asList(
      eventDtoTraceOneEventOne,
      eventDtoTraceTwoEventOne,
      eventDtoTraceThreeEventOne,
      eventDtoTraceOneEventTwo,
      eventDtoTraceTwoEventTwo,
      eventDtoTraceThreeEventTwo
    ));

    // when
    processEventCountAndTraces();

    // then trace state and sequence counts are correct
    assertThat(getAllStoredExternalEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceIdOne, Arrays.asList(
        mapToTracedEventDto(eventDtoTraceOneEventOne),
        mapToTracedEventDto(eventDtoTraceOneEventTwo)
      )),
      new EventTraceStateDto(traceIdTwo, Arrays.asList(
        mapToTracedEventDto(eventDtoTraceTwoEventOne),
        mapToTracedEventDto(eventDtoTraceTwoEventTwo)
      )),
      new EventTraceStateDto(traceIdThree, Arrays.asList(
        mapToTracedEventDto(eventDtoTraceThreeEventOne),
        mapToTracedEventDto(eventDtoTraceThreeEventTwo)
      ))
    );
    assertThat(getAllStoredExternalEventSequenceCounts()).containsExactlyInAnyOrder(
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
    CloudEventDto eventOneTraceOne = createCloudEventDtoWithProperties(
      traceIdOne, "eventIdOne", null, "ketchup", "signup-event", 100L);
    CloudEventDto eventOneTraceTwo = createCloudEventDtoWithProperties(
      traceIdTwo, "eventIdTwo", null, "ketchup", "signup-event", 200L);
    CloudEventDto eventOneTraceThree = createCloudEventDtoWithProperties(
      traceIdThree, "eventIdThree", null, "ketchup", "signup-event", 300L);
    eventClient.ingestEventBatch(Arrays.asList(eventOneTraceOne, eventOneTraceTwo, eventOneTraceThree));

    // when
    processEventCountAndTraces();

    // then trace state and sequence counts are correct after initial batch
    assertThat(getAllStoredExternalEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceIdOne, Collections.singletonList(mapToTracedEventDto(eventOneTraceOne))),
      new EventTraceStateDto(traceIdTwo, Collections.singletonList(mapToTracedEventDto(eventOneTraceTwo))),
      new EventTraceStateDto(traceIdThree, Collections.singletonList(mapToTracedEventDto(eventOneTraceThree)))
    );
    assertThat(getAllStoredExternalEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventOneTraceOne, null, 3)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());

    // when second batch adds further trace events
    CloudEventDto eventTwoTraceOne = createCloudEventDtoWithProperties(
      traceIdOne, "eventIdFour", "backend", "ketchup", "register-event", 400L);
    CloudEventDto eventTwoTraceTwo = createCloudEventDtoWithProperties(
      traceIdTwo, "eventIdFive", "backend", "ketchup", "register-event", 500L);
    CloudEventDto eventThreeTraceOne = createCloudEventDtoWithProperties(
      traceIdOne, "eventIdSix", "backend", "ketchup", "onboard-event", 600L);
    eventClient.ingestEventBatch(Arrays.asList(eventTwoTraceOne, eventTwoTraceTwo, eventThreeTraceOne));
    processEventCountAndTraces();

    // then trace state and sequence counts are correct after processing
    assertThat(getAllStoredExternalEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceIdOne, Arrays.asList(
        mapToTracedEventDto(eventOneTraceOne),
        mapToTracedEventDto(eventTwoTraceOne),
        mapToTracedEventDto(eventThreeTraceOne)
      )),
      new EventTraceStateDto(
        traceIdTwo,
        Arrays.asList(
          mapToTracedEventDto(eventOneTraceTwo),
          mapToTracedEventDto(eventTwoTraceTwo)
        )
      ),
      new EventTraceStateDto(traceIdThree, Collections.singletonList(mapToTracedEventDto(eventOneTraceThree)))
    );
    assertThat(getAllStoredExternalEventSequenceCounts()).containsExactlyInAnyOrder(
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
    CloudEventDto eventDtoOne = createCloudEventDtoWithProperties(
      traceId, "eventIdOne", "backend", "ketchup", "signup-event", 100L);
    CloudEventDto eventDtoTwo = createCloudEventDtoWithProperties(
      traceId, "eventIdTwo", null, "ketchup", "register-event", 200L);
    CloudEventDto eventDtoThree = createCloudEventDtoWithProperties(
      traceId, "eventIdThree", "backend", "ketchup", "onboarded-event", 300L);
    eventClient.ingestEventBatch(Arrays.asList(eventDtoOne, eventDtoTwo, eventDtoThree));

    // when
    processEventCountAndTraces();

    // then trace state and sequence counts are correct after initial batch
    assertThat(getAllStoredExternalEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceId, Arrays.asList(
        mapToTracedEventDto(eventDtoOne),
        mapToTracedEventDto(eventDtoTwo),
        mapToTracedEventDto(eventDtoThree)
      ))
    );
    assertThat(getAllStoredExternalEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventDtoOne, eventDtoTwo, 1),
      createSequenceFromSourceAndTargetEvents(eventDtoTwo, eventDtoThree, 1),
      createSequenceFromSourceAndTargetEvents(eventDtoThree, null, 1)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());

    // when second batch includes already ingested event with a new timestamp (a modification)
    CloudEventDto eventDtoThreeModified = createCloudEventDtoWithProperties(
      traceId,
      eventDtoThree.getId(),
      eventDtoThree.getGroup().orElse(null),
      eventDtoThree.getSource(),
      eventDtoThree.getType(),
      150L
    );

    eventClient.ingestEventBatch(Collections.singletonList(eventDtoThreeModified));
    processEventCountAndTraces();

    // then trace state and sequence counts are correct after modification
    assertThat(getAllStoredExternalEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceId, Arrays.asList(
        mapToTracedEventDto(eventDtoOne),
        mapToTracedEventDto(eventDtoThreeModified),
        mapToTracedEventDto(eventDtoTwo)
      ))
    );
    assertThat(getAllStoredExternalEventSequenceCounts()).containsExactlyInAnyOrder(
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
    CloudEventDto eventOne = createCloudEventDtoWithProperties(
      traceId, "eventIdOne", null, "ketchup", "signup-event", 100L);
    CloudEventDto eventTwo = createCloudEventDtoWithProperties(
      traceId, "eventIdTwo", "backend", "ketchup", "register-event", 200L);
    CloudEventDto eventThree = createCloudEventDtoWithProperties
      (traceId, "eventIdThree", "backend", "ketchup", "onboarded-event", 300L);
    eventClient.ingestEventBatch(Arrays.asList(eventOne, eventTwo, eventThree));

    // when
    processEventCountAndTraces();

    // then trace state and sequence counts are correct after initial batch
    assertThat(getAllStoredExternalEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceId, Arrays.asList(
        mapToTracedEventDto(eventOne),
        mapToTracedEventDto(eventTwo),
        mapToTracedEventDto(eventThree)
      ))
    );
    assertThat(getAllStoredExternalEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventOne, eventTwo, 1),
      createSequenceFromSourceAndTargetEvents(eventTwo, eventThree, 1),
      createSequenceFromSourceAndTargetEvents(eventThree, null, 1)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());

    // when second batch adds repeated event trace already processes
    CloudEventDto eventTwoRepeated = createCloudEventDtoWithProperties(
      traceId, eventTwo.getId(), eventTwo.getGroup().orElse(null), eventTwo.getSource(), eventTwo.getType(), 200L);

    eventClient.ingestEventBatch(Collections.singletonList(eventTwoRepeated));
    processEventCountAndTraces();

    // then trace state and sequence counts remain correct after processing
    assertThat(getAllStoredExternalEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceId, Arrays.asList(
        mapToTracedEventDto(eventOne),
        mapToTracedEventDto(eventTwo),
        mapToTracedEventDto(eventThree)
      ))
    );
    assertThat(getAllStoredExternalEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventOne, eventTwo, 1),
      createSequenceFromSourceAndTargetEvents(eventTwo, eventThree, 1),
      createSequenceFromSourceAndTargetEvents(eventThree, null, 1)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());
  }

  @Test
  public void processMultipleBatchesOfEventsWithSingleTraceAndMultipleOccurringEventsWithDifferentIds()
    throws IOException {
    // given
    String traceId = "traceIdOne";
    CloudEventDto eventTaskA = createCloudEventDtoWithProperties(
      traceId, "eventIdOne", null, "ketchup", "signup-event", 100L);
    CloudEventDto eventTaskB = createCloudEventDtoWithProperties(
      traceId, "eventIdTwo", "backend", "ketchup", "register-event", 200L);
    CloudEventDto eventTaskC = createCloudEventDtoWithProperties(
      traceId, "eventIdThree", "backend", "ketchup", "onboarded-event", 300L);
    eventClient.ingestEventBatch(Arrays.asList(eventTaskA, eventTaskB, eventTaskC));

    // when
    processEventCountAndTraces();

    // then trace state and sequence counts are correct after initial batch
    assertThat(getAllStoredExternalEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceId, Arrays.asList(
        mapToTracedEventDto(eventTaskA),
        mapToTracedEventDto(eventTaskB),
        mapToTracedEventDto(eventTaskC)
      ))
    );
    assertThat(getAllStoredExternalEventSequenceCounts()).containsExactlyInAnyOrder(
      createSequenceFromSourceAndTargetEvents(eventTaskA, eventTaskB, 1),
      createSequenceFromSourceAndTargetEvents(eventTaskB, eventTaskC, 1),
      createSequenceFromSourceAndTargetEvents(eventTaskC, null, 1)
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());

    // when second batch adds the second occurrence of an event with a new event ID, reflecting a loop in the
    // BPMN model
    CloudEventDto eventTaskD = createCloudEventDtoWithProperties(
      traceId, "eventIdFour", "backend", "ketchup", "complained-event", 400L);
    CloudEventDto eventTaskBSecondOccurrence = createCloudEventDtoWithProperties(
      traceId, "eventIdFive", eventTaskB.getGroup().orElse(null), eventTaskB.getSource(), eventTaskB.getType(), 500L
    );

    eventClient.ingestEventBatch(Arrays.asList(eventTaskD, eventTaskBSecondOccurrence));
    processEventCountAndTraces();

    // then trace state and sequence counts remain correct after processing and loop is correctly traced
    assertThat(getAllStoredExternalEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceId, Arrays.asList(
        mapToTracedEventDto(eventTaskA),
        mapToTracedEventDto(eventTaskB),
        mapToTracedEventDto(eventTaskC),
        mapToTracedEventDto(eventTaskD),
        mapToTracedEventDto(eventTaskBSecondOccurrence)
      ))
    );
    assertThat(getAllStoredExternalEventSequenceCounts()).containsExactlyInAnyOrder(
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
    CloudEventDto eventTaskCSecondOccurrence = createCloudEventDtoWithProperties(
      traceId, "eventIdSix", eventTaskC.getGroup().orElse(null), eventTaskC.getSource(), eventTaskC.getType(), 600L);
    CloudEventDto eventTaskDSecondOccurrence = createCloudEventDtoWithProperties(
      traceId, "eventIdSeven", eventTaskD.getGroup().orElse(null), eventTaskD.getSource(), eventTaskD.getType(), 700L);
    CloudEventDto eventTaskBThirdOccurrence = createCloudEventDtoWithProperties(
      traceId, "eventIdEight", eventTaskB.getGroup().orElse(null), eventTaskB.getSource(), eventTaskB.getType(), 800L);
    CloudEventDto eventTaskE = createCloudEventDtoWithProperties
      (traceId, "eventIdNine", "backend", "ketchup", "helped-event", 900L);

    eventClient.ingestEventBatch(Arrays.asList(
      eventTaskCSecondOccurrence,
      eventTaskDSecondOccurrence,
      eventTaskBThirdOccurrence,
      eventTaskE
    ));
    processEventCountAndTraces();

    // then trace state and sequence counts remain correct after processing and loop is correct traced
    assertThat(getAllStoredExternalEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceId, Arrays.asList(
        mapToTracedEventDto(eventTaskA),
        mapToTracedEventDto(eventTaskB),
        mapToTracedEventDto(eventTaskC),
        mapToTracedEventDto(eventTaskD),
        mapToTracedEventDto(eventTaskBSecondOccurrence),
        mapToTracedEventDto(eventTaskCSecondOccurrence),
        mapToTracedEventDto(eventTaskDSecondOccurrence),
        mapToTracedEventDto(eventTaskBThirdOccurrence),
        mapToTracedEventDto(eventTaskE)
      ))
    );
    assertThat(getAllStoredExternalEventSequenceCounts()).containsExactlyInAnyOrder(
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

  private TracedEventDto mapToTracedEventDto(final CloudEventDto cloudEventDto) {
    return TracedEventDto.fromEventDto(mapToEventDto(cloudEventDto));
  }

  private Long getLastProcessedEntityTimestampFromElasticsearch() throws IOException {
    return elasticSearchIntegrationTestExtension
      .getLastProcessedEventTimestampForEventIndexSuffix(EXTERNAL_EVENTS_INDEX_SUFFIX)
      .toInstant()
      .toEpochMilli();
  }

  private Long findMostRecentEventTimestamp() {
    return getAllStoredExternalEvents().stream()
      .map(EventDto::getIngestionTimestamp)
      .mapToLong(e -> e).max().getAsLong();
  }

  private CloudEventDto createCloudEventDtoWithProperties(String traceId, String eventId, String group,
                                                          String source, String eventName, Long timestamp) {
    return eventClient.createCloudEventDto()
      .toBuilder()
      .id(eventId)
      .traceid(traceId)
      .group(group)
      .source(source)
      .type(eventName)
      .time(Instant.ofEpochMilli(timestamp))
      .build();
  }

  private EventDto mapToEventDto(final CloudEventDto cloudEventDto) {
    return EventDto.builder()
      .id(cloudEventDto.getId())
      .eventName(cloudEventDto.getType())
      .timestamp(
        cloudEventDto.getTime()
          .map(Instant::toEpochMilli)
          .orElse(Instant.now().toEpochMilli())
      )
      .traceId(cloudEventDto.getTraceid())
      .group(cloudEventDto.getGroup().orElse(null))
      .source(cloudEventDto.getSource())
      .data(cloudEventDto.getData())
      .build();
  }

  private EventSequenceCountDto createSequenceFromSourceAndTargetEvents(CloudEventDto sourceEventDto,
                                                                        CloudEventDto targetEventDto, long count) {
    EventTypeDto sourceEvent = Optional.ofNullable(sourceEventDto)
      .map(source -> new EventTypeDto(source.getGroup().orElse(null), source.getSource(), source.getType()))
      .orElse(null);
    EventTypeDto targetEvent = Optional.ofNullable(targetEventDto)
      .map(target -> new EventTypeDto(target.getGroup().orElse(null), target.getSource(), target.getType()))
      .orElse(null);
    EventSequenceCountDto eventSequenceCountDto = EventSequenceCountDto.builder()
      .sourceEvent(sourceEvent)
      .targetEvent(targetEvent)
      .count(count)
      .build();
    eventSequenceCountDto.generateIdForEventSequenceCountDto();
    return eventSequenceCountDto;
  }

  private void processEventCountAndTraces() {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    embeddedOptimizeExtension.processEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  public List<EventDto> getAllStoredExternalEvents() {
    return elasticSearchIntegrationTestExtension.getAllStoredExternalEvents();
  }

  public List<EventTraceStateDto> getAllStoredExternalEventTraceStates() {
    return getAllStoredDocumentsForIndexAsClass(
      EVENT_TRACE_STATE_INDEX_PREFIX + EXTERNAL_EVENTS_INDEX_SUFFIX,
      EventTraceStateDto.class
    );
  }

  public List<EventSequenceCountDto> getAllStoredExternalEventSequenceCounts() {
    return getAllStoredDocumentsForIndexAsClass(
      EVENT_SEQUENCE_COUNT_INDEX_PREFIX + EXTERNAL_EVENTS_INDEX_SUFFIX,
      EventSequenceCountDto.class
    );
  }

  private <T> List<T> getAllStoredDocumentsForIndexAsClass(String indexName, Class<T> dtoClass) {
    SearchResponse response = elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(indexName);
    return mapHits(response.getHits(), dtoClass, elasticSearchIntegrationTestExtension.getObjectMapper());
  }

}
