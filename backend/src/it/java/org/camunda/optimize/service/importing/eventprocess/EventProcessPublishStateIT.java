/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import org.camunda.optimize.dto.optimize.query.event.EventImportSourceDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessState;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingRestDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.optimize.EventProcessClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

public class EventProcessPublishStateIT extends AbstractEventProcessIT {

  @Test
  public void afterPublishPublishStateIsCreated() {
    // given
    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId)).isNotEmpty();
  }

  @Test
  public void publishMappingWithNoEventSourcesFails() {
    // given
    final EventProcessMappingDto eventProcessMappingDto =
      eventProcessClient.buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
      Collections.emptyMap(),
      "failToPublish",
      createSimpleProcessDefinitionXml(),
      Collections.emptyList()
    );
    eventProcessMappingDto.setMappings(Collections.singletonMap(BPMN_START_EVENT_ID, EventMappingDto.builder()
      .end(EventTypeDto.builder().group(EVENT_GROUP).source(EVENT_SOURCE).eventName("someEventName").build())
      .build()));
    String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

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

    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);
    final String expectedProcessDefinitionId = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId);

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

    final String eventProcessMappingId1 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);
    final String eventProcessMappingId2 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    eventProcessClient.publishEventProcessMapping(eventProcessMappingId1);
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId2);
    final String expectedProcessDefinitionId = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId2);

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
    final EventProcessMappingRestDto storedEventProcessMapping = eventProcessClient.getEventProcessMapping(
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

    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(FINISHED_EVENT);

    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    // when the first import cycle completes the status has not been updated yet
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 0.0D);

    // when the second import cycle completes the status reflects the result of the previous cycle
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 33.3D);

    // when the third import cycle completes the status reflects the result of the previous cycle
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
  public void eventsIngestedAfterPublishDontAffectProgress() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventImportConfiguration().setMaxPageSize(1);

    final OffsetDateTime timeBaseLine = LocalDateUtil.getCurrentDateTime();
    final OffsetDateTime firstEventTimestamp = timeBaseLine.minusSeconds(60);
    LocalDateUtil.setCurrentTime(firstEventTimestamp);
    ingestTestEvent(STARTED_EVENT, firstEventTimestamp);

    final OffsetDateTime lastEventTimestamp = timeBaseLine.minusSeconds(30);
    LocalDateUtil.setCurrentTime(lastEventTimestamp);
    ingestTestEvent(FINISHED_EVENT, lastEventTimestamp);

    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final OffsetDateTime publishDateTime = LocalDateUtil.getCurrentDateTime();
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    LocalDateUtil.setCurrentTime(timeBaseLine.plusSeconds(10));
    ingestTestEvent(STARTED_EVENT, LocalDateUtil.getCurrentDateTime());
    ingestTestEvent(STARTED_EVENT, LocalDateUtil.getCurrentDateTime());

    executeImportCycle();
    executeImportCycle();

    // then
    final EventProcessMappingRestDto storedEventProcessMapping = eventProcessClient.getEventProcessMapping(
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
                .lastImportedEventTimestamp(firstEventTimestamp)
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

    final String eventProcessMappingId = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final OffsetDateTime publishDateTime = LocalDateUtil.getCurrentDateTime();
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    executeImportCycle();
    executeImportCycle();

    // then
    final EventProcessMappingRestDto storedEventProcessMapping = eventProcessClient.getEventProcessMapping(
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

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final OffsetDateTime publishDateTime = LocalDateUtil.getCurrentDateTime();
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    executeImportCycle();

    executeImportCycle();

    ingestTestEvent(STARTED_EVENT, OffsetDateTime.now());
    executeImportCycle();

    // then
    final EventProcessMappingRestDto storedEventProcessMapping = eventProcessClient.getEventProcessMapping(
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

}
