/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.test.optimize.EventProcessClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.Optional;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

public class EventProcessDefinitionImportIT extends AbstractEventProcessIT {

  @Test
  public void eventProcessDefinitionIsAvailableAfterProcessReachedPublishState() {
    // given
    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(FINISHED_EVENT);

    final EventProcessMappingDto simpleEventProcessMappingDto = buildSimpleEventProcessMappingDto(
      STARTED_EVENT, FINISHED_EVENT
    );
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);
    final String expectedProcessDefinitionId = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId);

    executeImportCycle();
    executeImportCycle();

    // then
    final Optional<EventProcessDefinitionDto> eventProcessDefinition =
      getEventProcessDefinitionFromElasticsearch(expectedProcessDefinitionId);

    assertThat(eventProcessDefinition)
      .get()
      .isEqualTo(
        EventProcessDefinitionDto.eventProcessBuilder()
          .id(expectedProcessDefinitionId)
          .key(eventProcessMappingId)
          .version("1")
          .versionTag(null)
          .name(simpleEventProcessMappingDto.getName())
          .engine(null)
          .tenantId(null)
          .bpmn20Xml(simpleEventProcessMappingDto.getXml())
          .userTaskNames(Collections.emptyMap())
          .flowNodeNames(ImmutableMap.of(
            BPMN_START_EVENT_ID, BPMN_START_EVENT_ID,
            BPMN_END_EVENT_ID, BPMN_END_EVENT_ID
          ))
          .build()
      );
  }

  @Test
  public void eventProcessDefinitionIsAvailableForMultiplePublishedProcesses() {
    // given
    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(FINISHED_EVENT);

    final String eventProcessMappingId1 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);
    final String eventProcessMappingId2 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId1);
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId2);

    final String expectedProcessDefinitionId1 = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId1);
    final String expectedProcessDefinitionId2 = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId2);

    executeImportCycle();
    executeImportCycle();

    // then
    assertThat(getEventProcessDefinitionFromElasticsearch(expectedProcessDefinitionId1))
      .isNotEmpty();

    assertThat(getEventProcessDefinitionFromElasticsearch(expectedProcessDefinitionId2))
      .isNotEmpty();
  }

  @Test
  public void eventProcessDefinitionIsAvailableRepublishAndPreviousIsGone() {
    // given
    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(FINISHED_EVENT);

    final EventProcessMappingDto simpleEventProcessMappingDto = buildSimpleEventProcessMappingDto(
      STARTED_EVENT, FINISHED_EVENT
    );
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);

    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);
    final String previousProcessDefinitionId = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId);

    executeImportCycle();
    executeImportCycle();

    // when
    final String newName = "updatedProcess";
    simpleEventProcessMappingDto.setName(newName);
    eventProcessClient.updateEventProcessMapping(eventProcessMappingId, simpleEventProcessMappingDto);
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    final String newProcessDefinitionId = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId);

    executeImportCycle();
    executeImportCycle();

    // then
    final Optional<EventProcessDefinitionDto> previousProcessDefinition =
      getEventProcessDefinitionFromElasticsearch(previousProcessDefinitionId);
    final Optional<EventProcessDefinitionDto> newProcessDefinition =
      getEventProcessDefinitionFromElasticsearch(newProcessDefinitionId);

    assertThat(previousProcessDefinition).isEmpty();

    assertThat(newProcessDefinition)
      .get()
      .hasFieldOrPropertyWithValue(DefinitionOptimizeDto.Fields.id, newProcessDefinitionId)
      .hasFieldOrPropertyWithValue(DefinitionOptimizeDto.Fields.name, newName);
  }

  @ParameterizedTest(name = "Event Process Definition is deleted on {0}.")
  @MethodSource("cancelOrDeleteAction")
  public void eventProcessDefinitionIsDeletedOn(final String actionName,
                                                final BiConsumer<EventProcessClient, String> action) {
    // given
    final String startedEventName = STARTED_EVENT;
    ingestTestEvent(startedEventName);
    final String finishedEventName = FINISHED_EVENT;
    ingestTestEvent(finishedEventName);

    final String eventProcessMappingId = createSimpleEventProcessMapping(startedEventName, finishedEventName);

    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);
    final String expectedProcessDefinitionId = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId);

    executeImportCycle();
    executeImportCycle();

    // when
    action.accept(eventProcessClient, eventProcessMappingId);
    executeImportCycle();

    // then
    assertThat(getEventProcessDefinitionFromElasticsearch(expectedProcessDefinitionId)).isEmpty();
  }

  @ParameterizedTest(name = "Only expected instance index is deleted on {0}, others are still present.")
  @MethodSource("cancelOrDeleteAction")
  public void otherEventProcessDefinitionIsNotAffectedOn(final String actionName,
                                                         final BiConsumer<EventProcessClient, String> action) {
    // given
    final String startedEventName = STARTED_EVENT;
    ingestTestEvent(startedEventName);
    final String finishedEventName = FINISHED_EVENT;
    ingestTestEvent(finishedEventName);

    final String eventProcessMappingId1 = createSimpleEventProcessMapping(startedEventName, finishedEventName);
    final String eventProcessMappingId2 = createSimpleEventProcessMapping(startedEventName, finishedEventName);

    eventProcessClient.publishEventProcessMapping(eventProcessMappingId1);
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId2);
    final String expectedProcessDefinitionId = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId2);

    executeImportCycle();
    executeImportCycle();

    // when
    action.accept(eventProcessClient, eventProcessMappingId1);
    executeImportCycle();

    // then
    assertThat(getEventProcessDefinitionFromElasticsearch(expectedProcessDefinitionId))
      .isNotEmpty();
  }

}
