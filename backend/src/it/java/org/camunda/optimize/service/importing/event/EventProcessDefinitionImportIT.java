/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class EventProcessDefinitionImportIT extends AbstractEventProcessIT {

  @Test
  public void eventProcessDefinitionIsAvailableAfterProcessReachedPublishState() {
    // given
    ingestTestEvent("startedEvent");
    ingestTestEvent("finishedEvent");

    final EventProcessMappingDto simpleEventProcessMappingDto = createSimpleEventProcessMappingDto(
      "startedEvent", "finishedEvent"
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
  public void eventProcessDefinitionIsAvailableRepublishAndPreviousIsGone() {
    // given
    ingestTestEvent("startedEvent");
    ingestTestEvent("finishedEvent");

    final EventProcessMappingDto simpleEventProcessMappingDto = createSimpleEventProcessMappingDto(
      "startedEvent", "finishedEvent"
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

  @Test
  public void eventProcessDefinitionIsGoneAfterProcessPublishCancel() {
    // given
    ingestTestEvent("startedEvent");
    ingestTestEvent("finishedEvent");

    final EventProcessMappingDto simpleEventProcessMappingDto = createSimpleEventProcessMappingDto(
      "startedEvent", "finishedEvent"
    );
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);

    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);
    final String expectedProcessDefinitionId = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId);

    executeImportCycle();
    executeImportCycle();

    // when
    eventProcessClient.cancelPublishEventProcessMapping(eventProcessMappingId);
    executeImportCycle();

    // then
    final Optional<EventProcessDefinitionDto> eventProcessDefinition =
      getEventProcessDefinitionFromElasticsearch(expectedProcessDefinitionId);

    assertThat(eventProcessDefinition).isEmpty();
  }

  @Test
  public void eventProcessDefinitionIsGoneAfterEventProcessDeleted() {
    // given
    ingestTestEvent("startedEvent");
    ingestTestEvent("finishedEvent");

    final EventProcessMappingDto simpleEventProcessMappingDto = createSimpleEventProcessMappingDto(
      "startedEvent", "finishedEvent"
    );
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);

    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);
    final String expectedProcessDefinitionId = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId);

    executeImportCycle();
    executeImportCycle();

    // when
    eventProcessClient.deleteEventProcessMapping(eventProcessMappingId);
    executeImportCycle();

    // then
    final Optional<EventProcessDefinitionDto> eventProcessDefinition =
      getEventProcessDefinitionFromElasticsearch(expectedProcessDefinitionId);

    assertThat(eventProcessDefinition).isEmpty();
  }

}
