/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.eventprocess;

import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.event.EventSourceEntryResponseDto;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventSourceEntry;
import static org.camunda.optimize.test.optimize.EventProcessClient.createSimpleCamundaEventSourceEntry;

public class EventProcessRestServiceEventSourceIT extends AbstractEventProcessIT {

  private final String PROCESS_DEF_KEY_1 = "aProcessDefinitionKey_1";
  private final String PROCESS_DEF_KEY_2 = "aProcessDefinitionKey_2";

  @Test
  public void createWithNoEventSource() {
    // given
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(null);
    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_1);

    // when
    Response response = eventProcessClient
      .createCreateEventProcessMappingRequest(eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createWithCamundaEventSource() {
    // given
    final EventSourceEntryDto eventSourceEntryDto = createCamundaSourceEntryForImportedDefinition(PROCESS_DEF_KEY_1);
    final EventProcessMappingDto eventProcessMapping =
      createWithEventSourceEntries(Collections.singletonList(eventSourceEntryDto));

    // when
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // then
    List<EventSourceEntryResponseDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();

    assertThat(eventSources)
      .usingElementComparatorIgnoringFields("id")
      .containsExactlyInAnyOrder(mapToRestDto(eventSourceEntryDto))
      .allSatisfy(eventSourceEntryRestDto -> assertThat(eventSourceEntryRestDto.getId()).isNotBlank());
  }

  @Test
  public void createWithCamundaAndExternalEventSource() {
    // given
    List<EventSourceEntryDto> eventSourceEntryDtos = new ArrayList<>();
    final EventSourceEntryDto externalEventSourceEntry = createExternalEventSourceEntry();
    final EventSourceEntryDto camundaEventSourceEntry =
      createCamundaSourceEntryForImportedDefinition(PROCESS_DEF_KEY_1);
    eventSourceEntryDtos.add(externalEventSourceEntry);
    eventSourceEntryDtos.add(camundaEventSourceEntry);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);

    // when
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // then
    List<EventSourceEntryResponseDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();

    assertThat(eventSources)
      .usingElementComparatorIgnoringFields("id")
      .containsExactlyInAnyOrder(mapToRestDto(camundaEventSourceEntry), mapToRestDto(externalEventSourceEntry))
      .allSatisfy(eventSourceEntryRestDto -> assertThat(eventSourceEntryRestDto.getId()).isNotBlank());
  }

  @Test
  public void createWithExternalEventSource() {
    // given
    final EventSourceEntryDto externalEventSourceEntry = createExternalEventSourceEntry();
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(Collections.singletonList(
      externalEventSourceEntry));

    // when
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // then
    List<EventSourceEntryResponseDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();

    assertThat(eventSources)
      .usingElementComparatorIgnoringFields("id")
      .containsExactly(mapToRestDto(externalEventSourceEntry));
  }

