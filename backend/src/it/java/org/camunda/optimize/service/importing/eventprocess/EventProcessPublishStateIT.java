/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.util.Maps;
import org.camunda.optimize.dto.optimize.query.event.process.EventImportSourceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessState;
import org.camunda.optimize.dto.optimize.query.event.process.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.MappedEventType;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.optimize.EventProcessClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.event.process.EventSourceType.CAMUNDA;
import static org.camunda.optimize.service.events.CamundaEventService.EVENT_SOURCE_CAMUNDA;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventSourceEntry;

public class EventProcessPublishStateIT extends AbstractEventProcessIT {

  @ParameterizedTest
  @MethodSource("eventSourceEntryTypeCombinations")
  public void afterPublishPublishStateIsCreated(List<EventSourceEntryDto> eventSourceEntries) {
    // given
    setupOptimizeForImportingExternalSources(eventSourceEntries);
    final String eventProcessMappingId = createEventProcessMappingWithMappingsAndEventSources(
      Collections.singletonMap(BPMN_START_EVENT_ID, buildExternalEventMapping("someEventName", MappedEventType.END)),
      eventSourceEntries
    );

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId)).isNotEmpty();
  }

  @Test
  public void publishMappingWithNoEventSourcesFails() {
    // given
    final String eventProcessMappingId = createEventProcessMappingWithMappingsAndEventSources(
      Collections.singletonMap(BPMN_START_EVENT_ID, buildExternalEventMapping("someEventName", MappedEventType.END)),
      Collections.emptyList()
    );

    // when
    Response response = eventProcessClient.createPublishEventProcessMappingRequest(eventProcessMappingId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void afterPublishPublishStateIsCreatedOnlyForTheExpectedEventProcess() {
    // given
    final String eventProcessMappingId1 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);
    final String eventProcessMappingId2 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId1);

    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId1)).isNotEmpty();
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId2)).isEmpty();
  }

  @ParameterizedTest(name = "Event process publish state is deleted on {0}.")
  @MethodSource("cancelOrDeleteAction")
  public void eventProcessPublishStateIsDeletedOn(final String actionName,
                                                  final BiConsumer<EventProcessClient, String> action) {
    // given
    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(FINISHED_EVENT);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    executeImportCycle();
    executeImportCycle();

    // when
    action.accept(eventProcessClient, eventProcessMappingId);
    executeImportCycle();

    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId)).isEmpty();
  }

  @ParameterizedTest(name = "Only expected event publish state is deleted on {0}, other is still present.")
  @MethodSource("cancelOrDeleteAction")
  public void otherEventPublishStateIsNotAffectedOn(final String actionName,
                                                    final BiConsumer<EventProcessClient, String> action) {
    // given
    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(FINISHED_EVENT);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final String eventProcessMappingId1 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);
    final String eventProcessMappingId2 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    eventProcessClient.publishEventProcessMapping(eventProcessMappingId1);
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId2);

    executeImportCycle();
    executeImportCycle();

    // when
    action.accept(eventProcessClient, eventProcessMappingId1);
    executeImportCycle();

    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId2)).isNotEmpty();
  }

  @Test
  public void progressIsZeroOnNoCycleRun() {
    // given
    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final OffsetDateTime publishDateTime = LocalDateUtil.getCurrentDateTime();
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    // then
    final EventProcessMappingResponseDto storedEventProcessMapping = eventProcessClient.getEventProcessMapping(
      eventProcessMappingId);
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasNoNullFieldsOrProperties()
      .isEqualToIgnoringGivenFields(
        EventProcessPublishStateDto.builder()
          .processMappingId(eventProcessMappingId)
          .name(EVENT_PROCESS_NAME)
          .state(EventProcessState.PUBLISH_PENDING)
          .publishDateTime(publishDateTime)
          .deleted(false)
          .publishProgress(0.0D)
          .eventImportSources(Collections.singletonList(
            EventImportSourceDto.builder()
              .firstEventForSourceAtTimeOfPublishTimestamp(OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(0L),
                ZoneId.systemDefault()
              ))
              .lastEventForSourceAtTimeOfPublishTimestamp(OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(0L),
                ZoneId.systemDefault()
              ))
              .lastImportedEventTimestamp(OffsetDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneId.systemDefault()))
              .eventSource(convertToEventSourceEntryDto(storedEventProcessMapping.getEventSources().get(0)))
              .build()))
          .build(),
        EventProcessPublishStateDto.Fields.id,
        EventProcessPublishStateDto.Fields.xml,
        EventProcessPublishStateDto.Fields.mappings
      );
  }

  @Test
  public void partialPublishingProgress() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventImportConfiguration().setMaxPageSize(1);
    final OffsetDateTime timeBaseLine = LocalDateUtil.getCurrentDateTime();

    final OffsetDateTime firstEventTimestamp = timeBaseLine.minusSeconds(60);
    LocalDateUtil.setCurrentTime(firstEventTimestamp);
    ingestTestEvent(STARTED_EVENT, firstEventTimestamp);

    final OffsetDateTime secondEventTimestamp = timeBaseLine.minusSeconds(45);
    LocalDateUtil.setCurrentTime(secondEventTimestamp);
    ingestTestEvent(STARTED_EVENT, secondEventTimestamp);

    final OffsetDateTime thirdEventTimestamp = timeBaseLine.minusSeconds(20);
    LocalDateUtil.setCurrentTime(thirdEventTimestamp);
    ingestTestEvent(STARTED_EVENT, thirdEventTimestamp);

    LocalDateUtil.setCurrentTime(timeBaseLine);
    ingestTestEvent(STARTED_EVENT, timeBaseLine);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    // when the first import cycle completes the status has not been updated yet
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 0.0D);

    // when the second import cycle completes only the first event has been considered, which we use as the starting
    // timestamp and which gets ignored for publish progress calculations - so will be zero
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 0.0D);

    // when the third import cycle completes the first event considered for publish progress has been ingested so the
    // publish progress is updated accordingly
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 25.0D);

    // when the fourth import cycle completes another event considered for publish progress has been ingested so the
    // publish progress is updated accordingly
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 66.6D);

    // when the fifth import cycle completes the status is updated to Published as all events have been processed
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISHED)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 100.0D);
  }

  @Test
  public void eventsIngestedAfterPublishDoNotAffectProgress() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventImportConfiguration().setMaxPageSize(1);

    final OffsetDateTime timeBaseLine = LocalDateUtil.getCurrentDateTime();
    final OffsetDateTime firstEventTimestamp = timeBaseLine.minusSeconds(60);
    LocalDateUtil.setCurrentTime(firstEventTimestamp);
    ingestTestEvent(STARTED_EVENT, firstEventTimestamp);

    final OffsetDateTime secondEventTimestamp = timeBaseLine.minusSeconds(45);
    LocalDateUtil.setCurrentTime(secondEventTimestamp);
    ingestTestEvent(STARTED_EVENT, secondEventTimestamp);

    final OffsetDateTime lastEventTimestamp = timeBaseLine.minusSeconds(30);
    LocalDateUtil.setCurrentTime(lastEventTimestamp);
    ingestTestEvent(FINISHED_EVENT, lastEventTimestamp);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final OffsetDateTime publishDateTime = LocalDateUtil.getCurrentDateTime();
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    LocalDateUtil.setCurrentTime(timeBaseLine.plusSeconds(10));
    ingestTestEvent(STARTED_EVENT, LocalDateUtil.getCurrentDateTime());
    ingestTestEvent(STARTED_EVENT, LocalDateUtil.getCurrentDateTime());
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    executeImportCycle();
    executeImportCycle();
    executeImportCycle();

    // then
    final EventProcessMappingResponseDto storedEventProcessMapping = eventProcessClient.getEventProcessMapping(
      eventProcessMappingId);
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasNoNullFieldsOrProperties()
      .isEqualToIgnoringGivenFields(
        EventProcessPublishStateDto.builder()
          .processMappingId(eventProcessMappingId)
          .state(EventProcessState.PUBLISH_PENDING)
          .publishDateTime(publishDateTime)
          .deleted(false)
          .publishProgress(50.0D)
          .eventImportSources(
            Collections.singletonList(
              EventImportSourceDto.builder()
                .firstEventForSourceAtTimeOfPublishTimestamp(firstEventTimestamp)
                .lastEventForSourceAtTimeOfPublishTimestamp(lastEventTimestamp)
                .lastImportExecutionTimestamp(LocalDateUtil.getCurrentDateTime())
                .lastImportedEventTimestamp(secondEventTimestamp)
                .eventSource(convertToEventSourceEntryDto(storedEventProcessMapping.getEventSources().get(0)))
                .build()))
          .build(),
        EventProcessPublishStateDto.Fields.id,
        EventProcessPublishStateDto.Fields.name,
        EventProcessPublishStateDto.Fields.xml,
        EventProcessPublishStateDto.Fields.mappings
      );
  }

  @Test
  public void eventProcessIsPublishedOnceAllEventsProcessed() {
    // given
    final OffsetDateTime timeBaseLine = LocalDateUtil.getCurrentDateTime();
    final OffsetDateTime firstEventTimestamp = timeBaseLine.minusSeconds(60);
    LocalDateUtil.setCurrentTime(firstEventTimestamp);
    ingestTestEvent(STARTED_EVENT, firstEventTimestamp);

    final OffsetDateTime lastEventTimestamp = timeBaseLine.minusSeconds(30);
    LocalDateUtil.setCurrentTime(lastEventTimestamp);
    ingestTestEvent(FINISHED_EVENT, lastEventTimestamp);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final OffsetDateTime publishDateTime = LocalDateUtil.getCurrentDateTime();
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    executeImportCycle();
    executeImportCycle();

    // then
    final EventProcessMappingResponseDto storedEventProcessMapping = eventProcessClient.getEventProcessMapping(
      eventProcessMappingId);
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasNoNullFieldsOrProperties()
      .isEqualToIgnoringGivenFields(
        EventProcessPublishStateDto.builder()
          .processMappingId(eventProcessMappingId)
          .state(EventProcessState.PUBLISHED)
          .publishDateTime(publishDateTime)
          .deleted(false)
          .publishProgress(100.0D)
          .eventImportSources(
            Collections.singletonList(
              EventImportSourceDto.builder()
                .firstEventForSourceAtTimeOfPublishTimestamp(firstEventTimestamp)
                .lastEventForSourceAtTimeOfPublishTimestamp(lastEventTimestamp)
                .lastImportExecutionTimestamp(LocalDateUtil.getCurrentDateTime())
                .lastImportedEventTimestamp(lastEventTimestamp)
                .eventSource(convertToEventSourceEntryDto(storedEventProcessMapping.getEventSources().get(0)))
                .build()))
          .build(),
        EventProcessPublishStateDto.Fields.id,
        EventProcessPublishStateDto.Fields.name,
        EventProcessPublishStateDto.Fields.xml,
        EventProcessPublishStateDto.Fields.mappings
      );
  }

  @Test
  public void eventProcessProgressesThroughPublishStatesAndProgressWithCamundaEventSource() throws SQLException {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventImportConfiguration().setMaxPageSize(1);

    final OffsetDateTime timeBaseLine = LocalDateUtil.getCurrentDateTime();
    final String tracingVariable = "key";
    final ProcessInstanceEngineDto processInstanceEngineDto =
      deployAndStartProcessWithVariables(Maps.newHashMap(tracingVariable, "value"));
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceEngineDto.getId());

    engineDatabaseExtension.changeProcessInstanceStartDate(
      processInstanceEngineDto.getId(),
      timeBaseLine.minusSeconds(60)
    );
    updateActivityStartEndTimestampInEngine(
      BPMN_START_EVENT_ID,
      timeBaseLine.minusSeconds(60),
      processInstanceEngineDto
    );
    updateActivityStartEndTimestampInEngine(USER_TASK_ID_ONE, timeBaseLine.minusSeconds(30), processInstanceEngineDto);
    updateActivityStartEndTimestampInEngine(BPMN_END_EVENT_ID, timeBaseLine, processInstanceEngineDto);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceEngineDto.getId(), timeBaseLine);

    importEngineEntities();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final String eventProcessMappingId = createEventProcessMappingWithMappingsAndEventSources(
      ImmutableMap.of(
        BPMN_START_EVENT_ID,
        buildCamundaEventMapping(processInstanceEngineDto, BPMN_START_EVENT_ID, MappedEventType.START),
        USER_TASK_ID_ONE,
        buildCamundaEventMapping(processInstanceEngineDto, USER_TASK_ID_ONE, MappedEventType.END),
        BPMN_END_EVENT_ID,
        buildCamundaEventMapping(processInstanceEngineDto, BPMN_END_EVENT_ID, MappedEventType.START)
      ),
      Collections.singletonList(
        camundaEventSource().toBuilder()
          .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
          .tracedByBusinessKey(false)
          .traceVariable(tracingVariable)
          .build())
    );
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    // when the first import cycle completes the status has not been updated yet
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 0.0D);

    // when the second import cycle completes only the first event has been considered, which we use as the starting
    // timestamp and which gets ignored for publish progress calculations - so will be zero
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 0.0D);

    // when the third import cycle completes another event considered for publish progress has been ingested so the
    // publish progress is updated accordingly
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 50.0);

    // when the fourth import cycle completes the status is updated to Published as all events have been processed
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISHED)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 100.0D);
  }

  @Test
  public void eventProcessCamundaEventSourceLastEventTimestampGetsUpdatedEvenIfNoFetchedEventsCanBeCorrelated()
    throws SQLException {
    // given
    final OffsetDateTime timeBaseLine = LocalDateUtil.getCurrentDateTime();
    final String tracingVariable = "key";
    final ProcessInstanceEngineDto processInstanceEngineDto =
      deployAndStartProcessWithVariables(Maps.newHashMap(tracingVariable, "value"));
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceEngineDto.getId());

    engineDatabaseExtension.changeProcessInstanceStartDate(
      processInstanceEngineDto.getId(),
      timeBaseLine.minusSeconds(60)
    );
    updateActivityStartEndTimestampInEngine(
      BPMN_START_EVENT_ID,
      timeBaseLine.minusSeconds(60),
      processInstanceEngineDto
    );
    updateActivityStartEndTimestampInEngine(USER_TASK_ID_ONE, timeBaseLine.minusSeconds(30), processInstanceEngineDto);
    updateActivityStartEndTimestampInEngine(BPMN_END_EVENT_ID, timeBaseLine, processInstanceEngineDto);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceEngineDto.getId(), timeBaseLine);

    importEngineEntities();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final String eventProcessMappingId = createEventProcessMappingWithMappingsAndEventSources(
      ImmutableMap.of(
        BPMN_START_EVENT_ID,
        buildCamundaEventMapping(processInstanceEngineDto, BPMN_START_EVENT_ID, MappedEventType.START),
        USER_TASK_ID_ONE,
        buildCamundaEventMapping(processInstanceEngineDto, USER_TASK_ID_ONE, MappedEventType.END),
        BPMN_END_EVENT_ID,
        buildCamundaEventMapping(processInstanceEngineDto, BPMN_END_EVENT_ID, MappedEventType.START)
      ),
      Collections.singletonList(
        camundaEventSource().toBuilder()
          .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
          .tracedByBusinessKey(false)
          .traceVariable("someOtherUncorrelateableVariable")
          .build())
    );
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    // when the first import cycle completes the status has not been updated yet
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 0.0D);

    // when the second import cycle completes the publish state will be updated
    executeImportCycle();
    final EventProcessPublishStateDto publishState = getEventProcessPublishStateDtoFromElasticsearch(
      eventProcessMappingId).get();
    // then no events have been correlated
    assertThat(getEventProcessInstancesFromElasticsearchForProcessMappingId(publishState.getId())).isEmpty();
    // then the import source last imported timestamp reflects the latest imported event
    assertThat(publishState.getEventImportSources()
                 .get(0)
                 .getLastImportedEventTimestamp()).isEqualTo(timeBaseLine);
  }

  @Test
  public void eventsIngestedAfterPublishDontAffectPublishStateAndProgress() {
    // given
    final OffsetDateTime timeBaseLine = LocalDateUtil.getCurrentDateTime();
    final OffsetDateTime firstEventTimestamp = timeBaseLine.minusSeconds(60);
    LocalDateUtil.setCurrentTime(firstEventTimestamp);
    ingestTestEvent(STARTED_EVENT, firstEventTimestamp);

    final OffsetDateTime lastEventTimestamp = timeBaseLine.minusSeconds(30);
    LocalDateUtil.setCurrentTime(lastEventTimestamp);
    ingestTestEvent(FINISHED_EVENT, lastEventTimestamp);

    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final OffsetDateTime publishDateTime = LocalDateUtil.getCurrentDateTime();
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    executeImportCycle();
    executeImportCycle();

    ingestTestEvent(STARTED_EVENT, OffsetDateTime.now());
    executeImportCycle();

    // then
    final EventProcessMappingResponseDto storedEventProcessMapping = eventProcessClient.getEventProcessMapping(
      eventProcessMappingId);
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasNoNullFieldsOrProperties()
      .isEqualToIgnoringGivenFields(
        EventProcessPublishStateDto.builder()
          .processMappingId(eventProcessMappingId)
          .state(EventProcessState.PUBLISHED)
          .publishDateTime(publishDateTime)
          .deleted(false)
          .publishProgress(100.0D)
          .eventImportSources(
            Collections.singletonList(
              EventImportSourceDto.builder()
                .firstEventForSourceAtTimeOfPublishTimestamp(firstEventTimestamp)
                .lastEventForSourceAtTimeOfPublishTimestamp(lastEventTimestamp)
                .lastImportExecutionTimestamp(LocalDateUtil.getCurrentDateTime())
                .lastImportedEventTimestamp(lastEventTimestamp)
                .eventSource(convertToEventSourceEntryDto(storedEventProcessMapping.getEventSources().get(0)))
                .build()))
          .build(),
        EventProcessPublishStateDto.Fields.id,
        EventProcessPublishStateDto.Fields.name,
        EventProcessPublishStateDto.Fields.xml,
        EventProcessPublishStateDto.Fields.mappings
      );
  }

  @Test
  public void eventProcessProgressesThroughPublishStatesAndProgressTakingAverageFromMultipleSources() throws
                                                                                                      SQLException {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventImportConfiguration().setMaxPageSize(1);

    final OffsetDateTime timeBaseLine = LocalDateUtil.getCurrentDateTime();
    final String tracingVariable = "key";
    final ProcessInstanceEngineDto processInstanceEngineDto =
      deployAndStartProcessWithVariables(Maps.newHashMap(tracingVariable, "value"));
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceEngineDto.getId());

    engineDatabaseExtension.changeProcessInstanceStartDate(
      processInstanceEngineDto.getId(),
      timeBaseLine.minusSeconds(60)
    );
    updateActivityStartEndTimestampInEngine(
      BPMN_START_EVENT_ID,
      timeBaseLine.minusSeconds(60),
      processInstanceEngineDto
    );
    updateActivityStartEndTimestampInEngine(USER_TASK_ID_ONE, timeBaseLine.minusSeconds(30), processInstanceEngineDto);
    updateActivityStartEndTimestampInEngine(BPMN_END_EVENT_ID, timeBaseLine, processInstanceEngineDto);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceEngineDto.getId(), timeBaseLine);
    importEngineEntities();

    final OffsetDateTime firstExternalEventTimestamp = timeBaseLine.minusSeconds(60);
    LocalDateUtil.setCurrentTime(firstExternalEventTimestamp);
    ingestTestEvent(STARTED_EVENT, firstExternalEventTimestamp);

    final OffsetDateTime secondExternalEventTimestamp = timeBaseLine.minusSeconds(40);
    LocalDateUtil.setCurrentTime(secondExternalEventTimestamp);
    ingestTestEvent(USER_TASK_ID_ONE, secondExternalEventTimestamp);

    final OffsetDateTime thirdExternalEventTimestamp = timeBaseLine.minusSeconds(20);
    LocalDateUtil.setCurrentTime(thirdExternalEventTimestamp);
    ingestTestEvent(STARTED_EVENT, thirdExternalEventTimestamp);

    LocalDateUtil.setCurrentTime(timeBaseLine);
    ingestTestEvent(USER_TASK_ID_ONE, timeBaseLine);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final String eventProcessMappingId = createEventProcessMappingWithMappingsAndEventSources(
      ImmutableMap.of(
        BPMN_START_EVENT_ID,
        buildCamundaEventMapping(processInstanceEngineDto, BPMN_START_EVENT_ID, MappedEventType.START),
        USER_TASK_ID_ONE,
        buildExternalEventMapping(USER_TASK_ID_ONE, MappedEventType.END),
        BPMN_END_EVENT_ID,
        buildCamundaEventMapping(processInstanceEngineDto, BPMN_END_EVENT_ID, MappedEventType.START)
      ),
      Arrays.asList(
        camundaEventSource().toBuilder()
          .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
          .tracedByBusinessKey(false)
          .traceVariable(tracingVariable)
          .build(),
        createExternalEventSourceEntry()
      )
    );
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    // when the first import cycle completes the status has not been updated yet
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 0.0D);

    // when the second import cycle completes only the first event for each source have been considered, which we use
    // as the starting timestamps and which get ignored for publish progress calculations - so the average will be zero
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 0.0D);

    // when the third import cycle completes another event considered for publish progress has been ingested for each
    // source so the publish progress is updated accordingly
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      // The camunda event source is 50% published and the external event source is 33.3% published, so the average
      // is taken
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 41.6);

    // when the fourth import cycle completes another event considered for publish progress has been ingested for
    // each source so the publish progress is updated accordingly
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      // The camunda event source is 100% published and the external event source is 66.6% published, so the average
      // is taken
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 83.3D);

    // when the fifth import cycle completes the status is updated to Published as all events have been processed
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISHED)
      // both sources are now 100% published so the average will be 100%
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 100.0D);
  }

  private void updateActivityStartEndTimestampInEngine(final String activityId,
                                                       final OffsetDateTime firstEventTimestamp,
                                                       final ProcessInstanceEngineDto processInstanceEngineDto) throws
                                                                                                                SQLException {
    engineDatabaseExtension.changeActivityInstanceStartDate(
      processInstanceEngineDto.getId(),
      activityId,
      firstEventTimestamp
    );
    engineDatabaseExtension.changeActivityInstanceEndDate(
      processInstanceEngineDto.getId(),
      activityId,
      firstEventTimestamp
    );
  }

  private String createEventProcessMappingWithMappingsAndEventSources(Map<String, EventMappingDto> eventMappings,
                                                                      List<EventSourceEntryDto> eventSourceEntries) {
    final EventProcessMappingDto eventProcessMappingDto =
      eventProcessClient.buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
        eventMappings,
        "someName",
        createTwoEventAndOneTaskActivitiesProcessDefinitionXml(),
        eventSourceEntries
      );
    return eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
  }

  private EventMappingDto buildExternalEventMapping(final String eventName,
                                                    final MappedEventType mappedEventType) {
    if (mappedEventType.equals(MappedEventType.START)) {
      return EventMappingDto.builder().start(buildExternalEventType(eventName)).build();
    } else {
      return EventMappingDto.builder().end(buildExternalEventType(eventName)).build();
    }
  }

  private EventMappingDto buildCamundaEventMapping(final ProcessInstanceEngineDto processInstanceEngineDto,
                                                   final String eventName,
                                                   final MappedEventType mappedEventType) {
    if (mappedEventType.equals(MappedEventType.START)) {
      return EventMappingDto.builder().start(buildCamundaEventType(processInstanceEngineDto, eventName)).build();
    } else {
      return EventMappingDto.builder().end(buildCamundaEventType(processInstanceEngineDto, eventName)).build();
    }
  }

  private EventTypeDto buildCamundaEventType(final ProcessInstanceEngineDto processInstanceEngineDto,
                                             final String eventName) {
    return EventTypeDto.builder()
      .group(processInstanceEngineDto.getProcessDefinitionKey())
      .source(EVENT_SOURCE_CAMUNDA)
      .eventName(eventName)
      .build();
  }

  private EventTypeDto buildExternalEventType(String eventName) {
    return EventTypeDto.builder()
      .group(EXTERNAL_EVENT_GROUP)
      .source(EXTERNAL_EVENT_SOURCE)
      .eventName(eventName)
      .build();
  }

  private static Stream<List<EventSourceEntryDto>> eventSourceEntryTypeCombinations() {
    return Stream.of(
      Collections.singletonList(createExternalEventSourceEntry()),
      Collections.singletonList(camundaEventSource()),
      Arrays.asList(createExternalEventSourceEntry(), camundaEventSource())
    );
  }

  private static EventSourceEntryDto camundaEventSource() {
    return EventSourceEntryDto.builder()
      .type(CAMUNDA)
      .eventScope(Collections.singletonList(EventScopeType.ALL))
      .tracedByBusinessKey(true)
      .processDefinitionKey(RandomStringUtils.randomAlphabetic(10))
      .versions(Collections.singletonList("ALL"))
      .build();
  }

  private void setupOptimizeForImportingExternalSources(final List<EventSourceEntryDto> eventSourceEntries) {
    // We import the definition and instance to create the event indices
    eventSourceEntries
      .forEach(entry -> {
        if (CAMUNDA.equals(entry.getType())) {
          final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
          entry.setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey());
        }
      });
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    // then we remove all imported data so the test can use its own fixtures
    elasticSearchIntegrationTestExtension.deleteAllOptimizeData();
  }

}
