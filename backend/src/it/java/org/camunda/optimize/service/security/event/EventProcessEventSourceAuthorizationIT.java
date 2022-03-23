/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.event;

import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.optimize.EventProcessClient.createSimpleCamundaEventSourceEntryWithTenant;

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
    final CamundaEventSourceEntryDto camundaSource = createCamundaSourceEntryForImportedDefinition(PROCESS_DEF_KEY);
    final EventProcessMappingDto eventProcessMapping = createEventProcessMapping();
    eventProcessMapping.getEventSources().add(camundaSource);

    // when
    final Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMapping.getId(), eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void updateEventSourcesWithoutAccessToProcess() {
    // given
    final EventProcessMappingDto eventProcessMapping = createEventProcessMapping();
    final CamundaEventSourceEntryDto eventSourceEntry =
      createSimpleCamundaEventSourceEntryWithTenant(PROCESS_DEF_KEY, TENANT_1);
    eventProcessMapping.getEventSources().add(eventSourceEntry);

    engineIntegrationExtension.createTenant(TENANT_1);
    authorizationClient.revokeAllResourceAuthorizationsForUser(DEFAULT_USERNAME, RESOURCE_TYPE_PROCESS_DEFINITION);

    // when
    final Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMapping.getId(), eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private EventProcessMappingDto createEventProcessMapping() {
    final EventProcessMappingDto eventProcessMappingDto = eventProcessClient.buildEventProcessMappingDto(
      processDefinitionXml);
    final String id = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    eventProcessMappingDto.setId(id);
    return eventProcessMappingDto;
  }

}
