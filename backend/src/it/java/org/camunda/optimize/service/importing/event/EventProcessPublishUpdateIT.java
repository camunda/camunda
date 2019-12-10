/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessState;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

public class EventProcessPublishUpdateIT extends AbstractEventProcessIT {

  @Test
  public void progressIsZeroOnNoCycleRun() {
    // given
    final String eventProcessId = createSimpleEventProcessMapping("start", "end");

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final OffsetDateTime publishDateTime = LocalDateUtil.getCurrentDateTime();
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId))
      .get()
      .hasNoNullFieldsOrProperties()
      .isEqualToIgnoringGivenFields(
        EventProcessPublishStateDto.builder()
          .processMappingId(eventProcessId)
          .state(EventProcessState.PUBLISH_PENDING)
          .publishDateTime(publishDateTime)
          .lastImportedEventIngestDateTime(OffsetDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneId.systemDefault()))
          .deleted(false)
          .publishProgress(0.0D)
          .build(),
        EventProcessPublishStateDto.Fields.id,
        EventProcessPublishStateDto.Fields.xml,
        EventProcessPublishStateDto.Fields.mappings
      );
  }

  @Test
  public void partialPublishingProgress() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getIngestedEventImportConfiguration().setMaxPageSize(1);

    final OffsetDateTime timeBaseLine = LocalDateUtil.getCurrentDateTime();
    final OffsetDateTime firstEventTimestamp = timeBaseLine.minusSeconds(60);
    LocalDateUtil.setCurrentTime(firstEventTimestamp);
    final String ingestedStartEventName = "startedEvent";
    ingestTestEvent(ingestedStartEventName, firstEventTimestamp);

    final OffsetDateTime lastEventTimestamp = timeBaseLine.minusSeconds(30);
    LocalDateUtil.setCurrentTime(lastEventTimestamp);
    final String ingestedEndEventName = "finishedEvent";
    ingestTestEvent(ingestedEndEventName, lastEventTimestamp);

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    // when the first import cycle completes the status has not been updated yet
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 0.0D);

    // when the second import cycle completes the status reflects the result of the previous cycle
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISH_PENDING)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 50.0D);

    // when the third import cycle completes the status is updated to Published as all events have been processed
    executeImportCycle();
    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId))
      .get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.state, EventProcessState.PUBLISHED)
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishProgress, 100.0D);
  }

  @Test
  public void eventsIngestedAfterPublishDontAffectProgress() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getIngestedEventImportConfiguration().setMaxPageSize(1);

    final OffsetDateTime timeBaseLine = LocalDateUtil.getCurrentDateTime();
    final OffsetDateTime firstEventTimestamp = timeBaseLine.minusSeconds(60);
    LocalDateUtil.setCurrentTime(firstEventTimestamp);
    final String ingestedStartEventName = "startedEvent";
    ingestTestEvent(ingestedStartEventName, firstEventTimestamp);

    final OffsetDateTime lastEventTimestamp = timeBaseLine.minusSeconds(30);
    LocalDateUtil.setCurrentTime(lastEventTimestamp);
    final String ingestedEndEventName = "finishedEvent";
    ingestTestEvent(ingestedEndEventName, lastEventTimestamp);

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final OffsetDateTime publishDateTime = LocalDateUtil.getCurrentDateTime();
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    LocalDateUtil.setCurrentTime(timeBaseLine.plusSeconds(10));
    ingestTestEvent(ingestedStartEventName, LocalDateUtil.getCurrentDateTime());
    ingestTestEvent(ingestedStartEventName, LocalDateUtil.getCurrentDateTime());

    executeImportCycle();
    executeImportCycle();

    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId))
      .get()
      .hasNoNullFieldsOrProperties()
      .isEqualToIgnoringGivenFields(
        EventProcessPublishStateDto.builder()
          .processMappingId(eventProcessId)
          .state(EventProcessState.PUBLISH_PENDING)
          .publishDateTime(publishDateTime)
          .lastImportedEventIngestDateTime(firstEventTimestamp)
          .deleted(false)
          .publishProgress(50.0D)
          .build(),
        EventProcessPublishStateDto.Fields.id,
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
    final String ingestedStartEventName = "startedEvent";
    ingestTestEvent(ingestedStartEventName, firstEventTimestamp);

    final OffsetDateTime lastEventTimestamp = timeBaseLine.minusSeconds(30);
    LocalDateUtil.setCurrentTime(lastEventTimestamp);
    final String ingestedEndEventName = "finishedEvent";
    ingestTestEvent(ingestedEndEventName, lastEventTimestamp);

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final OffsetDateTime publishDateTime = LocalDateUtil.getCurrentDateTime();
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();
    executeImportCycle();

    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId))
      .get()
      .hasNoNullFieldsOrProperties()
      .isEqualToIgnoringGivenFields(
        EventProcessPublishStateDto.builder()
          .processMappingId(eventProcessId)
          .state(EventProcessState.PUBLISHED)
          .publishDateTime(publishDateTime)
          .lastImportedEventIngestDateTime(lastEventTimestamp)
          .deleted(false)
          .publishProgress(100.0D)
          .build(),
        EventProcessPublishStateDto.Fields.id,
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
    final String ingestedStartEventName = "startedEvent";
    ingestTestEvent(ingestedStartEventName, firstEventTimestamp);

    final OffsetDateTime lastEventTimestamp = timeBaseLine.minusSeconds(30);
    LocalDateUtil.setCurrentTime(lastEventTimestamp);
    final String ingestedEndEventName = "finishedEvent";
    ingestTestEvent(ingestedEndEventName, lastEventTimestamp);

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final OffsetDateTime publishDateTime = LocalDateUtil.getCurrentDateTime();
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    executeImportCycle();

    ingestTestEvent(ingestedStartEventName, OffsetDateTime.now());
    executeImportCycle();

    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId))
      .get()
      .hasNoNullFieldsOrProperties()
      .isEqualToIgnoringGivenFields(
        EventProcessPublishStateDto.builder()
          .processMappingId(eventProcessId)
          .state(EventProcessState.PUBLISHED)
          .publishDateTime(publishDateTime)
          .lastImportedEventIngestDateTime(lastEventTimestamp)
          .deleted(false)
          .publishProgress(100.0D)
          .build(),
        EventProcessPublishStateDto.Fields.id,
        EventProcessPublishStateDto.Fields.xml,
        EventProcessPublishStateDto.Fields.mappings
      );
  }

}
