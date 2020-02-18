/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceType;
import org.camunda.optimize.dto.optimize.rest.event.EventSourceEntryRestDto;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class EventProcessRestServiceEventSourceIT extends AbstractEventProcessIT {

  private final String PROCESS_DEF_KEY_1 = "aProcessDefinitionKey_1";
  private final String PROCESS_DEF_KEY_2 = "aProcessDefinitionKey_2";
  private static String processDefinitionXml;

  @BeforeAll
  public static void setup() {
    processDefinitionXml = createSimpleProcessDefinitionXml();
  }

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
    EventSourceEntryDto eventSourceEntryDto = createCamundaEventSourceEntry(PROCESS_DEF_KEY_1);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(Collections.singletonList(eventSourceEntryDto));
    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_1);

    // when
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // then
    List<EventSourceEntryRestDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
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
    final EventSourceEntryDto camundaEventSourceEntry = createCamundaEventSourceEntry(PROCESS_DEF_KEY_1);
    eventSourceEntryDtos.add(externalEventSourceEntry);
    eventSourceEntryDtos.add(camundaEventSourceEntry);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);
    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_1);

    // when
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // then
    List<EventSourceEntryRestDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
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
    List<EventSourceEntryRestDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();

    assertThat(eventSources)
      .usingElementComparatorIgnoringFields("id")
      .containsExactly(mapToRestDto(externalEventSourceEntry));
  }

  @Test
  public void createWithDuplicateCamundaEventSource_fails() {
    // given
    final EventSourceEntryDto eventSourceEntry = createCamundaEventSourceEntry(PROCESS_DEF_KEY_1);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(Arrays.asList(eventSourceEntry, eventSourceEntry));
    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_1);

    // when
    Response response = eventProcessClient
      .createCreateEventProcessMappingRequest(eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
  }

  @Test
  public void createWithMultipleExternalEventSources_fails() {
    // given
    final EventSourceEntryDto eventSourceEntry = EventSourceEntryDto.builder().type(EventSourceType.EXTERNAL).build();
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(Arrays.asList(eventSourceEntry, eventSourceEntry));

    // when
    Response response = eventProcessClient
      .createCreateEventProcessMappingRequest(eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
  }

  @Test
  public void updateEventSource() {
    // given
    List<EventSourceEntryDto> eventSourceEntryDtos = new ArrayList<>();
    final EventSourceEntryDto externalEventSourceEntry = createExternalEventSourceEntry();
    eventSourceEntryDtos.add(externalEventSourceEntry);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);

    final EventSourceEntryDto camundaEventSourceEntry = createCamundaEventSourceEntry(PROCESS_DEF_KEY_1);
    eventProcessMapping.getEventSources().add(camundaEventSourceEntry);
    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_1);

    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // when
    camundaEventSourceEntry.setEventScope(EventScopeType.PROCESS_INSTANCE);
    performUpdateMappingRequest(eventProcessMappingId, eventProcessMapping);

    List<EventSourceEntryRestDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();

    // then
    assertThat(eventSources)
      .hasSize(2)
      .usingElementComparatorIgnoringFields("id")
      .containsExactlyInAnyOrder(mapToRestDto(camundaEventSourceEntry), mapToRestDto(externalEventSourceEntry));
  }

  @Test
  public void updateWithNoEventSource() {
    // given
    List<EventSourceEntryDto> eventSourceEntryDtos = new ArrayList<>();
    eventSourceEntryDtos.add(createExternalEventSourceEntry());
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);
    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_1);

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
    final EventSourceEntryDto camundaEventSourceEntry = createCamundaEventSourceEntry(PROCESS_DEF_KEY_1);
    List<EventSourceEntryDto> eventSourceEntryDtos = new ArrayList<>();
    eventSourceEntryDtos.add(camundaEventSourceEntry);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);
    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_1);

    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // when
    eventProcessMapping.getEventSources().add(camundaEventSourceEntry);
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMapping)
      .execute();

    // then
    List<EventSourceEntryRestDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();

    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
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
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
  }

  @Test
  public void deleteEventSource() {
    // given
    List<EventSourceEntryDto> eventSourceEntryDtos = new ArrayList<>();
    final EventSourceEntryDto externalEventSourceEntry = createExternalEventSourceEntry();
    eventSourceEntryDtos.add(externalEventSourceEntry);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);
    final EventSourceEntryDto camundaSourceEntry1 = createCamundaEventSourceEntry(PROCESS_DEF_KEY_1);
    final EventSourceEntryDto camundaSourceEntry2 = createCamundaEventSourceEntry(PROCESS_DEF_KEY_2);
    eventProcessMapping.getEventSources().add(camundaSourceEntry1);
    eventProcessMapping.getEventSources().add(camundaSourceEntry2);
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_1);
    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_2);

    // when
    eventProcessMapping.getEventSources().remove(camundaSourceEntry1);
    performUpdateMappingRequest(eventProcessMappingId, eventProcessMapping);

    // then
    List<EventSourceEntryRestDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();
    assertThat(eventSources)
      .usingElementComparatorIgnoringFields("id")
      .containsExactlyInAnyOrder(mapToRestDto(camundaSourceEntry2), mapToRestDto(externalEventSourceEntry))
      .allSatisfy(eventSourceEntryRestDto -> assertThat(eventSourceEntryRestDto.getId()).isNotBlank());
  }

  private EventProcessMappingDto createWithEventSourceEntries(final List<EventSourceEntryDto> eventSourceEntries) {
    return eventProcessClient.buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
      null,
      null,
      processDefinitionXml,
      eventSourceEntries
    );
  }

  private EventSourceEntryDto createExternalEventSourceEntry() {
    return EventSourceEntryDto.builder()
      .type(EventSourceType.EXTERNAL)
      .eventScope(EventScopeType.ALL)
      .build();
  }

  private EventSourceEntryDto createCamundaEventSourceEntry(final String processDefinitionKey) {
    return EventSourceEntryDto.builder()
      .type(EventSourceType.CAMUNDA)
      .processDefinitionKey(processDefinitionKey)
      .versions(ImmutableList.of(ALL_VERSIONS))
      .tracedByBusinessKey(true)
      .eventScope(EventScopeType.ALL)
      .build();
  }

  private EventSourceEntryRestDto mapToRestDto(final EventSourceEntryDto sourceEntryDto) {
    return EventSourceEntryRestDto.builder()
      .type(sourceEntryDto.getType())
      .versions(sourceEntryDto.getVersions())
      .tenants(sourceEntryDto.getTenants())
      .traceVariable(sourceEntryDto.getTraceVariable())
      .tracedByBusinessKey(sourceEntryDto.getTracedByBusinessKey())
      .processDefinitionKey(sourceEntryDto.getProcessDefinitionKey())
      .processDefinitionName(sourceEntryDto.getProcessDefinitionKey())
      .eventScope(sourceEntryDto.getEventScope())
      .build();
  }

  private void grantAuthorizationsToDefaultUser(final String processDefinitionKey) {
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      DEFAULT_USERNAME,
      processDefinitionKey,
      RESOURCE_TYPE_PROCESS_DEFINITION
    );
  }

  private void performUpdateMappingRequest(final String eventProcessMappingId,
                                           final EventProcessMappingDto eventProcessMappingDto) {
    eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMappingDto)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

}
