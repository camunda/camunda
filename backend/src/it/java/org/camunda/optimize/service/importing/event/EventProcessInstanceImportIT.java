/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.SimpleEventDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;

public class EventProcessInstanceImportIT extends AbstractEventProcessIT {

  @Test
  public void dedicatedInstanceIndexIsCreatedForPublishedEventProcess() {
    // given
    final String eventProcessMappingId = createSimpleEventProcessMapping("whatever", "huh");

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    executeImportCycle();

    // then
    final String eventProcessPublishStateId = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId);

    final Map<String, List<AliasMetaData>> eventProcessInstanceIndicesAndAliases =
      getEventProcessInstanceIndicesWithAliasesFromElasticsearch();
    assertThat(eventProcessInstanceIndicesAndAliases)
      .hasSize(1)
      .hasEntrySatisfying(
        getVersionedEventProcessInstanceIndexNameForPublishedStateId(eventProcessPublishStateId),
        aliases -> assertThat(aliases)
          .extracting(AliasMetaData::alias, AliasMetaData::writeIndex)
          .containsExactlyInAnyOrder(
            Tuple.tuple(
              getOptimizeIndexAliasForIndexName(EVENT_PROCESS_INSTANCE_INDEX_PREFIX + eventProcessPublishStateId), true
            ),
            Tuple.tuple(
              getOptimizeIndexAliasForIndexName(ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME), false
            )
          )
      );
  }

  @Test
  public void dedicatedInstanceIndexIsDeletedOnCancelPublish() {
    // given
    final String eventProcessId = createSimpleEventProcessMapping("whatever", "huh");

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    eventProcessClient.cancelPublishEventProcessMapping(eventProcessId);

    executeImportCycle();

    // then
    final Map<String, List<AliasMetaData>> eventProcessInstanceIndicesAndAliases =
      getEventProcessInstanceIndicesWithAliasesFromElasticsearch();
    assertThat(eventProcessInstanceIndicesAndAliases).hasSize(0);
  }

  @Test
  public void instancesAreGeneratedForExistingEventsAfterPublish() {
    // given
    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(60));
    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventName = "startedEvent";
    final String ingestedStartEventId = ingestTestEvent(ingestedStartEventName, startDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final OffsetDateTime endDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedEndEventName = "finishedEvent";
    final String ingestedEndEventId = ingestTestEvent(ingestedEndEventName, endDateTime);

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedEventProcessInstanceForTraceId(eventProcessId, startDateTime, endDateTime),
              ProcessInstanceDto.Fields.events
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    SimpleEventDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .build(),
                    SimpleEventDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(startDateTime)
                      .endDate(endDateTime)
                      .durationInMs(30000L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .build()
                  )
            );

        }
      );
  }

  @Test
  public void instancesAreGeneratedForExistingEventsAfterPublish_otherEventsHaveNoEffect() {
    // given
    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(60));
    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventName = "startedEvent";
    final String ingestedStartEventId = ingestTestEvent(ingestedStartEventName, startDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final OffsetDateTime endDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedEndEventName = "finishedEvent";
    final String ingestedEndEventId = ingestTestEvent(ingestedEndEventName, endDateTime);

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    ingestTestEvent("randomOtherEvent", LocalDateUtil.getCurrentDateTime());
    ingestTestEvent("evenAnotherEvent", LocalDateUtil.getCurrentDateTime());

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedEventProcessInstanceForTraceId(eventProcessId, startDateTime, endDateTime),
              ProcessInstanceDto.Fields.events
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    SimpleEventDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .build(),
                    SimpleEventDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(startDateTime)
                      .endDate(endDateTime)
                      .durationInMs(30_000L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .build()
                  )
            );

        }
      );
  }

  @Test
  public void instancesAreGeneratedWhenEventsAreIngestedAfterPublish() {
    // given
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final String ingestedStartEventName = "startedEvent";
    final String ingestedEndEventName = "finishedEvent";

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(60));
    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventId = ingestTestEvent(ingestedStartEventName, startDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final OffsetDateTime endDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedEndEventId = ingestTestEvent(ingestedEndEventName, endDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.plusSeconds(30));
    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedEventProcessInstanceForTraceId(eventProcessId, startDateTime, endDateTime),
              ProcessInstanceDto.Fields.events
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    SimpleEventDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .build(),
                    SimpleEventDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(startDateTime)
                      .endDate(endDateTime)
                      .durationInMs(30_000L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .build()
                  )
            );
        }
      );
  }

  @Test
  public void newEventsWithSameIdUpdateActivityInstances() {
    // given
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final String ingestedStartEventName = "startedEvent";
    final String ingestedEndEventName = "finishedEvent";

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessId);
    executeImportCycle();

    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(60));
    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventId = ingestTestEvent(ingestedStartEventName, startDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final String ingestedEndEventId = ingestTestEvent(ingestedEndEventName, startDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(10));
    final OffsetDateTime updatedEndDateTime = LocalDateUtil.getCurrentDateTime();
    ingestTestEvent(ingestedEndEventId, ingestedEndEventName, updatedEndDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.plusSeconds(30));
    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedEventProcessInstanceForTraceId(eventProcessId, startDateTime, updatedEndDateTime),
              ProcessInstanceDto.Fields.events
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    SimpleEventDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .build(),
                    SimpleEventDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(startDateTime)
                      .endDate(updatedEndDateTime)
                      .durationInMs(50_000L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .build()
                  )
            );
        }
      );
  }

  @Test
  public void instanceIsRunningIfNoEndEventYetIngested() {
    // given

    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(60));
    final String ingestedStartEventName = "startedEvent";
    final String ingestedEndEventName = "finishedEvent";

    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventId = ingestTestEvent(ingestedStartEventName, startDateTime);

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              ProcessInstanceDto.builder()
                .processDefinitionId(eventProcessId)
                .processDefinitionKey(eventProcessId)
                .processDefinitionVersion("1")
                .processInstanceId(MY_TRACE_ID_1)
                .duration(null)
                .startDate(startDateTime)
                .endDate(null)
                .state(PROCESS_INSTANCE_STATE_ACTIVE)
                .variables(Collections.singletonList(
                  SimpleProcessVariableDto.builder()
                    .id(VARIABLE_ID)
                    .name(VARIABLE_ID)
                    .value(VARIABLE_VALUE)
                    .version(1L)
                    .build()
                ))
                .build(),
              ProcessInstanceDto.Fields.events, ProcessInstanceDto.Fields.userTasks
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    SimpleEventDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .build()
                  )
            );
        }
      );
  }

  @Test
  public void instanceIsCompletedOnceEndEventGotIngested() {
    // given
    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(60));
    final String ingestedStartEventName = "startedEvent";
    final String ingestedEndEventName = "finishedEvent";

    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventId = ingestTestEvent(ingestedStartEventName, startDateTime);

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final OffsetDateTime endDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedEndEventId = ingestTestEvent(ingestedEndEventName, endDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.plusSeconds(30));
    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedEventProcessInstanceForTraceId(eventProcessId, startDateTime, endDateTime),
              ProcessInstanceDto.Fields.events
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    SimpleEventDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .build(),
                    SimpleEventDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(startDateTime)
                      .endDate(endDateTime)
                      .durationInMs(30_000L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .build()
                  )
            );
        }
      );
  }

  @Test
  public void instanceIsCompletedEvenIfOnlyEndEventGotIngested() {
    // given
    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    final String ingestedStartEventName = "startedEvent";
    final String ingestedEndEventName = "finishedEvent";

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final OffsetDateTime endDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedEndEventId = ingestTestEvent(ingestedEndEventName, endDateTime);

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedEventProcessInstanceForTraceId(eventProcessId, null, endDateTime),
              ProcessInstanceDto.Fields.events
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    SimpleEventDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(null)
                      .endDate(endDateTime)
                      .durationInMs(null)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .build()
                  )
            );
        }
      );
  }

  private EventProcessInstanceDto createExpectedCompletedEventProcessInstanceForTraceId(final String eventProcessId,
                                                                                        final OffsetDateTime startDateTime,
                                                                                        final OffsetDateTime endDateTime) {
    Long duration = null;
    if (startDateTime != null && endDateTime != null) {
      duration = startDateTime.until(endDateTime, ChronoUnit.MILLIS);
    }
    return EventProcessInstanceDto.eventProcessInstanceBuilder()
      .processDefinitionId(eventProcessId)
      .processDefinitionKey(eventProcessId)
      .processDefinitionVersion("1")
      .processInstanceId(MY_TRACE_ID_1)
      .duration(duration)
      .startDate(startDateTime)
      .endDate(endDateTime)
      .state(PROCESS_INSTANCE_STATE_COMPLETED)
      .variables(Collections.singletonList(
        SimpleProcessVariableDto.builder()
          .id(VARIABLE_ID)
          .name(VARIABLE_ID)
          .value(VARIABLE_VALUE)
          .version(1L)
          .build()
      ))
      .build();
  }

  private String getVersionedEventProcessInstanceIndexNameForPublishedStateId(final String eventProcessPublishStateId) {
    return elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .getIndexNameService()
      .getVersionedOptimizeIndexNameForIndexMapping(new EventProcessInstanceIndex(eventProcessPublishStateId));
  }

  private String getOptimizeIndexAliasForIndexName(final String indexName) {
    return elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .getIndexNameService()
      .getOptimizeIndexAliasForIndex(indexName);
  }

}
