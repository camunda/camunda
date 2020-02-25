/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.event;

import com.google.common.collect.ImmutableList;
import org.apache.http.HttpStatus;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceType;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class EventProcessEventSourceAuthorizationIT extends AbstractEventProcessIT {

  private final String PROCESS_DEF_KEY = "aProcessDefinitionKey";
  private final String TENANT_1 = "tenant1";
  private static String processDefinitionXml;

  @BeforeAll
  public static void setup() {
    processDefinitionXml = createSimpleProcessDefinitionXml();
  }

  @Test
  public void updateEventSourcesWithAuthorization() {
    // given
    final EventProcessMappingDto eventProcessMapping = createEventProcessMapping();
    final String eventProcessMappingId = eventProcessMapping.getId();
    final EventSourceEntryDto eventSourceEntry = eventProcessClient.createSimpleCamundaEventSourceEntry(PROCESS_DEF_KEY);
    eventProcessMapping.getEventSources().add(eventSourceEntry);
    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY);

    // when
    final Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);
  }

  @Test
  public void updateEventSourcesWithoutAccessToProcess() {
    // given
    final EventProcessMappingDto eventProcessMapping = createEventProcessMapping();
    final EventSourceEntryDto eventSourceEntry = eventProcessClient.createSimpleCamundaEventSourceEntry(PROCESS_DEF_KEY, TENANT_1);
    eventProcessMapping.getEventSources().add(eventSourceEntry);

    engineIntegrationExtension.createTenant(TENANT_1);
    authorizationClient.revokeAllResourceAuthorizationsForUser(DEFAULT_USERNAME, RESOURCE_TYPE_PROCESS_DEFINITION);

    // when
    final Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMapping.getId(), eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
  }

  private EventProcessMappingDto createEventProcessMapping() {
    final EventProcessMappingDto eventProcessMappingDto = eventProcessClient.buildEventProcessMappingDto(
      processDefinitionXml);
    final String id = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    eventProcessMappingDto.setId(id);
    return eventProcessMappingDto;
  }

  private void grantAuthorizationsToDefaultUser(final String processDefinitionKey) {
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      DEFAULT_USERNAME,
      processDefinitionKey,
      RESOURCE_TYPE_PROCESS_DEFINITION
    );
  }
}
