/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.event;

import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static io.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static io.camunda.optimize.test.optimize.EventProcessClient.createEventMappingsDto;
import static io.camunda.optimize.test.optimize.EventProcessClient.createMappedEventDto;
import static io.camunda.optimize.test.optimize.EventProcessClient.createSimpleCamundaEventSourceEntry;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import io.camunda.optimize.dto.optimize.rest.EventMappingCleanupRequestDto;
import io.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import io.camunda.optimize.service.util.IdGenerator;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class EventProcessAuthorizationIT extends AbstractEventProcessIT {

  private static String simpleDiagramXml;

  @BeforeAll
  public static void setup() {
    simpleDiagramXml = createProcessDefinitionXml();
  }

  @Test
  public void getIsEventBasedProcessesEnabledWithoutAuthentication() {
    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildGetIsEventProcessEnabledRequest()
            .withoutAuthentication()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void createEventProcessMappingWithoutAuthentication() {
    // when
    final Response response =
        eventProcessClient
            .createCreateEventProcessMappingRequest(
                eventProcessClient.buildEventProcessMappingDto(simpleDiagramXml))
            .withoutAuthentication()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void updateEventProcessMappingWithoutAuthentication() {
    // when
    final EventProcessMappingDto updateDto =
        eventProcessClient.buildEventProcessMappingDto(simpleDiagramXml);
    final Response response =
        eventProcessClient
            .createUpdateEventProcessMappingRequest("doesNotMatter", updateDto)
            .withoutAuthentication()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getEventProcessMappingWithoutAuthentication() {
    // when
    final Response response =
        eventProcessClient
            .createGetEventProcessMappingRequest(IdGenerator.getNextId())
            .withoutAuthentication()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getAllEventProcessMappingWithoutAuthentication() {
    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .withoutAuthentication()
            .buildGetAllEventProcessMappingsRequests()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void createEventProcessMappingWithoutAuthorization() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessAccessUserIds().clear();

    // when
    final Response response =
        eventProcessClient
            .createCreateEventProcessMappingRequest(
                eventProcessClient.buildEventProcessMappingDto(simpleDiagramXml))
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void updateEventProcessMappingWithoutAuthorization() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessAccessUserIds().clear();

    // when
    final EventProcessMappingDto updateDto =
        eventProcessClient.buildEventProcessMappingDto(simpleDiagramXml);
    final Response response =
        eventProcessClient
            .createUpdateEventProcessMappingRequest("doesNotMatter", updateDto)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Tag(OPENSEARCH_SINGLE_TEST_FAIL_OK)
  @Test
  public void getEventProcessMappingWithIdWithoutEventSourceAuthorization() {
    // given
    final String eventProcessName = "anEventProcessName";
    final String definitionKey1 = deployAndStartProcess().getProcessDefinitionKey();
    final String definitionKey2 = deployAndStartProcess().getProcessDefinitionKey();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();

    final Map<String, EventMappingDto> processMappings =
        Collections.singletonMap(
            USER_TASK_ID_THREE,
            createEventMappingsDto(createMappedEventDto(), createMappedEventDto()));

    final CamundaEventSourceEntryDto eventSourceEntry1 =
        createCamundaEventSourceEntry(definitionKey1);
    final CamundaEventSourceEntryDto eventSourceEntry2 =
        createCamundaEventSourceEntry(definitionKey2);

    final EventProcessMappingDto eventProcessMappingDto =
        eventProcessClient.buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
            processMappings,
            eventProcessName,
            simpleDiagramXml,
            Lists.newArrayList(eventSourceEntry1, eventSourceEntry2));
    final String expectedId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // Give Kermit user access to event source 1, but not event source 2
    embeddedOptimizeExtension
        .getConfigurationService()
        .getEventBasedProcessConfiguration()
        .setAuthorizedUserIds(Lists.newArrayList(KERMIT_USER));
    authorizationClient.addUserAndGrantOptimizeAccess(KERMIT_USER);
    authorizationClient.grantSingleResourceAuthorizationsForUser(
        KERMIT_USER, definitionKey1, RESOURCE_TYPE_PROCESS_DEFINITION);

    // when
    final Response response =
        eventProcessClient
            .createGetEventProcessMappingRequest(expectedId)
            .withUserAuthentication(KERMIT_USER, KERMIT_USER)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getAllEventProcessMappingsWithPartialEventSourceAuthorization() {
    final String eventProcessName = "anEventProcessName";
    final String definitionKey1 = deployAndStartProcess().getProcessDefinitionKey();
    final String definitionKey2 = deployAndStartProcess().getProcessDefinitionKey();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();

    final Map<String, EventMappingDto> processMappings =
        Collections.singletonMap(
            USER_TASK_ID_THREE,
            createEventMappingsDto(createMappedEventDto(), createMappedEventDto()));

    final CamundaEventSourceEntryDto eventSourceEntry1 =
        createCamundaEventSourceEntry(definitionKey1);
    final CamundaEventSourceEntryDto eventSourceEntry2 =
        createCamundaEventSourceEntry(definitionKey2);

    final EventProcessMappingDto eventProcessMappingDto1 =
        eventProcessClient.buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
            processMappings,
            eventProcessName,
            simpleDiagramXml,
            Lists.newArrayList(eventSourceEntry1));
    final String expectedId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto1);

    final EventProcessMappingDto eventProcessMappingDto2 =
        eventProcessClient.buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
            processMappings,
            eventProcessName,
            simpleDiagramXml,
            Lists.newArrayList(eventSourceEntry1, eventSourceEntry2));
    eventProcessClient.createEventProcessMapping(eventProcessMappingDto2);

    // Give Kermit user access to event source 1, but not event source 2
    embeddedOptimizeExtension
        .getConfigurationService()
        .getEventBasedProcessConfiguration()
        .setAuthorizedUserIds(Lists.newArrayList(KERMIT_USER));
    authorizationClient.addUserAndGrantOptimizeAccess(KERMIT_USER);
    authorizationClient.grantSingleResourceAuthorizationsForUser(
        KERMIT_USER, definitionKey1, RESOURCE_TYPE_PROCESS_DEFINITION);

    // when
    final List<EventProcessMappingDto> allMappings =
        eventProcessClient.getAllEventProcessMappings(KERMIT_USER, KERMIT_USER);

    // then only mappings where the user has authorization to see all event sources are returned
    assertThat(allMappings).hasSize(1);
    assertThat(allMappings.get(0).getId()).isEqualTo(expectedId);
  }

  @Test
  public void cleanupMissingDataSourceMappingsWithoutEventSourceAuthorization() {
    // given
    final String definitionKey1 = "aKey1";
    final String definitionKey2 = "aKey2";

    // Give Kermit user access to event source 1, but not event source 2
    embeddedOptimizeExtension
        .getConfigurationService()
        .getEventBasedProcessConfiguration()
        .setAuthorizedUserIds(Lists.newArrayList(KERMIT_USER));
    authorizationClient.addUserAndGrantOptimizeAccess(KERMIT_USER);
    authorizationClient.grantSingleResourceAuthorizationsForUser(
        KERMIT_USER, definitionKey1, RESOURCE_TYPE_PROCESS_DEFINITION);

    // when
    final Response response =
        eventProcessClient
            .createCleanupEventProcessMappingsRequest(
                EventMappingCleanupRequestDto.builder()
                    .xml("")
                    .eventSources(
                        ImmutableList.of(
                            createCamundaEventSourceEntry(definitionKey1),
                            createCamundaEventSourceEntry(definitionKey2)))
                    .build())
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private CamundaEventSourceEntryDto createCamundaEventSourceEntry(
      final String processDefinitionKey) {
    final ProcessDefinitionOptimizeDto processDefinitionDto =
        ProcessDefinitionOptimizeDto.builder()
            .id(processDefinitionKey + "-" + "1")
            .key(processDefinitionKey)
            .name(null)
            .version("1")
            .bpmn20Xml(processDefinitionKey + "1")
            .build();
    databaseIntegrationTestExtension.addEntryToDatabase(
        PROCESS_DEFINITION_INDEX_NAME, processDefinitionDto.getId(), processDefinitionDto);
    return createSimpleCamundaEventSourceEntry(processDefinitionKey);
  }
}
