/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.event;

import com.google.common.collect.Lists;
import org.apache.http.HttpStatus;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.EventBasedProcessRestServiceIT.createProcessDefinitionXml;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class EventProcessAuthorizationIT extends AbstractEventProcessIT {

  private static String simpleDiagramXml;

  @BeforeAll
  public static void setup() {
    simpleDiagramXml = createProcessDefinitionXml();
  }

  @Test
  public void getIsEventBasedProcessesEnabledWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetIsEventProcessEnabledRequest()
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void createEventProcessMappingWithoutAuthorization() {
    // when
    Response response = eventProcessClient
      .createCreateEventProcessMappingRequest(
        eventProcessClient.buildEventProcessMappingDto(simpleDiagramXml)
      )
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getEventProcessMappingWithoutAuthorization() {
    // when
    Response response = eventProcessClient.createGetEventProcessMappingRequest(IdGenerator.getNextId())
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }


  @Test
  public void getEventProcessMappingWithIdWithoutEventSourceAuthorization() {
    // given
    final String eventProcessName = "anEventProcessName";
    final String definitionKey1 = "aKey1";
    final String definitionKey2 = "aKey2";

    final Map<String, EventMappingDto> processMappings = Collections.singletonMap(
      USER_TASK_ID_THREE,
      eventProcessClient.createEventMappingsDto(eventProcessClient.createMappedEventDto(), eventProcessClient.createMappedEventDto())
    );

    final EventSourceEntryDto eventSourceEntry1 =
      eventProcessClient.createSimpleCamundaEventSourceEntry(definitionKey1);
    final EventSourceEntryDto eventSourceEntry2 =
      eventProcessClient.createSimpleCamundaEventSourceEntry(definitionKey2);

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
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void getAllEventProcessMappingWithoutAuthorization() {
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
  public void getAllEventProcessMappingsWithPartialEventSourceAuthorization() {
    final String eventProcessName = "anEventProcessName";
    final String definitionKey1 = "aKey1";
    final String definitionKey2 = "aKey2";

    final Map<String, EventMappingDto> processMappings = Collections.singletonMap(
      USER_TASK_ID_THREE,
      eventProcessClient.createEventMappingsDto(eventProcessClient.createMappedEventDto(), eventProcessClient.createMappedEventDto())
    );

    final EventSourceEntryDto eventSourceEntry1 =
      eventProcessClient.createSimpleCamundaEventSourceEntry(definitionKey1);
    final EventSourceEntryDto eventSourceEntry2 =
      eventProcessClient.createSimpleCamundaEventSourceEntry(definitionKey2);

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
    assertThat(allMappings.size()).isEqualTo(1);
    assertThat(allMappings.get(0).getId()).isEqualTo(expectedId);
  }

  @Test
  public void updateEventProcessMappingWithoutAuthorization() {
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
  public void deleteEventProcessMappingWithoutAuthorization() {
    // when
    Response response = eventProcessClient
      .createDeleteEventProcessMappingRequest("doesNotMatter")
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }
}