  @Test
  public void createWithDuplicateCamundaEventSource_fails() {
    // given
    final EventSourceEntryDto eventSourceEntry = createCamundaSourceEntryForImportedDefinition(PROCESS_DEF_KEY_1);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(Arrays.asList(
      eventSourceEntry,
      eventSourceEntry
    ));

    // when
    ConflictResponseDto conflictResponseDto = eventProcessClient
      .createCreateEventProcessMappingRequest(eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

    // then
    assertThat(conflictResponseDto.getErrorCode()).isEqualTo(OptimizeConflictException.ERROR_CODE);
  }

  @Test
  public void createWithEventSourceWithoutType_fails() {
    // given
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(
      Collections.singletonList(createExternalEventSourceEntry().toBuilder().type(null).build()));
    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_1);

    // when
    Response response = eventProcessClient
      .createCreateEventProcessMappingRequest(eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute(Response.Status.BAD_REQUEST.getStatusCode());

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createWithMultipleExternalEventSources_fails() {
    // given
    final EventSourceEntryDto eventSourceEntry = createExternalEventSourceEntry();
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(Arrays.asList(
      eventSourceEntry,
      eventSourceEntry
    ));

    // when
    ConflictResponseDto conflictResponseDto = eventProcessClient
      .createCreateEventProcessMappingRequest(eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

    // then
    assertThat(conflictResponseDto.getErrorCode()).isEqualTo(OptimizeConflictException.ERROR_CODE);
  }

  @Test
  public void createWithEventProcessEventSources_fails() {
    // given
    addEventProcessDefinitionDtoToElasticsearch();
    final EventSourceEntryDto eventSourceEntry = createSimpleCamundaEventSourceEntry(
      PROCESS_DEF_KEY_1
    );
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(Arrays.asList(
      eventSourceEntry
    ));

    // when
    ConflictResponseDto conflictResponseDto = eventProcessClient
      .createCreateEventProcessMappingRequest(eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

    // then
    assertThat(conflictResponseDto.getErrorCode()).isEqualTo(OptimizeConflictException.ERROR_CODE);
  }

  @Test
  public void updateEventSource() {
    // given
    List<EventSourceEntryDto> eventSourceEntryDtos = new ArrayList<>();
    final EventSourceEntryDto externalEventSourceEntry = createExternalEventSourceEntry();
    eventSourceEntryDtos.add(externalEventSourceEntry);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);

    final EventSourceEntryDto camundaEventSourceEntry =
      createCamundaSourceEntryForImportedDefinition(PROCESS_DEF_KEY_1);
    eventProcessMapping.getEventSources().add(camundaEventSourceEntry);

    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // when
    camundaEventSourceEntry.setEventScope(Collections.singletonList(EventScopeType.PROCESS_INSTANCE));
    performUpdateMappingRequest(eventProcessMappingId, eventProcessMapping);

    List<EventSourceEntryResponseDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();

    // then
    assertThat(eventSources)
      .hasSize(2)
      .usingElementComparatorIgnoringFields("id")
      .containsExactlyInAnyOrder(mapToRestDto(camundaEventSourceEntry), mapToRestDto(externalEventSourceEntry));
  }

  @Test
  public void updateEventSource_multipleEventScopes() {
    // given
    EventSourceEntryDto eventSourceEntryDto = createCamundaSourceEntryForImportedDefinition(PROCESS_DEF_KEY_1);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(Collections.singletonList(
      eventSourceEntryDto));
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // then
    List<EventSourceEntryResponseDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();
    assertThat(eventSources)
      .usingElementComparatorIgnoringFields("id")
      .containsExactlyInAnyOrder(mapToRestDto(eventSourceEntryDto))
      .allSatisfy(eventSourceEntryRestDto -> assertThat(eventSourceEntryRestDto.getId()).isNotBlank());

    // when
    eventSourceEntryDto.setEventScope(Arrays.asList(EventScopeType.START_END, EventScopeType.PROCESS_INSTANCE));
    performUpdateMappingRequest(eventProcessMappingId, eventProcessMapping);

    // then
    List<EventSourceEntryResponseDto> updatedEventSources = eventProcessClient.getEventProcessMapping(
      eventProcessMappingId)
      .getEventSources();
    assertThat(updatedEventSources)
      .usingElementComparatorIgnoringFields("id")
      .containsExactlyInAnyOrder(mapToRestDto(eventSourceEntryDto))
      .allSatisfy(eventSourceEntryRestDto -> assertThat(eventSourceEntryRestDto.getId()).isNotBlank());
  }

  @Test
  public void updateWithNoEventSource() {
    // given
    List<EventSourceEntryDto> eventSourceEntryDtos = new ArrayList<>();
    eventSourceEntryDtos.add(createExternalEventSourceEntry());
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);

    // when
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);
    eventProcessMapping.setEventSources(null);
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMapping)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateWithDuplicateCamundaEventSource_fails() {
    // given
    final EventSourceEntryDto camundaEventSourceEntry =
      createCamundaSourceEntryForImportedDefinition(PROCESS_DEF_KEY_1);
    List<EventSourceEntryDto> eventSourceEntryDtos = new ArrayList<>();
    eventSourceEntryDtos.add(camundaEventSourceEntry);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);

    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // when
    eventProcessMapping.getEventSources().add(camundaEventSourceEntry);
    ConflictResponseDto conflictResponseDto = eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMapping)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

    // then
    List<EventSourceEntryResponseDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();

