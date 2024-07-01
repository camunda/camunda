/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.security.event;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
// import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
// import static
// io.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
// import static
// io.camunda.optimize.test.optimize.EventProcessClient.createSimpleCamundaEventSourceEntryWithTenant;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
// import io.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
// import io.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
// import jakarta.ws.rs.core.Response;
// import org.junit.jupiter.api.BeforeAll;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class EventProcessEventSourceAuthorizationIT extends AbstractEventProcessIT {
//
//   private final String PROCESS_DEF_KEY = "aProcessDefinitionKey";
//   private final String TENANT_1 = "tenant1";
//   private static String processDefinitionXml;
//
//   @BeforeAll
//   public static void setup() {
//     processDefinitionXml = createSimpleProcessDefinitionXml();
//   }
//
//   @Test
//   public void updateEventSourcesWithAuthorization() {
//     // given
//     final CamundaEventSourceEntryDto camundaSource =
//         createCamundaSourceEntryForImportedDefinition(PROCESS_DEF_KEY);
//     final EventProcessMappingDto eventProcessMapping = createEventProcessMapping();
//     eventProcessMapping.getEventSources().add(camundaSource);
//
//     // when
//     final Response response =
//         eventProcessClient
//             .createUpdateEventProcessMappingRequest(
//                 eventProcessMapping.getId(), eventProcessMapping)
//             .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
//             .execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
//   }
//
//   @Test
//   public void updateEventSourcesWithoutAccessToProcess() {
//     // given
//     final EventProcessMappingDto eventProcessMapping = createEventProcessMapping();
//     final CamundaEventSourceEntryDto eventSourceEntry =
//         createSimpleCamundaEventSourceEntryWithTenant(PROCESS_DEF_KEY, TENANT_1);
//     eventProcessMapping.getEventSources().add(eventSourceEntry);
//
//     engineIntegrationExtension.createTenant(TENANT_1);
//     authorizationClient.revokeAllResourceAuthorizationsForUser(
//         DEFAULT_USERNAME, RESOURCE_TYPE_PROCESS_DEFINITION);
//
//     // when
//     final Response response =
//         eventProcessClient
//             .createUpdateEventProcessMappingRequest(
//                 eventProcessMapping.getId(), eventProcessMapping)
//             .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
//             .execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   private EventProcessMappingDto createEventProcessMapping() {
//     final EventProcessMappingDto eventProcessMappingDto =
//         eventProcessClient.buildEventProcessMappingDto(processDefinitionXml);
//     final String id = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
//     eventProcessMappingDto.setId(id);
//     return eventProcessMappingDto;
//   }
// }
