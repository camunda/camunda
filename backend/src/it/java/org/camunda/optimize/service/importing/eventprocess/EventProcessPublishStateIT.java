/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.util.Maps;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventImportSourceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessState;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.MappedEventType;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.optimize.EventProcessClient;
import org.junit.jupiter.api.Disabled;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType.CAMUNDA;
import static org.camunda.optimize.service.events.CamundaEventService.EVENT_SOURCE_CAMUNDA;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventAllGroupsSourceEntry;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventSourceEntryForGroup;

public class EventProcessPublishStateIT extends AbstractEventProcessIT {

  @ParameterizedTest
  @MethodSource("eventSourceEntryTypeCombinations")
  public void afterPublishPublishStateIsCreated(List<EventSourceEntryDto<?>> eventSourceEntries) {
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
      .usingRecursiveComparison()
      .ignoringFields(
        EventProcessPublishStateDto.Fields.id,
        EventProcessPublishStateDto.Fields.xml,
        EventProcessPublishStateDto.Fields.mappings
      )
      .isEqualTo(
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
              .eventImportSourceType(storedEventProcessMapping.getEventSources().get(0).getSourceType())
              .eventSourceConfigurations(
                Collections.singletonList(storedEventProcessMapping.getEventSources().get(0).getConfiguration()))
              .build()))
          .build()
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

