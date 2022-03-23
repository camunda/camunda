/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.rest.EventMappingCleanupRequestDto;
import org.camunda.optimize.dto.optimize.rest.ValidationErrorResponseDto;
import org.camunda.optimize.service.events.CamundaEventService;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.rest.providers.BeanConstraintViolationExceptionHandler.THE_REQUEST_BODY_WAS_INVALID;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaProcessInstanceStartEventSuffix;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventAllGroupsSourceEntry;
import static org.camunda.optimize.test.optimize.EventProcessClient.createSimpleCamundaEventSourceEntry;

public class EventBasedProcessRestServiceMappingCleanupIT extends AbstractEventProcessIT {

  public static final String THREE_EVENT_PROCESS_DEFINITION_KEY_1 = "threeEvents1";
  public static final String THREE_EVENT_PROCESS_DEFINITION_KEY_2 = "threeEvents2";

  @Test
  public void cleanupMissingFlowNodeMappings_invalidRequest() {
    // when
    ValidationErrorResponseDto errorResponse = eventProcessClient.createCleanupEventProcessMappingsRequest(
      EventMappingCleanupRequestDto.builder()
        .mappings(null)
        .xml(null)
        .eventSources(null)
        .build()
    ).execute(ValidationErrorResponseDto.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then
    assertThat(errorResponse.getErrorMessage()).isEqualTo(THE_REQUEST_BODY_WAS_INVALID);
    assertThat(errorResponse.getValidationErrors())
      .extracting(ValidationErrorResponseDto.ValidationError::getProperty)
      .containsExactlyInAnyOrder("mappings", "xml", "eventSources");
  }

  @Test
  public void cleanupMissingFlowNodeMappings() {
    // given
    final String definitionKey = THREE_EVENT_PROCESS_DEFINITION_KEY_1;
    deployAndStartThreeEventProcessReturnXml(definitionKey);

    runEngineImportAndEventProcessing();

    // when
    final BpmnModelInstance singleEventModelInstance = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name("aProcessName")
      .startEvent(BPMN_START_EVENT_ID)
      .done();
    final Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(
      BPMN_START_EVENT_ID,
      EventMappingDto.builder().start(createCamundaEventTypeDto(BPMN_START_EVENT_ID, definitionKey)).build()
    );
    eventMappings.put(
      BPMN_INTERMEDIATE_EVENT_ID,
      EventMappingDto.builder()
        .start(createCamundaEventTypeDto(BPMN_INTERMEDIATE_EVENT_ID, definitionKey))
        .build()
    );
    eventMappings.put(
      BPMN_END_EVENT_ID,
      EventMappingDto.builder().end(createCamundaEventTypeDto(BPMN_END_EVENT_ID, definitionKey)).build()
    );

    Map<String, EventMappingDto> cleanedMapping = eventProcessClient.cleanupEventProcessMappings(
      EventMappingCleanupRequestDto.builder()
        .mappings(eventMappings)
        .xml(Bpmn.convertToString(singleEventModelInstance))
        .eventSources(ImmutableList.of(createSimpleCamundaEventSourceEntry(definitionKey, ALL_VERSIONS)))
        .build()
    );

    // then
    assertThat(cleanedMapping).containsOnlyKeys(BPMN_START_EVENT_ID);
  }

  @Test
  public void cleanupMissingFlowNodeMappings_invalidBpmnXmlSupplied() {
    // given
    final String definitionKey = THREE_EVENT_PROCESS_DEFINITION_KEY_1;
    deployAndStartThreeEventProcessReturnXml(definitionKey);

    runEngineImportAndEventProcessing();

    // when
    final Response response = eventProcessClient.createCleanupEventProcessMappingsRequest(
      EventMappingCleanupRequestDto.builder()
        .mappings(Collections.emptyMap())
        .xml("invalid BPMN Xml")
        .eventSources(ImmutableList.of(createSimpleCamundaEventSourceEntry(definitionKey, ALL_VERSIONS)))
        .build()
    ).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("invalidExternalEventSourceCombinations")
  public void cleanupMissingFlowNodeMappings_invalidExternalEventSourceCombination(final List<EventSourceEntryDto<?>> sources) {
    // when
    final Response response = eventProcessClient.createCleanupEventProcessMappingsRequest(
      EventMappingCleanupRequestDto.builder()
        .mappings(Collections.emptyMap())
        .xml(createTwoEventAndOneTaskActivitiesProcessDefinitionXml())
        .eventSources(sources)
        .build()
    ).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void cleanupMissingDataSourceMappings_externalSourceRemoved() {
    // given
    final String definitionKey = THREE_EVENT_PROCESS_DEFINITION_KEY_1;
    final String xml = deployAndStartThreeEventProcessReturnXml(definitionKey);

    final String eventName1 = "someExternalEvent1";
    final String eventName2 = "someExternalEvent2";
    embeddedOptimizeExtension.getEventService().saveEventBatch(
      ImmutableList.of(createEventDto(eventName1), createEventDto(eventName2))
    );

    runEngineImportAndEventProcessing();

    // when
    final Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(
      BPMN_START_EVENT_ID,
      EventMappingDto.builder()
        .start(createCamundaEventTypeDto(BPMN_START_EVENT_ID, definitionKey))
        .end(createExternalEventTypeDto(eventName2))
        .build()
    );
    eventMappings.put(
      BPMN_INTERMEDIATE_EVENT_ID,
      EventMappingDto.builder()
        .start(createExternalEventTypeDto(eventName1))
        .build()
    );
    eventMappings.put(
      BPMN_END_EVENT_ID,
      EventMappingDto.builder().end(createCamundaEventTypeDto(BPMN_END_EVENT_ID, definitionKey)).build()
    );

    Map<String, EventMappingDto> cleanedMapping = eventProcessClient.cleanupEventProcessMappings(
      EventMappingCleanupRequestDto.builder()
        .mappings(eventMappings)
        .xml(xml)
        .eventSources(ImmutableList.of(createSimpleCamundaEventSourceEntry(definitionKey, ALL_VERSIONS)))
        .build()
    );

    // then
    assertThat(cleanedMapping).containsOnlyKeys(BPMN_START_EVENT_ID, BPMN_END_EVENT_ID);
    assertThat(cleanedMapping.get(BPMN_START_EVENT_ID))
      .satisfies(eventMappingDto -> {
        assertThat(eventMappingDto.getStart()).isNotNull();
        assertThat(eventMappingDto.getEnd()).isNull();
      });
  }

  @Test
  public void cleanupMissingDataSourceMappings_oneCamundaSourceOutOfTwoGone() {
    // given
    final String definitionKey1 = THREE_EVENT_PROCESS_DEFINITION_KEY_1;
    final String xml = deployAndStartThreeEventProcessReturnXml(definitionKey1);
    final String definitionKey2 = THREE_EVENT_PROCESS_DEFINITION_KEY_2;
    deployAndStartThreeEventProcessReturnXml(definitionKey2);

    final String eventName1 = "someExternalEvent1";
    embeddedOptimizeExtension.getEventService().saveEventBatch(ImmutableList.of(createEventDto(eventName1)));

    runEngineImportAndEventProcessing();

    // when
    final Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(
      BPMN_START_EVENT_ID,
      EventMappingDto.builder()
        .start(createCamundaEventTypeDto(BPMN_START_EVENT_ID, definitionKey2))
        .build()
    );
    eventMappings.put(
      BPMN_INTERMEDIATE_EVENT_ID,
      EventMappingDto.builder()
        .start(createCamundaEventTypeDto(BPMN_INTERMEDIATE_EVENT_ID, definitionKey2))
        .end(createExternalEventTypeDto(eventName1))
        .build()
    );
    eventMappings.put(
      BPMN_END_EVENT_ID,
      EventMappingDto.builder().end(createCamundaEventTypeDto(BPMN_END_EVENT_ID, definitionKey1)).build()
    );

    Map<String, EventMappingDto> cleanedMapping = eventProcessClient.cleanupEventProcessMappings(
      EventMappingCleanupRequestDto.builder()
        .mappings(eventMappings)
        .xml(xml)
        .eventSources(ImmutableList.of(
          createSimpleCamundaEventSourceEntry(definitionKey1, ALL_VERSIONS),
          createExternalEventAllGroupsSourceEntry()
        ))
        .build()
    );

    // then
    assertThat(cleanedMapping).containsOnlyKeys(BPMN_INTERMEDIATE_EVENT_ID, BPMN_END_EVENT_ID);
    assertThat(cleanedMapping.get(BPMN_INTERMEDIATE_EVENT_ID))
      .satisfies(eventMappingDto -> {
        assertThat(eventMappingDto.getStart()).isNull();
        assertThat(eventMappingDto.getEnd()).isNotNull();
      });
  }

  @Test
  public void cleanupMissingDataSourceMappings_eventOutOfScope() {
    // given
    final String definitionKey = THREE_EVENT_PROCESS_DEFINITION_KEY_1;
    final String xml = deployAndStartThreeEventProcessReturnXml(definitionKey);

    final String eventName1 = "someExternalEvent1";
    embeddedOptimizeExtension.getEventService().saveEventBatch(ImmutableList.of(createEventDto(eventName1)));

    runEngineImportAndEventProcessing();

    // when events mapped to start/end and intermediate BPMN events
    final Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(
      BPMN_START_EVENT_ID,
      EventMappingDto.builder()
        .start(createCamundaEventTypeDto(BPMN_START_EVENT_ID, definitionKey))
        .build()
    );
    eventMappings.put(
      BPMN_INTERMEDIATE_EVENT_ID,
      EventMappingDto.builder()
        .start(createCamundaEventTypeDto(BPMN_INTERMEDIATE_EVENT_ID, definitionKey))
        .build()
    );
    eventMappings.put(
      BPMN_END_EVENT_ID,
      EventMappingDto.builder()
        .end(createCamundaEventTypeDto(applyCamundaProcessInstanceStartEventSuffix(definitionKey), definitionKey))
        .build()
    );

    // and the scope does not include non-start/end BPMN events
    final CamundaEventSourceEntryDto camundaEntry =
      createSimpleCamundaEventSourceEntry(definitionKey, ALL_VERSIONS);
    camundaEntry.getConfiguration()
      .setEventScope(Arrays.asList(EventScopeType.PROCESS_INSTANCE, EventScopeType.START_END));

    Map<String, EventMappingDto> cleanedMapping = eventProcessClient.cleanupEventProcessMappings(
      EventMappingCleanupRequestDto.builder()
        .mappings(eventMappings)
        .xml(xml)
        .eventSources(ImmutableList.of(camundaEntry))
        .build()
    );

    // then the BPMN intermediate event has been cleaned up
    assertThat(cleanedMapping).containsOnlyKeys(BPMN_START_EVENT_ID, BPMN_END_EVENT_ID);
  }

  @Test
  public void cleanupMissingFlowNodeMappings_eventLabelDifferencesDoNotTriggerCleanup() {
    // given
    final String definitionKey = THREE_EVENT_PROCESS_DEFINITION_KEY_1;
    deployAndStartThreeEventProcessReturnXml(definitionKey);

    runEngineImportAndEventProcessing();

    // when
    final BpmnModelInstance singleEventModelInstance = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name("aProcessName")
      .startEvent(BPMN_START_EVENT_ID)
      .done();
    final Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(
      BPMN_START_EVENT_ID,
      EventMappingDto.builder()
        .start(createCamundaEventTypeDto(BPMN_START_EVENT_ID, definitionKey)
                 .toBuilder().eventLabel("someLabel").build())
        .build()
    );
    eventMappings.put(
      BPMN_INTERMEDIATE_EVENT_ID,
      EventMappingDto.builder()
        .start(createCamundaEventTypeDto(BPMN_INTERMEDIATE_EVENT_ID, definitionKey))
        .build()
    );
    eventMappings.put(
      BPMN_END_EVENT_ID,
      EventMappingDto.builder().end(createCamundaEventTypeDto(BPMN_END_EVENT_ID, definitionKey)).build()
    );

    Map<String, EventMappingDto> cleanedMapping = eventProcessClient.cleanupEventProcessMappings(
      EventMappingCleanupRequestDto.builder()
        .mappings(eventMappings)
        .xml(Bpmn.convertToString(singleEventModelInstance))
        .eventSources(ImmutableList.of(createSimpleCamundaEventSourceEntry(definitionKey, ALL_VERSIONS)))
        .build()
    );

    // then
    assertThat(cleanedMapping).containsOnlyKeys(BPMN_START_EVENT_ID);
  }

  @Test
  public void cleanupMissingCamundaEventMappings_newProcessVersionDoesNotContainCertainEvent_allVersions() {
    // given
    final String definitionKey = THREE_EVENT_PROCESS_DEFINITION_KEY_1;
    final String xml = deployAndStartThreeEventProcessReturnXml(definitionKey);
    final BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(definitionKey)
      .camundaVersionTag("aVersionTag")
      .name("aProcessName")
      .startEvent(BPMN_START_EVENT_ID)
      // INTERMEDIATE is not present anymore
      .endEvent(BPMN_END_EVENT_ID)
      .done();
    engineIntegrationExtension.deployAndStartProcess(modelInstance);

    runEngineImportAndEventProcessing();

    // when
    final Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(
      BPMN_START_EVENT_ID,
      EventMappingDto.builder()
        .start(createCamundaEventTypeDto(BPMN_START_EVENT_ID, definitionKey))
        .build()
    );
    eventMappings.put(
      BPMN_INTERMEDIATE_EVENT_ID,
      EventMappingDto.builder()
        .start(createCamundaEventTypeDto(BPMN_INTERMEDIATE_EVENT_ID, definitionKey))
        .build()
    );
    eventMappings.put(
      BPMN_END_EVENT_ID,
      EventMappingDto.builder().end(createCamundaEventTypeDto(BPMN_END_EVENT_ID, definitionKey)).build()
    );

    Map<String, EventMappingDto> cleanedMapping = eventProcessClient.cleanupEventProcessMappings(
      EventMappingCleanupRequestDto.builder()
        .mappings(eventMappings)
        .xml(xml)
        .eventSources(ImmutableList.of(
          createSimpleCamundaEventSourceEntry(definitionKey, ALL_VERSIONS),
          createExternalEventAllGroupsSourceEntry()
        ))
        .build()
    );

    // then
    assertThat(cleanedMapping).containsOnlyKeys(BPMN_START_EVENT_ID, BPMN_END_EVENT_ID);
  }

  @Test
  public void cleanupMissingCamundaEventMappings_specificProcessVersionDoesNotContainCertainEvent() {
    // given
    final String definitionKey = THREE_EVENT_PROCESS_DEFINITION_KEY_1;
    final BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(definitionKey)
      .camundaVersionTag("aVersionTag")
      .name("aProcessName")
      .startEvent(BPMN_START_EVENT_ID)
      // INTERMEDIATE is not present in V1
      .endEvent(BPMN_END_EVENT_ID)
      .done();
    engineIntegrationExtension.deployAndStartProcess(modelInstance);
    // but in V2
    final String xml = deployAndStartThreeEventProcessReturnXml(definitionKey);

    runEngineImportAndEventProcessing();

    // when
    final Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(
      BPMN_START_EVENT_ID,
      EventMappingDto.builder()
        .start(createCamundaEventTypeDto(BPMN_START_EVENT_ID, definitionKey))
        .build()
    );
    eventMappings.put(
      BPMN_INTERMEDIATE_EVENT_ID,
      EventMappingDto.builder()
        .start(createCamundaEventTypeDto(BPMN_INTERMEDIATE_EVENT_ID, definitionKey))
        .build()
    );
    eventMappings.put(
      BPMN_END_EVENT_ID,
      EventMappingDto.builder().end(createCamundaEventTypeDto(BPMN_END_EVENT_ID, definitionKey)).build()
    );

    Map<String, EventMappingDto> cleanedMapping = eventProcessClient.cleanupEventProcessMappings(
      EventMappingCleanupRequestDto.builder()
        .mappings(eventMappings)
        .xml(xml)
        .eventSources(ImmutableList.of(
          createSimpleCamundaEventSourceEntry(definitionKey, "1"),
          createExternalEventAllGroupsSourceEntry()
        ))
        .build()
    );

    // then
    assertThat(cleanedMapping).containsOnlyKeys(BPMN_START_EVENT_ID, BPMN_END_EVENT_ID);
  }

  @Test
  public void cleanupMissingCamundaEventMappings_specificTenantDoesNotContainCertainEvent() {
    // given
    final String definitionKey = THREE_EVENT_PROCESS_DEFINITION_KEY_1;
    final String tenant1 = "tenant1";
    engineIntegrationExtension.createTenant(tenant1);
    final BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(definitionKey)
      .camundaVersionTag("aVersionTag")
      .name("aProcessName")
      .startEvent(BPMN_START_EVENT_ID)
      // INTERMEDIATE is not present for tenant 1
      .endEvent(BPMN_END_EVENT_ID)
      .done();
    engineIntegrationExtension.deployAndStartProcess(modelInstance, tenant1);
    // but for tenant 2
    final String tenant2 = "tenant2";
    final String xml = deployAndStartThreeEventProcessReturnXml(definitionKey, tenant2);

    runEngineImportAndEventProcessing();

    // when
    final Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(
      BPMN_START_EVENT_ID,
      EventMappingDto.builder()
        .start(createCamundaEventTypeDto(BPMN_START_EVENT_ID, definitionKey))
        .build()
    );
    eventMappings.put(
      BPMN_INTERMEDIATE_EVENT_ID,
      EventMappingDto.builder()
        .start(createCamundaEventTypeDto(BPMN_INTERMEDIATE_EVENT_ID, definitionKey))
        .build()
    );
    eventMappings.put(
      BPMN_END_EVENT_ID,
      EventMappingDto.builder().end(createCamundaEventTypeDto(BPMN_END_EVENT_ID, definitionKey)).build()
    );

    Map<String, EventMappingDto> cleanedMapping = eventProcessClient.cleanupEventProcessMappings(
      EventMappingCleanupRequestDto.builder()
        .mappings(eventMappings)
        .xml(xml)
        .eventSources(ImmutableList.of(
          createSimpleCamundaEventSourceEntry(definitionKey, "1", tenant1),
          createExternalEventAllGroupsSourceEntry()
        ))
        .build()
    );

    // then
    assertThat(cleanedMapping).containsOnlyKeys(BPMN_START_EVENT_ID, BPMN_END_EVENT_ID);
  }

  private EventDto createEventDto(final String eventName) {
    return EventDto.builder()
      .id(IdGenerator.getNextId())
      .eventName(eventName)
      .timestamp(Instant.now().toEpochMilli())
      .traceId(MY_TRACE_ID_1)
      .group(EXTERNAL_EVENT_GROUP)
      .source(EXTERNAL_EVENT_SOURCE)
      .data(ImmutableMap.of(VARIABLE_ID, VARIABLE_VALUE))
      .build();
  }

  private void runEngineImportAndEventProcessing() {
    importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.processEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private EventTypeDto createExternalEventTypeDto(final String eventName) {
    return EventTypeDto.builder()
      .group(EXTERNAL_EVENT_GROUP)
      .source(EXTERNAL_EVENT_SOURCE)
      .eventName(eventName)
      .build();
  }

  private EventTypeDto createCamundaEventTypeDto(final String eventName, final String definitionKey) {
    return EventTypeDto.builder()
      .group(definitionKey)
      .source(CamundaEventService.EVENT_SOURCE_CAMUNDA)
      .eventName(eventName)
      .build();
  }

  private String deployAndStartThreeEventProcessReturnXml(final String definitionKey) {
    return deployAndStartThreeEventProcessReturnXml(definitionKey, null);
  }

  private String deployAndStartThreeEventProcessReturnXml(final String definitionKey, final String tenantId) {
    final BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(definitionKey)
      .camundaVersionTag("aVersionTag")
      .name("aProcessName")
      .startEvent(BPMN_START_EVENT_ID)
      .intermediateCatchEvent(BPMN_INTERMEDIATE_EVENT_ID).condition("${true}")
      .endEvent(BPMN_END_EVENT_ID)
      .done();
    engineIntegrationExtension.deployAndStartProcess(modelInstance, tenantId);
    return Bpmn.convertToString(modelInstance);
  }

}
