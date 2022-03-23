/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.event;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.rest.EventMappingCleanupRequestDto;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.eventprocess.EventBasedProcessRestServiceIT.createProcessDefinitionXml;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.optimize.EventProcessClient.createEventMappingsDto;
import static org.camunda.optimize.test.optimize.EventProcessClient.createMappedEventDto;
import static org.camunda.optimize.test.optimize.EventProcessClient.createSimpleCamundaEventSourceEntry;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;

public class EventProcessAuthorizationIT extends AbstractEventProcessIT {

  private static String simpleDiagramXml;

  @BeforeAll
  public static void setup() {
    simpleDiagramXml = createProcessDefinitionXml();
  }

  @Test
  public void getIsEventBasedProcessesEnabledWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetIsEventProcessEnabledRequest()
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void createEventProcessMappingWithoutAuthentication() {
    // when
    Response response = eventProcessClient
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
    EventProcessMappingDto updateDto =
      eventProcessClient.buildEventProcessMappingDto(simpleDiagramXml);
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest("doesNotMatter", updateDto)
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void deleteEventProcessMappingWithoutAuthentication() {
    // when
    Response response = eventProcessClient
      .createDeleteEventProcessMappingRequest("doesNotMatter")
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getEventProcessMappingWithoutAuthentication() {
    // when
    Response response = eventProcessClient.createGetEventProcessMappingRequest(IdGenerator.getNextId())
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getAllEventProcessMappingWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
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
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessAccessUserIds().clear();

    // when
    Response response = eventProcessClient
      .createCreateEventProcessMappingRequest(
        eventProcessClient.buildEventProcessMappingDto(simpleDiagramXml))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void updateEventProcessMappingWithoutAuthorization() {
    // given
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessAccessUserIds().clear();

    // when
    EventProcessMappingDto updateDto =
      eventProcessClient.buildEventProcessMappingDto(simpleDiagramXml);
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest("doesNotMatter", updateDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void deleteEventProcessMappingWithoutAuthorization() {
    // given
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessAccessUserIds().clear();

    // when
    Response response = eventProcessClient
      .createDeleteEventProcessMappingRequest("doesNotMatter")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getEventProcessMappingWithIdWithoutEventSourceAuthorization() {
    // given
    final String eventProcessName = "anEventProcessName";
    final String definitionKey1 = deployAndStartProcess().getProcessDefinitionKey();
    final String definitionKey2 = deployAndStartProcess().getProcessDefinitionKey();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();

    final Map<String, EventMappingDto> processMappings = Collections.singletonMap(
      USER_TASK_ID_THREE,
      createEventMappingsDto(createMappedEventDto(), createMappedEventDto())
    );

    final CamundaEventSourceEntryDto eventSourceEntry1 = createCamundaEventSourceEntry(definitionKey1);
    final CamundaEventSourceEntryDto eventSourceEntry2 = createCamundaEventSourceEntry(definitionKey2);

    final EventProcessMappingDto eventProcessMappingDto =
      eventProcessClient.buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
        processMappings,
        eventProcessName,
        simpleDiagramXml,
        Lists.newArrayList(
          eventSourceEntry1,
          eventSourceEntry2
        )
      );
    final String expectedId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // Give Kermit user access to event source 1, but not event source 2
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessConfiguration()
      .setAuthorizedUserIds(Lists.newArrayList(KERMIT_USER));
    authorizationClient.addUserAndGrantOptimizeAccess(KERMIT_USER);
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER,
      definitionKey1,
      RESOURCE_TYPE_PROCESS_DEFINITION
    );

    // when
    Response response = eventProcessClient.createGetEventProcessMappingRequest(expectedId)
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

    final Map<String, EventMappingDto> processMappings = Collections.singletonMap(
      USER_TASK_ID_THREE,
      createEventMappingsDto(createMappedEventDto(), createMappedEventDto())
    );

    final CamundaEventSourceEntryDto eventSourceEntry1 = createCamundaEventSourceEntry(definitionKey1);
    final CamundaEventSourceEntryDto eventSourceEntry2 = createCamundaEventSourceEntry(definitionKey2);

    final EventProcessMappingDto eventProcessMappingDto1 =
      eventProcessClient.buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
        processMappings,
        eventProcessName,
        simpleDiagramXml,
        Lists.newArrayList(
          eventSourceEntry1
        )
      );
    final String expectedId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto1);

    final EventProcessMappingDto eventProcessMappingDto2 =
      eventProcessClient.buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
        processMappings,
        eventProcessName,
        simpleDiagramXml,
        Lists.newArrayList(
          eventSourceEntry1,
          eventSourceEntry2
        )
      );
    eventProcessClient.createEventProcessMapping(eventProcessMappingDto2);

    // Give Kermit user access to event source 1, but not event source 2
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessConfiguration()
      .setAuthorizedUserIds(Lists.newArrayList(KERMIT_USER));
    authorizationClient.addUserAndGrantOptimizeAccess(KERMIT_USER);
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER,
      definitionKey1,
      RESOURCE_TYPE_PROCESS_DEFINITION
    );

    // when
    List<EventProcessMappingDto> allMappings = eventProcessClient.getAllEventProcessMappings(KERMIT_USER, KERMIT_USER);

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
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessConfiguration()
      .setAuthorizedUserIds(Lists.newArrayList(KERMIT_USER));
    authorizationClient.addUserAndGrantOptimizeAccess(KERMIT_USER);
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER,
      definitionKey1,
      RESOURCE_TYPE_PROCESS_DEFINITION
    );

    // when
    Response response = eventProcessClient.createCleanupEventProcessMappingsRequest(
      EventMappingCleanupRequestDto.builder()
        .xml("")
        .eventSources(ImmutableList.of(
          createCamundaEventSourceEntry(definitionKey1),
          createCamundaEventSourceEntry(definitionKey2)
        ))
        .build()
    ).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private CamundaEventSourceEntryDto createCamundaEventSourceEntry(final String processDefinitionKey) {
    final ProcessDefinitionOptimizeDto processDefinitionDto = ProcessDefinitionOptimizeDto.builder()
      .id(processDefinitionKey + "-" + "1")
      .key(processDefinitionKey)
      .name(null)
      .version("1")
      .bpmn20Xml(processDefinitionKey + "1")
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME, processDefinitionDto.getId(), processDefinitionDto
    );
    return createSimpleCamundaEventSourceEntry(processDefinitionKey);
  }

}
