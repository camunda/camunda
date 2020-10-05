/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.optimize.EventProcessClient;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaTaskStartEventSuffix;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;

public class EventProcessInstanceImportIT extends AbstractEventProcessIT {

  @Test
  public void dedicatedInstanceIndexIsCreatedForPublishedEventProcess() {
    // given
    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

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
              getOptimizeIndexAliasForIndexName(new EventProcessInstanceIndex(eventProcessPublishStateId).getIndexName()),
              true
            ),
            Tuple.tuple(
              getOptimizeIndexAliasForIndexName(ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME), false
            )
          )
      );
  }

  @Test
  public void dedicatedInstanceIndexesAreCreatedForMultipleEventProcesses() {
    // given
    final String eventProcessMappingId1 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);
    final String eventProcessMappingId2 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId1);
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId2);

    executeImportCycle();

    // then
    final String eventProcessPublishStateId1 = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId1);
    final String eventProcessPublishStateId2 = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId2);

    assertThat(getEventProcessInstanceIndicesWithAliasesFromElasticsearch())
      .hasSize(2)
      .containsKeys(
        getVersionedEventProcessInstanceIndexNameForPublishedStateId(eventProcessPublishStateId1),
        getVersionedEventProcessInstanceIndexNameForPublishedStateId(eventProcessPublishStateId2)
      );
  }

  @ParameterizedTest(name = "Instance index is deleted on {0}.")
  @MethodSource("cancelOrDeleteAction")
  public void dedicatedInstanceIndexIsDeletedOn(final String actionName,
                                                final BiConsumer<EventProcessClient, String> action) {
    // given
    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    executeImportCycle();

    action.accept(eventProcessClient, eventProcessMappingId);

    executeImportCycle();

    // then
    final boolean eventProcessInstanceIndicesExist = eventProcessInstanceIndicesExist();
    assertThat(eventProcessInstanceIndicesExist).isFalse();
  }

  @ParameterizedTest(name = "Only expected instance index is deleted on {0}, other is still present.")
  @MethodSource("cancelOrDeleteAction")
  public void dedicatedInstanceIndexIsDeletedOtherInstanceIndexNotAffected(
    final String actionName,
    final BiConsumer<EventProcessClient, String> action) {
    // given
    final String eventProcessMappingId1 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);
    final String eventProcessMappingId2 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId1);
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId2);
    final String eventProcessPublishStateId2 = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId2);

    executeImportCycle();

    action.accept(eventProcessClient, eventProcessMappingId1);

    executeImportCycle();

    // then
    assertThat(getEventProcessInstanceIndicesWithAliasesFromElasticsearch())
      .containsOnlyKeys(getVersionedEventProcessInstanceIndexNameForPublishedStateId(eventProcessPublishStateId2));
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

    final String eventProcessMappingId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedEventProcessInstanceForTraceId(eventProcessMappingId, startDateTime, endDateTime),
              ProcessInstanceDto.Fields.events,
              EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates,
              EventProcessInstanceDto.Fields.correlatedEventsById
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    FlowNodeInstanceDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .processInstanceId(processInstanceDto.getProcessInstanceId())
                      .build(),
                    FlowNodeInstanceDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(endDateTime)
                      .endDate(endDateTime)
                      .durationInMs(0L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .processInstanceId(processInstanceDto.getProcessInstanceId())
                      .build()
                  )
            );
        }
      );
  }

  @Test
  public void instancesAreGeneratedForMultipleEventProcesses() {
    // given
    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(FINISHED_EVENT);

    final String eventProcessMappingId1 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);
    final String eventProcessMappingId2 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId1);
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId2);

    executeImportCycle();

    // then
    final String eventProcessPublishStateId1 = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId1);
    final String eventProcessPublishStateId2 = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId2);

    final List<EventProcessInstanceDto> eventProcess1ProcessInstances =
      getEventProcessInstancesFromElasticsearchForProcessMappingId(eventProcessPublishStateId1);
    final List<EventProcessInstanceDto> eventProcess2ProcessInstances =
      getEventProcessInstancesFromElasticsearchForProcessMappingId(eventProcessPublishStateId2);

    assertThat(eventProcess1ProcessInstances).hasSize(1);
    assertThat(eventProcess2ProcessInstances).hasSize(1);
  }

  @Test
  public void canceledFlowNodesAreCorrelatedToInstanceCorrectlyOnEventProcessInstancePublish() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
    engineIntegrationExtension.cancelActivityInstance(processInstanceEngineDto.getId(), USER_TASK_ID_ONE);
    importEngineEntities();
    publishEventMappingUsingProcessInstanceCamundaEvents(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(USER_TASK_ID_ONE),
        BPMN_END_EVENT_ID
      )
    );
    importEngineEntities();

    // when
    executeImportCycle();

    // then
    assertThat(getEventProcessInstancesFromElasticsearch())
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getEvents())
          .extracting(FlowNodeInstanceDto::getActivityId, FlowNodeInstanceDto::getCanceled)
          .containsExactlyInAnyOrder(
            Tuple.tuple(BPMN_START_EVENT_ID, false),
            Tuple.tuple(USER_TASK_ID_ONE, true)
          );
      });
  }

  @Test
  public void canceledFlowNodesUpdateAlreadyPublishedInstanceFlowNodes() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
    importEngineEntities();
    publishEventMappingUsingProcessInstanceCamundaEvents(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(USER_TASK_ID_ONE),
        BPMN_END_EVENT_ID
      )
    );
    importEngineEntities();

    // when
    executeImportCycle();

    // then all flow nodes for the process are not canceled
    assertThat(getEventProcessInstancesFromElasticsearch())
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getEvents())
          .extracting(FlowNodeInstanceDto::getActivityId, FlowNodeInstanceDto::getCanceled)
          .containsExactlyInAnyOrder(
            Tuple.tuple(BPMN_START_EVENT_ID, false),
            Tuple.tuple(USER_TASK_ID_ONE, false)
          );
      });

    // when we cancel the running user task and reimport the activity
    engineIntegrationExtension.cancelActivityInstance(processInstanceEngineDto.getId(), USER_TASK_ID_ONE);
    importEngineEntities();
    executeImportCycle();

    // then the user task is marked as canceled on the event process
    assertThat(getEventProcessInstancesFromElasticsearch())
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getEvents())
          .extracting(FlowNodeInstanceDto::getActivityId, FlowNodeInstanceDto::getCanceled)
          .containsExactlyInAnyOrder(
            Tuple.tuple(BPMN_START_EVENT_ID, false),
            Tuple.tuple(USER_TASK_ID_ONE, true)
          );
      });
  }

  @Test
  public void instancesAreGeneratedForExistingEventsAfterPublish_otherEventsHaveNoEffect() {
    // given
    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(60));
    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventId = ingestTestEvent(STARTED_EVENT, startDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final OffsetDateTime endDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedEndEventId = ingestTestEvent(FINISHED_EVENT, endDateTime);

    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    ingestTestEvent("randomOtherEvent", LocalDateUtil.getCurrentDateTime());
    ingestTestEvent("evenAnotherEvent", LocalDateUtil.getCurrentDateTime());

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedEventProcessInstanceForTraceId(eventProcessMappingId, startDateTime, endDateTime),
              ProcessInstanceDto.Fields.events,
              EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates,
              EventProcessInstanceDto.Fields.correlatedEventsById
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    FlowNodeInstanceDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .processInstanceId(processInstanceDto.getProcessInstanceId())
                      .build(),
                    FlowNodeInstanceDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(endDateTime)
                      .endDate(endDateTime)
                      .durationInMs(0L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .processInstanceId(processInstanceDto.getProcessInstanceId())
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

    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    executeImportCycle();

    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(60));
    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventId = ingestTestEvent(STARTED_EVENT, startDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final OffsetDateTime endDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedEndEventId = ingestTestEvent(FINISHED_EVENT, endDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.plusSeconds(30));
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedEventProcessInstanceForTraceId(eventProcessMappingId, startDateTime, endDateTime),
              ProcessInstanceDto.Fields.events,
              EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates,
              EventProcessInstanceDto.Fields.correlatedEventsById
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    FlowNodeInstanceDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .processInstanceId(processInstanceDto.getProcessInstanceId())
                      .build(),
                    FlowNodeInstanceDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(endDateTime)
                      .endDate(endDateTime)
                      .durationInMs(0L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .processInstanceId(processInstanceDto.getProcessInstanceId())
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

    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);
    executeImportCycle();

    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(60));
    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventId = ingestTestEvent(STARTED_EVENT, startDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final String ingestedEndEventId = ingestTestEvent(FINISHED_EVENT, startDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(10));
    final OffsetDateTime updatedEndDateTime = LocalDateUtil.getCurrentDateTime();
    ingestTestEvent(ingestedEndEventId, FINISHED_EVENT, updatedEndDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.plusSeconds(30));
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedEventProcessInstanceForTraceId(
                eventProcessMappingId,
                startDateTime,
                updatedEndDateTime
              ),
              ProcessInstanceDto.Fields.events, EventProcessInstanceDto.Fields.correlatedEventsById
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    FlowNodeInstanceDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .processInstanceId(processInstanceDto.getProcessInstanceId())
                      .build(),
                    FlowNodeInstanceDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(updatedEndDateTime)
                      .endDate(updatedEndDateTime)
                      .durationInMs(0L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .processInstanceId(processInstanceDto.getProcessInstanceId())
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

    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventId = ingestTestEvent(STARTED_EVENT, startDateTime);

    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              EventProcessInstanceDto.eventProcessInstanceBuilder()
                .processDefinitionId(eventProcessMappingId)
                .processDefinitionKey(eventProcessMappingId)
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
              ProcessInstanceDto.Fields.events,
              ProcessInstanceDto.Fields.userTasks,
              EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates,
              EventProcessInstanceDto.Fields.correlatedEventsById
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    FlowNodeInstanceDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .processInstanceId(processInstanceDto.getProcessInstanceId())
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

    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventId = ingestTestEvent(STARTED_EVENT, startDateTime);

    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    executeImportCycle();

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final OffsetDateTime endDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedEndEventId = ingestTestEvent(FINISHED_EVENT, endDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.plusSeconds(30));
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedEventProcessInstanceForTraceId(eventProcessMappingId, startDateTime, endDateTime),
              ProcessInstanceDto.Fields.events,
              EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates,
              EventProcessInstanceDto.Fields.correlatedEventsById
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    FlowNodeInstanceDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .processInstanceId(processInstanceDto.getProcessInstanceId())
                      .build(),
                    FlowNodeInstanceDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(endDateTime)
                      .endDate(endDateTime)
                      .durationInMs(0L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .processInstanceId(processInstanceDto.getProcessInstanceId())
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

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final OffsetDateTime endDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedEndEventId = ingestTestEvent(FINISHED_EVENT, endDateTime);

    final String eventProcessId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedEventProcessInstanceForTraceId(eventProcessId, null, endDateTime),
              ProcessInstanceDto.Fields.events,
              EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates,
              EventProcessInstanceDto.Fields.correlatedEventsById
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    FlowNodeInstanceDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(endDateTime)
                      .endDate(endDateTime)
                      .durationInMs(0L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .processInstanceId(processInstanceDto.getProcessInstanceId())
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
      .getOptimizeIndexNameWithVersion(new EventProcessInstanceIndex(eventProcessPublishStateId));
  }

  private String getOptimizeIndexAliasForIndexName(final String indexName) {
    return elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .getIndexNameService()
      .getOptimizeIndexAliasForIndex(indexName);
  }

  @SneakyThrows
  private boolean eventProcessInstanceIndicesExist() {
    final String indexAlias = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .getIndexNameService()
      .getOptimizeIndexAliasForIndex(EVENT_PROCESS_INSTANCE_INDEX_PREFIX) + "*";
    return elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .getHighLevelClient()
      .indices().exists(new GetIndexRequest(indexAlias), RequestOptions.DEFAULT);
  }

}
