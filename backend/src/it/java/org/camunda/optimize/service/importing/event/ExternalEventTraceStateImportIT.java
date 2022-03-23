/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.event;

import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.TracedEventDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

public class ExternalEventTraceStateImportIT extends AbstractEventTraceStateImportIT {

  @Test
  public void noEventsToProcess() {
    // when
    processEventCountAndTraces();

    // then
    assertThat(
      elasticSearchIntegrationTestExtension.getDocumentCountOf(new EventTraceStateIndex(EXTERNAL_EVENTS_INDEX_SUFFIX).getIndexName()))
      .isZero();
    assertThat(
      elasticSearchIntegrationTestExtension
        .getDocumentCountOf(new EventSequenceCountIndex(EXTERNAL_EVENTS_INDEX_SUFFIX).getIndexName())
    ).isZero();
  }

  @Test
  public void processSingleBatchOfEventsNewUniqueTraceIds() throws IOException {
    // given
    CloudEventRequestDto eventDtoTraceOne = createCloudEventDtoWithProperties(
      "traceOne", "eventIdOne", "backend", "ketchup", "signup-event", 100L);
    CloudEventRequestDto eventDtoTraceTwo = createCloudEventDtoWithProperties(
      "traceTwo", "eventIdTwo", "backend", "mayonnaise", "register-event", 200L);
    CloudEventRequestDto eventDtoTraceThree = createCloudEventDtoWithProperties(
      "traceThree", "eventIdThree", null, "mayonnaise", "onboard-event", 300L
    );
    ingestionClient.ingestEventBatch(Arrays.asList(eventDtoTraceOne, eventDtoTraceTwo, eventDtoTraceThree));

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
    CloudEventRequestDto eventDtoOne = createCloudEventDtoWithProperties(
      traceId, "eventIdOne", "backend", "ketchup", "signup-event", 100L
    );
    CloudEventRequestDto eventDtoTwo = createCloudEventDtoWithProperties(
      traceId, "eventIdTwo", "backend", "ketchup", "register-event", 200L
    );
    CloudEventRequestDto eventDtoThree = createCloudEventDtoWithProperties(
      traceId, "eventIdThree", null, "mayonnaise", "onboard-event", 300L
    );
    ingestionClient.ingestEventBatch(Arrays.asList(eventDtoOne, eventDtoTwo, eventDtoThree));

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
    CloudEventRequestDto eventDtoTraceOneEventOne = createCloudEventDtoWithProperties(
      traceIdOne, "eventIdOne", "backend", "ketchup", "signup-event", 100L
    );
    CloudEventRequestDto eventDtoTraceTwoEventOne = createCloudEventDtoWithProperties(
      traceIdTwo, "eventIdTwo", "backend", "ketchup", "signup-event", 200L
    );
    CloudEventRequestDto eventDtoTraceThreeEventOne = createCloudEventDtoWithProperties(
      traceIdThree, "eventIdThree", "backend", "ketchup", "signup-event", 300L
    );
    CloudEventRequestDto eventDtoTraceOneEventTwo = createCloudEventDtoWithProperties(
      traceIdOne, "eventIdFour", "backend", "ketchup", "register-event", 400L
    );
    CloudEventRequestDto eventDtoTraceTwoEventTwo = createCloudEventDtoWithProperties(
      traceIdTwo, "eventIdFive", "backend", "ketchup", "register-event", 500L
    );
    CloudEventRequestDto eventDtoTraceThreeEventTwo = createCloudEventDtoWithProperties(
      traceIdThree, "eventIdSix", "backend", "ketchup", "onboarded-event", 600L
    );
    ingestionClient.ingestEventBatch(Arrays.asList(
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
    CloudEventRequestDto eventOneTraceOne = createCloudEventDtoWithProperties(
      traceIdOne, "eventIdOne", null, "ketchup", "signup-event", 100L);
    CloudEventRequestDto eventOneTraceTwo = createCloudEventDtoWithProperties(
      traceIdTwo, "eventIdTwo", null, "ketchup", "signup-event", 200L);
    CloudEventRequestDto eventOneTraceThree = createCloudEventDtoWithProperties(
      traceIdThree, "eventIdThree", null, "ketchup", "signup-event", 300L);
    ingestionClient.ingestEventBatch(Arrays.asList(eventOneTraceOne, eventOneTraceTwo, eventOneTraceThree));

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
    CloudEventRequestDto eventTwoTraceOne = createCloudEventDtoWithProperties(
      traceIdOne, "eventIdFour", "backend", "ketchup", "register-event", 400L);
    CloudEventRequestDto eventTwoTraceTwo = createCloudEventDtoWithProperties(
      traceIdTwo, "eventIdFive", "backend", "ketchup", "register-event", 500L);
    CloudEventRequestDto eventThreeTraceOne = createCloudEventDtoWithProperties(
      traceIdOne, "eventIdSix", "backend", "ketchup", "onboard-event", 600L);
    ingestionClient.ingestEventBatch(Arrays.asList(eventTwoTraceOne, eventTwoTraceTwo, eventThreeTraceOne));
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
    CloudEventRequestDto eventDtoOne = createCloudEventDtoWithProperties(
      traceId, "eventIdOne", "backend", "ketchup", "signup-event", 100L);
    CloudEventRequestDto eventDtoTwo = createCloudEventDtoWithProperties(
      traceId, "eventIdTwo", null, "ketchup", "register-event", 200L);
    CloudEventRequestDto eventDtoThree = createCloudEventDtoWithProperties(
      traceId, "eventIdThree", "backend", "ketchup", "onboarded-event", 300L);
    ingestionClient.ingestEventBatch(Arrays.asList(eventDtoOne, eventDtoTwo, eventDtoThree));

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
    CloudEventRequestDto eventDtoThreeModified = createCloudEventDtoWithProperties(
      traceId,
      eventDtoThree.getId(),
      eventDtoThree.getGroup().orElse(null),
      eventDtoThree.getSource(),
      eventDtoThree.getType(),
      150L
    );

    ingestionClient.ingestEventBatch(Collections.singletonList(eventDtoThreeModified));
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
    CloudEventRequestDto eventOne = createCloudEventDtoWithProperties(
      traceId, "eventIdOne", null, "ketchup", "signup-event", 100L);
    CloudEventRequestDto eventTwo = createCloudEventDtoWithProperties(
      traceId, "eventIdTwo", "backend", "ketchup", "register-event", 200L);
    CloudEventRequestDto eventThree = createCloudEventDtoWithProperties
      (traceId, "eventIdThree", "backend", "ketchup", "onboarded-event", 300L);
    ingestionClient.ingestEventBatch(Arrays.asList(eventOne, eventTwo, eventThree));

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
    CloudEventRequestDto eventTwoRepeated = createCloudEventDtoWithProperties(
      traceId, eventTwo.getId(), eventTwo.getGroup().orElse(null), eventTwo.getSource(), eventTwo.getType(), 200L);

    ingestionClient.ingestEventBatch(Collections.singletonList(eventTwoRepeated));
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
    CloudEventRequestDto eventTaskA = createCloudEventDtoWithProperties(
      traceId, "eventIdOne", null, "ketchup", "signup-event", 100L);
    CloudEventRequestDto eventTaskB = createCloudEventDtoWithProperties(
      traceId, "eventIdTwo", "backend", "ketchup", "register-event", 200L);
    CloudEventRequestDto eventTaskC = createCloudEventDtoWithProperties(
      traceId, "eventIdThree", "backend", "ketchup", "onboarded-event", 300L);
    ingestionClient.ingestEventBatch(Arrays.asList(eventTaskA, eventTaskB, eventTaskC));

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
    CloudEventRequestDto eventTaskD = createCloudEventDtoWithProperties(
      traceId, "eventIdFour", "backend", "ketchup", "complained-event", 400L);
    CloudEventRequestDto eventTaskBSecondOccurrence = createCloudEventDtoWithProperties(
      traceId, "eventIdFive", eventTaskB.getGroup().orElse(null), eventTaskB.getSource(), eventTaskB.getType(), 500L
    );

    ingestionClient.ingestEventBatch(Arrays.asList(eventTaskD, eventTaskBSecondOccurrence));
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
    CloudEventRequestDto eventTaskCSecondOccurrence = createCloudEventDtoWithProperties(
      traceId, "eventIdSix", eventTaskC.getGroup().orElse(null), eventTaskC.getSource(), eventTaskC.getType(), 600L);
    CloudEventRequestDto eventTaskDSecondOccurrence = createCloudEventDtoWithProperties(
      traceId, "eventIdSeven", eventTaskD.getGroup().orElse(null), eventTaskD.getSource(), eventTaskD.getType(), 700L);
    CloudEventRequestDto eventTaskBThirdOccurrence = createCloudEventDtoWithProperties(
      traceId, "eventIdEight", eventTaskB.getGroup().orElse(null), eventTaskB.getSource(), eventTaskB.getType(), 800L);
    CloudEventRequestDto eventTaskE = createCloudEventDtoWithProperties
      (traceId, "eventIdNine", "backend", "ketchup", "helped-event", 900L);

    ingestionClient.ingestEventBatch(Arrays.asList(
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

  @Test
  public void processMultipleBatchOfEventsAcrossMultipleTraces_sameTimestampEventsGetOrderedCorrectly() throws
                                                                                                        IOException {
    // given
    String traceIdOne = "traceIdOne";
    String traceIdTwo = "traceIdTwo";
    CloudEventRequestDto eventDtoTraceOneEventOne = createCloudEventDtoWithProperties(
      traceIdOne, "eventIdOne", "backend", "ketchup", "first-event", 100L
    );
    CloudEventRequestDto eventDtoTraceOneEventTwo = createCloudEventDtoWithProperties(
      traceIdOne, "eventIdTwo", "backend", "ketchup", "second-event", 200L
    );
    CloudEventRequestDto eventDtoTraceOneEventThree = createCloudEventDtoWithProperties(
      traceIdOne, "eventIdThree", "backend", "ketchup", "third-event", 300L
    );
    ingestionClient.ingestEventBatch(Arrays.asList(
      eventDtoTraceOneEventOne,
      eventDtoTraceOneEventTwo,
      eventDtoTraceOneEventThree
    ));

    // when
    processEventCountAndTraces();

    // then trace state and sequence counts are correct after first batch
    assertThat(getAllStoredExternalEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceIdOne, Arrays.asList(
        mapToTracedEventDto(eventDtoTraceOneEventOne),
        mapToTracedEventDto(eventDtoTraceOneEventTwo),
        mapToTracedEventDto(eventDtoTraceOneEventThree)
      ))
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());

    // when events ingested with identical timestamp events for new trace
    CloudEventRequestDto eventDtoTraceTwoEventOne = createCloudEventDtoWithProperties(
      traceIdTwo, "eventIdFour", "backend", "ketchup", "first-event", 100L
    );
    CloudEventRequestDto eventDtoTraceTwoEventTwo = createCloudEventDtoWithProperties(
      traceIdTwo, "eventIdFive", "backend", "ketchup", "second-event", 100L
    );
    CloudEventRequestDto eventDtoTraceTwoEventThree = createCloudEventDtoWithProperties(
      traceIdTwo, "eventIdSix", "backend", "ketchup", "third-event", 200L
    );
    ingestionClient.ingestEventBatch(Arrays.asList(
      eventDtoTraceTwoEventThree,
      eventDtoTraceTwoEventOne,
      eventDtoTraceTwoEventTwo
    ));
    processEventCountAndTraces();

    // then trace state and sequence counts are correct and identical timestamp order is resolved correctly
    assertThat(getAllStoredExternalEventTraceStates()).containsExactlyInAnyOrder(
      new EventTraceStateDto(traceIdOne, Arrays.asList(
        mapToTracedEventDto(eventDtoTraceOneEventOne),
        mapToTracedEventDto(eventDtoTraceOneEventTwo),
        mapToTracedEventDto(eventDtoTraceOneEventThree)
      )),
      new EventTraceStateDto(traceIdTwo, Arrays.asList(
        mapToTracedEventDto(eventDtoTraceTwoEventOne),
        mapToTracedEventDto(eventDtoTraceTwoEventTwo),
        mapToTracedEventDto(eventDtoTraceTwoEventThree)
      ))
    );
    assertThat(getLastProcessedEntityTimestampFromElasticsearch()).isEqualTo(findMostRecentEventTimestamp());
  }

  private TracedEventDto mapToTracedEventDto(final CloudEventRequestDto cloudEventDto) {
    return TracedEventDto.fromEventDto(mapToEventDto(cloudEventDto));
  }

  private Long getLastProcessedEntityTimestampFromElasticsearch() throws IOException {
    return getLastProcessedEntityTimestampFromElasticsearch(EXTERNAL_EVENTS_INDEX_SUFFIX);
  }

  private Long findMostRecentEventTimestamp() {
    return getAllStoredExternalEvents().stream()
      .map(EventDto::getIngestionTimestamp)
      .mapToLong(e -> e).max().getAsLong();
  }

  private CloudEventRequestDto createCloudEventDtoWithProperties(String traceId, String eventId, String group,
                                                                 String source, String eventName, Long timestamp) {
    return ingestionClient.createCloudEventDto()
      .toBuilder()
      .id(eventId)
      .traceid(traceId)
      .group(group)
      .source(source)
      .type(eventName)
      .time(Instant.ofEpochMilli(timestamp))
      .build();
  }

  private EventDto mapToEventDto(final CloudEventRequestDto cloudEventDto) {
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

  private EventSequenceCountDto createSequenceFromSourceAndTargetEvents(CloudEventRequestDto sourceEventDto,
                                                                        CloudEventRequestDto targetEventDto,
                                                                        long count) {
    EventTypeDto sourceEvent = Optional.ofNullable(sourceEventDto)
      .map(source -> EventTypeDto.builder()
        .eventName(source.getType())
        .source(source.getSource())
        .group(source.getGroup().orElse(null))
        .build())
      .orElse(null);
    EventTypeDto targetEvent = Optional.ofNullable(targetEventDto)
      .map(target -> EventTypeDto.builder()
        .eventName(target.getType())
        .source(target.getSource())
        .group(target.getGroup().orElse(null))
        .build())
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