    assertThat(conflictResponseDto.getErrorCode()).isEqualTo(OptimizeConflictException.ERROR_CODE);
    assertThat(eventSources)
      .usingElementComparatorIgnoringFields("id")
      .containsExactly(mapToRestDto(camundaEventSourceEntry));
  }

  @Test
  public void updateWithMultipleExternalEventSources_fails() {
    // given
    List<EventSourceEntryDto> eventSourceEntryDtos = new ArrayList<>();
    final EventSourceEntryDto externalEventSourceEntry = createExternalEventSourceEntry();
    eventSourceEntryDtos.add(externalEventSourceEntry);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);

    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // when
    eventProcessMapping.getEventSources().add(externalEventSourceEntry);
    ConflictResponseDto conflictResponseDto = eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

    // then
    assertThat(conflictResponseDto.getErrorCode()).isEqualTo(OptimizeConflictException.ERROR_CODE);
  }

  @Test
  public void updateWithEventProcessEventSources_fails() {
    // given
    addEventProcessDefinitionDtoToElasticsearch();
    final EventSourceEntryDto eventProcessEventSource = createSimpleCamundaEventSourceEntry(
      PROCESS_DEF_KEY_1
    );
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(new ArrayList<>());

    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // when
    eventProcessMapping.getEventSources().add(eventProcessEventSource);
    ConflictResponseDto conflictResponseDto = eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

    // then
    assertThat(conflictResponseDto.getErrorCode()).isEqualTo(OptimizeConflictException.ERROR_CODE);
  }

  @Test
  public void deleteEventSource() {
    // given
    List<EventSourceEntryDto> eventSourceEntryDtos = new ArrayList<>();
    final EventSourceEntryDto externalEventSourceEntry = createExternalEventSourceEntry();
    eventSourceEntryDtos.add(externalEventSourceEntry);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);

    final EventSourceEntryDto camundaSourceEntry1 = createCamundaSourceEntryForImportedDefinition(PROCESS_DEF_KEY_1);
    final EventSourceEntryDto camundaSourceEntry2 = createCamundaSourceEntryForImportedDefinition(PROCESS_DEF_KEY_2);
    eventProcessMapping.getEventSources().add(camundaSourceEntry1);
    eventProcessMapping.getEventSources().add(camundaSourceEntry2);
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // when
    eventProcessMapping.getEventSources().remove(camundaSourceEntry1);
    performUpdateMappingRequest(eventProcessMappingId, eventProcessMapping);

    // then
    List<EventSourceEntryResponseDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();
    assertThat(eventSources)
      .usingElementComparatorIgnoringFields("id")
      .containsExactlyInAnyOrder(mapToRestDto(camundaSourceEntry2), mapToRestDto(externalEventSourceEntry))
      .allSatisfy(eventSourceEntryRestDto -> assertThat(eventSourceEntryRestDto.getId()).isNotBlank());
  }

  private void addEventProcessDefinitionDtoToElasticsearch() {
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      PROCESS_DEF_KEY_1,
      PROCESS_DEF_KEY_1,
      "1",
      Collections.singletonList(new IdentityDto(DEFAULT_USERNAME, IdentityType.USER))
    );
  }

  private EventProcessMappingDto createWithEventSourceEntries(final List<EventSourceEntryDto> eventSourceEntries) {
    return eventProcessClient.buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
      null,
      null,
      createTwoEventAndOneTaskActivitiesProcessDefinitionXml(),
      eventSourceEntries
    );
  }

  private EventSourceEntryResponseDto mapToRestDto(final EventSourceEntryDto sourceEntryDto) {
    return EventSourceEntryResponseDto.builder()
      .type(sourceEntryDto.getType())
      .versions(sourceEntryDto.getVersions())
      .tenants(sourceEntryDto.getTenants())
      .traceVariable(sourceEntryDto.getTraceVariable())
      .tracedByBusinessKey(sourceEntryDto.isTracedByBusinessKey())
      .processDefinitionKey(sourceEntryDto.getProcessDefinitionKey())
      .processDefinitionName(sourceEntryDto.getProcessDefinitionKey())
      .eventScope(sourceEntryDto.getEventScope())
      .build();
  }

  private void performUpdateMappingRequest(final String eventProcessMappingId,
                                           final EventProcessMappingDto eventProcessMappingDto) {
    eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMappingDto)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

}