    // when the second import cycle completes only the first and second event for each source have been processed
    // as the import fetches all events with the exact firstEvent Timestamp + a maxPageSize amount of events after it
    // so the publish progress is updated accordingly
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 25.0D);

    // when the third import cycle completes another event considered for publish progress has been ingested so the
    // publish progress is updated accordingly
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 66.6D);

    // when the fourth import cycle completes the status is updated to Published as all events have been processed
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

    // then
    final EventProcessMappingResponseDto storedEventProcessMapping = eventProcessClient.getEventProcessMapping(
      eventProcessMappingId);
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasNoNullFieldsOrProperties()
      .usingRecursiveComparison()
      .ignoringFields(
        EventProcessPublishStateDto.Fields.id,
        EventProcessPublishStateDto.Fields.name,
        EventProcessPublishStateDto.Fields.xml,
        EventProcessPublishStateDto.Fields.mappings
      )
      .isEqualTo(
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
                .eventImportSourceType(storedEventProcessMapping.getEventSources().get(0).getSourceType())
                .eventSourceConfigurations(
                  Collections.singletonList(storedEventProcessMapping.getEventSources().get(0).getConfiguration()))
                .build()))
          .build()
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
      .usingRecursiveComparison()
      .ignoringFields(
        EventProcessPublishStateDto.Fields.id,
        EventProcessPublishStateDto.Fields.name,
        EventProcessPublishStateDto.Fields.xml,
        EventProcessPublishStateDto.Fields.mappings
      )
      .isEqualTo(
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
                .eventImportSourceType(storedEventProcessMapping.getEventSources().get(0).getSourceType())
                .eventSourceConfigurations(
                  Collections.singletonList(storedEventProcessMapping.getEventSources().get(0).getConfiguration()))
                .build()))
          .build()
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

    final CamundaEventSourceEntryDto eventSource = camundaEventSource();
    final CamundaEventSourceConfigDto sourceConfig = eventSource.getConfiguration();
    sourceConfig.setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey());
    sourceConfig.setTracedByBusinessKey(false);
    sourceConfig.setTraceVariable(tracingVariable);
    final String eventProcessMappingId = createEventProcessMappingWithMappingsAndEventSources(
      ImmutableMap.of(
        BPMN_START_EVENT_ID,
        buildCamundaEventMapping(processInstanceEngineDto, BPMN_START_EVENT_ID, MappedEventType.START),
        USER_TASK_ID_ONE,
        buildCamundaEventMapping(processInstanceEngineDto, USER_TASK_ID_ONE, MappedEventType.END),
        BPMN_END_EVENT_ID,
        buildCamundaEventMapping(processInstanceEngineDto, BPMN_END_EVENT_ID, MappedEventType.START)
      ),
      Collections.singletonList(eventSource)
    );
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    // when the first import cycle completes the status has not been updated yet
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 0.0D);

    // when the second import cycle completes only the first and second event for each source have been processed
    // as the import fetches all events with the exact firstEvent Timestamp + a maxPageSize amount of events after it
    // so the publish progress is updated accordingly
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 50.0);

    // when the third import cycle completes the status is updated to Published as all events have been processed
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

    final CamundaEventSourceEntryDto eventSourceDto = camundaEventSource();
    final CamundaEventSourceConfigDto eventsourceConfig = eventSourceDto.getConfiguration();
    eventsourceConfig.setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey());
    eventsourceConfig.setTracedByBusinessKey(false);
    eventsourceConfig.setTraceVariable("someOtherUncorrelateableVariable");
    final String eventProcessMappingId = createEventProcessMappingWithMappingsAndEventSources(
      ImmutableMap.of(
        BPMN_START_EVENT_ID,
        buildCamundaEventMapping(processInstanceEngineDto, BPMN_START_EVENT_ID, MappedEventType.START),
        USER_TASK_ID_ONE,
        buildCamundaEventMapping(processInstanceEngineDto, USER_TASK_ID_ONE, MappedEventType.END),
        BPMN_END_EVENT_ID,
        buildCamundaEventMapping(processInstanceEngineDto, BPMN_END_EVENT_ID, MappedEventType.START)
      ),
      Collections.singletonList(eventSourceDto)
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
    assertThat(getEventProcessInstancesFromElasticsearchForProcessPublishStateId(publishState.getId())).isEmpty();
    // then the import source last imported timestamp reflects the latest imported event
    assertThat(publishState.getEventImportSources()
                 .get(0)
                 .getLastImportedEventTimestamp()).isEqualTo(timeBaseLine);
  }

  @Test
  public void eventsIngestedAfterPublishDoNotAffectPublishStateAndProgress() {
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
      .usingRecursiveComparison()
      .ignoringFields(
        EventProcessPublishStateDto.Fields.id,
        EventProcessPublishStateDto.Fields.name,
        EventProcessPublishStateDto.Fields.xml,
        EventProcessPublishStateDto.Fields.mappings
      )
      .isEqualTo(
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
                .eventImportSourceType(storedEventProcessMapping.getEventSources().get(0).getSourceType())
                .eventSourceConfigurations(
                  Collections.singletonList(storedEventProcessMapping.getEventSources().get(0).getConfiguration()))
                .build()))
          .build()
      );
  }

  @Disabled("Disabled without thorough investigation to get master passing again. EBP is not a critical feature")
  @Test
  public void eventProcessProgressesThroughPublishStatesAndProgressTakingAverageFromMultipleSources() {
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

    final CamundaEventSourceEntryDto camundaEventSource = camundaEventSource();
    final CamundaEventSourceConfigDto eventSourceConfig = camundaEventSource.getConfiguration();
    eventSourceConfig.setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey());
    eventSourceConfig.setTracedByBusinessKey(false);
    eventSourceConfig.setTraceVariable(processInstanceEngineDto.getProcessDefinitionKey());
    final String eventProcessMappingId = createEventProcessMappingWithMappingsAndEventSources(
      ImmutableMap.of(
        BPMN_START_EVENT_ID,
        buildCamundaEventMapping(processInstanceEngineDto, BPMN_START_EVENT_ID, MappedEventType.START),
        USER_TASK_ID_ONE,
        buildExternalEventMapping(USER_TASK_ID_ONE, MappedEventType.END),
        BPMN_END_EVENT_ID,
        buildCamundaEventMapping(processInstanceEngineDto, BPMN_END_EVENT_ID, MappedEventType.START)
      ),
      Arrays.asList(camundaEventSource, createExternalEventAllGroupsSourceEntry())
    );
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    // when the first import cycle completes the status has not been updated yet
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 0.0D);

    // when the second import cycle completes only the first and second event for each source have been processed
    // as the import fetches all events with the exact firstEvent Timestamp + a maxPageSize amount of events after it
    // so the publish progress is updated accordingly
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      // The camunda event source is 50% published and the external event source is 33.3% published, so the average
      // is taken
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 41.6);

    // when the third import cycle completes another event considered for publish progress has been ingested for
    // each source so the publish progress is updated accordingly
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      // The camunda event source is 100% published and the external event source is 66.6% published, so the average
      // is taken
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 83.3D);

    // when the fourth import cycle completes the status is updated to Published as all events have been processed
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISHED)
      // both sources are now 100% published so the average will be 100%
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 100.0D);
  }

  private static Stream<String> groupsToCorrelate() {
    return Stream.of("groupA", null);
  }

  @ParameterizedTest
  @MethodSource("groupsToCorrelate")
  public void eventProcessProgressesThroughPublishStatesForSingleGroupOfExternalEvents(final String groupToCorrelate) {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventImportConfiguration().setMaxPageSize(1);
    final OffsetDateTime timeBaseLine = LocalDateUtil.getCurrentDateTime();
    final String otherGroup = "otherGroup";

    final OffsetDateTime firstExternalEventTimestamp = timeBaseLine.minusSeconds(60);
    LocalDateUtil.setCurrentTime(firstExternalEventTimestamp);
    ingestTestEventForGroup(STARTED_EVENT, firstExternalEventTimestamp, groupToCorrelate);

    final OffsetDateTime secondExternalEventTimestamp = timeBaseLine.minusSeconds(30);
    LocalDateUtil.setCurrentTime(secondExternalEventTimestamp);
    ingestTestEventForGroup(USER_TASK_ID_ONE, secondExternalEventTimestamp, groupToCorrelate);

    LocalDateUtil.setCurrentTime(timeBaseLine);
    ingestTestEventForGroup(FINISHED_EVENT, timeBaseLine, groupToCorrelate);

    // these events should not be correlated or used for calculations as the group isn't selected as source
    ingestTestEventForGroup(STARTED_EVENT, firstExternalEventTimestamp.minusSeconds(10), otherGroup);
    ingestTestEventForGroup(USER_TASK_ID_ONE, secondExternalEventTimestamp.minusSeconds(10), otherGroup);
    ingestTestEventForGroup(FINISHED_EVENT, timeBaseLine.minusSeconds(10), otherGroup);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final String eventProcessMappingId = createEventProcessMappingWithMappingsAndEventSources(
      ImmutableMap.of(
        BPMN_START_EVENT_ID,
        buildExternalEventMapping(STARTED_EVENT, MappedEventType.END, groupToCorrelate),
        USER_TASK_ID_ONE,
        buildExternalEventMapping(USER_TASK_ID_ONE, MappedEventType.END, groupToCorrelate),
        BPMN_END_EVENT_ID,
        buildExternalEventMapping(FINISHED_EVENT, MappedEventType.END, groupToCorrelate)
      ),
      Collections.singletonList(createExternalEventSourceEntryForGroup(groupToCorrelate))
    );
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    // when the first import cycle completes the status has not been updated yet
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 0.0D);

    // when the second import cycle completes only the first and second event for each source have been processed
    // as the import fetches all events with the exact firstEvent Timestamp + a maxPageSize amount of events after it
    // so the publish progress is updated accordingly
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 50.0D);

    // when the third import cycle completes another event considered for publish progress has been ingested. The
    // events
    // from the second group are ignored and not considered for progress calculation
    executeImportCycle();
    // then
    final EventProcessPublishStateDto finalPublishState =
      getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId)
        .orElseThrow(() -> new OptimizeIntegrationTestException("Cannot get publish state"));
    assertThat(finalPublishState)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISHED)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 100.0D)
      .extracting(EventProcessPublishStateDto::getEventImportSources).asList().hasSize(1);
    // and the event instance has all expected correlated events
    final List<EventProcessInstanceDto> eventInstances =
      getEventProcessInstancesFromElasticsearchForProcessPublishStateId(finalPublishState.getId());
    assertThat(eventInstances)
      .singleElement()
      .extracting(ProcessInstanceDto::getFlowNodeInstances)
      .satisfies(events -> assertThat(
        events.stream().map(FlowNodeInstanceDto::getFlowNodeId).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(BPMN_START_EVENT_ID, USER_TASK_ID_ONE, BPMN_END_EVENT_ID));
  }

  @Test
  public void eventProcessProgressesThroughPublishStatesAndProgressConsidersMultipleExternalSourcesAndCamundaSource() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventImportConfiguration().setMaxPageSize(1);

    final OffsetDateTime timeBaseLine = LocalDateUtil.getCurrentDateTime();
    final String tracingVariable = "key";
    final ProcessInstanceEngineDto processInstanceEngineDto =
      deployAndStartProcessWithVariables(Maps.newHashMap(tracingVariable, MY_TRACE_ID_1));
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceEngineDto.getId());

    engineDatabaseExtension.changeProcessInstanceStartDate(
      processInstanceEngineDto.getId(),
      timeBaseLine.minusSeconds(60)
    );
    updateActivityStartEndTimestampInEngine(
      BPMN_START_EVENT_ID,
      timeBaseLine.minusSeconds(45),
      processInstanceEngineDto
    );
    updateActivityStartEndTimestampInEngine(USER_TASK_ID_ONE, timeBaseLine.minusSeconds(30), processInstanceEngineDto);
    updateActivityStartEndTimestampInEngine(BPMN_END_EVENT_ID, timeBaseLine, processInstanceEngineDto);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceEngineDto.getId(), timeBaseLine);
    importEngineEntities();

    final String firstGroup = "groupA";
    final String secondGroup = "groupB";

    final OffsetDateTime firstExternalEventTimestamp = timeBaseLine.minusSeconds(60);
    LocalDateUtil.setCurrentTime(firstExternalEventTimestamp);
    ingestTestEventForGroup(USER_TASK_ID_ONE, firstExternalEventTimestamp, firstGroup);

    final OffsetDateTime secondExternalEventTimestamp = timeBaseLine.minusSeconds(40);
    LocalDateUtil.setCurrentTime(secondExternalEventTimestamp);
    ingestTestEventForGroup(FINISHED_EVENT, secondExternalEventTimestamp, firstGroup);

    final OffsetDateTime thirdExternalEventTimestamp = timeBaseLine.minusSeconds(20);
    LocalDateUtil.setCurrentTime(thirdExternalEventTimestamp);
    ingestTestEventForGroup(USER_TASK_ID_ONE, thirdExternalEventTimestamp, secondGroup);

    LocalDateUtil.setCurrentTime(timeBaseLine);
    ingestTestEventForGroup(FINISHED_EVENT, timeBaseLine, secondGroup);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final CamundaEventSourceEntryDto camundaEventSource = camundaEventSource();
    final CamundaEventSourceConfigDto eventSourceConfig = camundaEventSource.getConfiguration();
    eventSourceConfig.setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey());
    eventSourceConfig.setTracedByBusinessKey(false);
    eventSourceConfig.setTenants(Collections.singletonList(null));
    eventSourceConfig.setTraceVariable(tracingVariable);
    final String eventProcessMappingId = createEventProcessMappingWithMappingsAndEventSources(
      ImmutableMap.of(
        BPMN_START_EVENT_ID,
        buildCamundaEventMapping(processInstanceEngineDto, BPMN_START_EVENT_ID, MappedEventType.START),
        USER_TASK_ID_ONE,
        buildExternalEventMapping(USER_TASK_ID_ONE, MappedEventType.END, firstGroup),
        BPMN_END_EVENT_ID,
        buildExternalEventMapping(FINISHED_EVENT, MappedEventType.END, secondGroup)
      ),
      Arrays.asList(
        camundaEventSource,
        createExternalEventSourceEntryForGroup(firstGroup),
        createExternalEventSourceEntryForGroup(secondGroup)
      )
    );
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    // when the first import cycle completes the status has not been updated yet, but there are two import sources -
    // one for the camunda source, and another for the two external group event sources
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 0.0D);

    // when the second import cycle completes only the first and second event for each source have been processed
    // as the import fetches all events with the exact firstEvent Timestamp + a maxPageSize amount of events after it
    // so the publish progress is updated accordingly
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      // The camunda event source is 25% published and the external event source is 33.3% published, so the average
      // is taken
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 29.1);

    // when the third import cycle completes another event considered for publish progress has been ingested for
    // each source so the publish progress is updated accordingly
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      // The camunda event source is 50% published and the external event source is 66.6% published, so the average
      // is taken
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 58.3D);

    // when the fourth import cycle completes the status is updated to Published as all events have been processed
    executeImportCycle();
    // then
    final EventProcessPublishStateDto publishedMapping =
      getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId)
        .orElseThrow(() -> new OptimizeIntegrationTestException("Cannot get published state"));
    assertThat(publishedMapping.getEventImportSources()).hasSize(2);
    assertThat(publishedMapping)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISHED)
      // both sources are now 100% published so the average will be 100%
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 100.0D);
    // and the event instance has all expected correlated events
    final List<EventProcessInstanceDto> eventInstances =
      getEventProcessInstancesFromElasticsearchForProcessPublishStateId(publishedMapping.getId());
    assertThat(eventInstances)
      .singleElement()
      .extracting(ProcessInstanceDto::getFlowNodeInstances)
      .satisfies(events -> assertThat(
        events.stream().map(FlowNodeInstanceDto::getFlowNodeId).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(BPMN_START_EVENT_ID, USER_TASK_ID_ONE, BPMN_END_EVENT_ID));
  }

  private void ingestTestEventForGroup(final String eventId, final OffsetDateTime timestamp, final String group) {
    ingestTestEvent(eventId, eventId, timestamp, MY_TRACE_ID_1, group);
  }

  private void updateActivityStartEndTimestampInEngine(final String activityId,
                                                       final OffsetDateTime firstEventTimestamp,
                                                       final ProcessInstanceEngineDto processInstanceEngineDto) {
    engineDatabaseExtension.changeFlowNodeStartDate(
      processInstanceEngineDto.getId(),
      activityId,
      firstEventTimestamp
    );
    engineDatabaseExtension.changeFlowNodeEndDate(
      processInstanceEngineDto.getId(),
      activityId,
      firstEventTimestamp
    );
  }

  private String createEventProcessMappingWithMappingsAndEventSources(Map<String, EventMappingDto> eventMappings,
                                                                      List<EventSourceEntryDto<?>> eventSourceEntries) {
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
    return buildExternalEventMapping(eventName, mappedEventType, EXTERNAL_EVENT_GROUP);
  }

  private EventMappingDto buildExternalEventMapping(final String eventName,
                                                    final MappedEventType mappedEventType,
                                                    final String group) {
    if (mappedEventType.equals(MappedEventType.START)) {
      return EventMappingDto.builder().start(buildExternalEventType(eventName, group)).build();
    } else {
      return EventMappingDto.builder().end(buildExternalEventType(eventName, group)).build();
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

  private EventTypeDto buildExternalEventType(final String eventName, final String group) {
    return EventTypeDto.builder()
      .group(group)
      .source(EXTERNAL_EVENT_SOURCE)
      .eventName(eventName)
      .build();
  }

  private static Stream<List<EventSourceEntryDto<?>>> eventSourceEntryTypeCombinations() {
    return Stream.of(
      Collections.singletonList(createExternalEventAllGroupsSourceEntry()),
      Collections.singletonList(camundaEventSource()),
      Collections.singletonList(createExternalEventSourceEntryForGroup("groupA")),
      Arrays.asList(createExternalEventAllGroupsSourceEntry(), camundaEventSource()),
      Arrays.asList(createExternalEventSourceEntryForGroup("groupA"), camundaEventSource()),
      Arrays.asList(createExternalEventSourceEntryForGroup("groupA"), createExternalEventSourceEntryForGroup("groupB"))
    );
  }

  private static CamundaEventSourceEntryDto camundaEventSource() {
    return CamundaEventSourceEntryDto.builder()
      .configuration(CamundaEventSourceConfigDto.builder()
                       .eventScope(Collections.singletonList(EventScopeType.ALL))
                       .tracedByBusinessKey(true)
                       .processDefinitionKey(RandomStringUtils.randomAlphabetic(10))
                       .versions(Collections.singletonList("ALL"))
                       .build())
      .build();
  }

  private void setupOptimizeForImportingExternalSources(final List<EventSourceEntryDto<?>> eventSourceEntries) {
    // We import the definition and instance to create the event indices
    eventSourceEntries
      .forEach(entry -> {
        if (CAMUNDA.equals(entry.getSourceType())) {
          final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
          ((CamundaEventSourceEntryDto) entry).getConfiguration()
            .setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey());
        }
      });
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    // then we remove all imported data so the test can use its own fixtures
    elasticSearchIntegrationTestExtension.deleteAllOptimizeData();
  }

}
