/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableList;
import org.apache.http.HttpStatus;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.rest.event.EventSourceEntryRestDto;
import org.camunda.optimize.service.importing.event.AbstractEventProcessIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
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
  public void addEventSource() {
    // given
    final EventProcessMappingDto eventProcessMapping = createEventProcessMapping();
    final String eventProcessMappingId = eventProcessMapping.getId();
    final EventSourceEntryDto eventSourceEntry = createEventSourceEntry(PROCESS_DEF_KEY_1);
    eventProcessMapping.getEventSources().add(eventSourceEntry);
    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_1);

    // when
    performUpdateMappingRequest(eventProcessMappingId, eventProcessMapping);

    List<EventSourceEntryRestDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();
    final EventSourceEntryRestDto expectedEventSourceEntryRestDto = getEventSourceAsRestDto(eventSourceEntry);

    // then
    assertThat(eventSources)
      .usingElementComparatorIgnoringFields("id")
      .containsExactly(expectedEventSourceEntryRestDto)
      .allSatisfy(eventSourceEntryRestDto -> {
        assertThat(eventSourceEntryRestDto.getId()).isNotBlank();
      });
  }

  @Test
  public void addDuplicateEventSource() {
    // given
    final EventProcessMappingDto eventProcessMapping = createEventProcessMapping();
    final String eventProcessMappingId = eventProcessMapping.getId();
    final EventSourceEntryDto eventSourceEntry = createEventSourceEntry(PROCESS_DEF_KEY_1);
    eventProcessMapping.getEventSources().add(eventSourceEntry);
    eventProcessMapping.getEventSources().add(eventSourceEntry);
    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_1);

    // when
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute();

    List<EventSourceEntryRestDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_CONFLICT);
    assertThat(eventSources).isEmpty();
  }

  @Test
  public void deleteEventSource() {
    // given
    final EventProcessMappingDto eventProcessMapping = createEventProcessMapping();
    final String eventProcessMappingId = eventProcessMapping.getId();
    final EventSourceEntryDto eventSourceEntry1 = createEventSourceEntry(PROCESS_DEF_KEY_1);
    final EventSourceEntryDto eventSourceEntry2 = createEventSourceEntry(PROCESS_DEF_KEY_2);

    eventProcessMapping.getEventSources().add(eventSourceEntry1);
    eventProcessMapping.getEventSources().add(eventSourceEntry2);

    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_1);
    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_2);

    // when
    performUpdateMappingRequest(eventProcessMappingId, eventProcessMapping);
    eventProcessMapping.getEventSources().remove(eventSourceEntry1);
    performUpdateMappingRequest(eventProcessMappingId, eventProcessMapping);

    List<EventSourceEntryRestDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();

    // then
    assertThat(eventSources).extracting(EventSourceEntryRestDto::getProcessDefinitionKey)
      .containsExactly(eventSourceEntry2.getProcessDefinitionKey());
  }

  @Test
  public void updateEventSource() {
    // given
    final EventProcessMappingDto eventProcessMapping = createEventProcessMapping();
    final String eventProcessMappingId = eventProcessMapping.getId();
    final EventSourceEntryDto eventSourceEntry1 = createEventSourceEntry(PROCESS_DEF_KEY_1);

    eventProcessMapping.getEventSources().add(eventSourceEntry1);
    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_1);

    // when
    performUpdateMappingRequest(eventProcessMappingId, eventProcessMapping);
    eventSourceEntry1.setEventScope(EventScopeType.PROCESS_INSTANCE);
    performUpdateMappingRequest(eventProcessMappingId, eventProcessMapping);

    List<EventSourceEntryRestDto> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();

    // then
    assertThat(eventSources)
      .extracting(EventSourceEntryRestDto::getProcessDefinitionKey)
      .containsExactly(eventSourceEntry1.getProcessDefinitionKey());
  }

  private EventProcessMappingDto createEventProcessMapping() {
    final EventProcessMappingDto eventProcessMappingDto = eventProcessClient.buildEventProcessMappingDto(
      processDefinitionXml);
    final String id = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    eventProcessMappingDto.setId(id);
    return eventProcessMappingDto;
  }

  private EventSourceEntryDto createEventSourceEntry(final String processDefinitionKey) {
    return EventSourceEntryDto.builder()
      .processDefinitionKey(processDefinitionKey)
      .versions(ImmutableList.of(ALL_VERSIONS))
      .tracedByBusinessKey(true)
      .eventScope(EventScopeType.ALL)
      .build();
  }

  private EventSourceEntryRestDto getEventSourceAsRestDto(final EventSourceEntryDto sourceEntryDto) {
    return EventSourceEntryRestDto.builder()
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
      .execute(HttpStatus.SC_NO_CONTENT);
  }
}
