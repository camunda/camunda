/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess.autogeneration;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessState;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingResponseDto;
import org.camunda.optimize.service.util.BpmnModelUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.model.bpmn.GatewayDirection.Converging;
import static org.camunda.bpm.model.bpmn.GatewayDirection.Diverging;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateGatewayIdForNode;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventAllGroupsSourceEntry;
import static org.camunda.optimize.test.optimize.EventProcessClient.createMappedEventDto;

public class EventBasedProcessAutogenerationExternalSourceIT extends AbstractEventProcessAutogenerationIT {

  @BeforeEach
  public void init() {
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessConfiguration()
      .getEventImport()
      .setEnabled(true);
  }

  @Test
  public void createFromExternalSource_noClearStartEventFound_bestFitCanBeMapped() {
    // given
    final Instant now = Instant.now();
    final String traceIdOne = "tracingIdOne";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, traceIdOne, now),
      createCloudEventOfType(EVENT_B, traceIdOne, now.plusSeconds(10)),
      createCloudEventOfType(EVENT_C, traceIdOne, now.plusSeconds(20))
    ));
    final String traceIdTwo = "tracingIdTwo";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_B, traceIdTwo, now),
      createCloudEventOfType(EVENT_A, traceIdTwo, now.plusSeconds(10))
    ));
    final List<EventSourceEntryDto<?>> externalSource = Collections.singletonList(
      createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(externalSource);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then the created process is configured correctly
    assertProcessMappingConfiguration(processMapping, externalSource, EventProcessState.MAPPED);
    assertThat(processMapping.getMappings()).hasSize(3);
    assertThat(processMapping.getXml()).isNotNull();
  }

  @Test
  public void createFromExternalSource_noClearEndEventFound_bestFitCanBeMapped() {
    // given
    final Instant now = Instant.now();
    final String traceIdOne = "tracingIdOne";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, traceIdOne, now),
      createCloudEventOfType(EVENT_B, traceIdOne, now.plusSeconds(10)),
      createCloudEventOfType(EVENT_C, traceIdOne, now.plusSeconds(20))
    ));
    final String traceIdTwo = "tracingIdTwo";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_C, traceIdTwo, now),
      createCloudEventOfType(EVENT_B, traceIdTwo, now.plusSeconds(10))
    ));
    final List<EventSourceEntryDto<?>> externalSource = Collections.singletonList(
      createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(externalSource);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then the created process is configured correctly
    assertProcessMappingConfiguration(processMapping, externalSource, EventProcessState.MAPPED);
    assertThat(processMapping.getMappings()).hasSize(2);
    assertThat(processMapping.getXml()).isNotNull();
  }

  @Test
  public void createFromExternalSource_simpleLinearModel() {
    // given
    final String traceId = "tracingId";
    final Instant now = Instant.now();
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, traceId, now),
      createCloudEventOfType(EVENT_B, traceId, now.plusSeconds(10)),
      createCloudEventOfType(EVENT_C, traceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_D, traceId, now.plusSeconds(30))
    ));
    final List<EventSourceEntryDto<?>> externalSource = Collections.singletonList(
      createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(externalSource);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then the created process is configured correctly
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance modelInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, externalSource, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(mappings, modelInstance, Arrays.asList(EVENT_A, EVENT_B, EVENT_C, EVENT_D));
    assertThat(modelInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are of the correct type and connected to expected nodes correctly
    assertNodeConnection(idOf(EVENT_A), START_EVENT, idOf(EVENT_B), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, idOf(EVENT_C), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_C), INTERMEDIATE_EVENT, idOf(EVENT_D), END_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_D), END_EVENT, null, null, modelInstance);
    // and the expected number of sequence flows exists
    assertThat(modelInstance.getModelElementsByType(SequenceFlow.class)).hasSize(3);

    final List<EventTypeDto> eventCounts = getEventCountsAsEventTypeDtos(externalSource);
    assertThat(eventCounts).containsAll(getMappedEventTypeDtosFromMappings(mappings));
  }

  @Test
  public void createFromExternalSource_simpleLinearModel_eventsWithNoGroupProperty() {
    // given
    final EventTypeDto eventA = createMappedEventDto().toBuilder().group(null).build();
    final EventTypeDto eventB = createMappedEventDto().toBuilder().group(null).build();
    final EventTypeDto eventC = createMappedEventDto().toBuilder().group(null).build();
    final EventTypeDto eventD = createMappedEventDto().toBuilder().group(null).build();
    final String traceId = "tracingId";
    final Instant now = Instant.now();
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(eventA, traceId, now),
      createCloudEventOfType(eventB, traceId, now.plusSeconds(10)),
      createCloudEventOfType(eventC, traceId, now.plusSeconds(20)),
      createCloudEventOfType(eventD, traceId, now.plusSeconds(30))
    ));
    final List<EventSourceEntryDto<?>> externalSource = Collections.singletonList(
      createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(externalSource);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then the created process is configured correctly
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance modelInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, externalSource, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(mappings, modelInstance, Arrays.asList(eventA, eventB, eventC, eventD));
    assertThat(modelInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are of the correct type and connected to expected nodes correctly
    assertNodeConnection(idOf(eventA), START_EVENT, idOf(eventB), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(idOf(eventB), INTERMEDIATE_EVENT, idOf(eventC), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(idOf(eventC), INTERMEDIATE_EVENT, idOf(eventD), END_EVENT, modelInstance);
    assertNodeConnection(idOf(eventD), END_EVENT, null, null, modelInstance);
    // and the expected number of sequence flows exists
    assertThat(modelInstance.getModelElementsByType(SequenceFlow.class)).hasSize(3);

    final List<EventTypeDto> eventCounts = getEventCountsAsEventTypeDtos(externalSource);
    assertThat(eventCounts).containsAll(getMappedEventTypeDtosFromMappings(mappings));
  }

  @Test
  public void createFromExternalSource_illegalCharactersUsedInEventProperties_modelCanStillBeGenerated() {
    // given
    final String traceId = "tracingId";
    final Instant now = Instant.now();
    final EventTypeDto illegalCharEventType = EventTypeDto.builder()
      .group("illegalChars !@£$%^&*()+")
      .source("legalChars _.-")
      .eventName(" whitespace \t\n")
      .build();
    final CloudEventRequestDto illegalCharEvent = createCloudEventOfType(
      illegalCharEventType,
      traceId,
      now
    );
    final String expectedIllegalCharEventNodeId = "event_illegalChars------------_legalChars-_.-_-whitespace---";
    ingestEventAndProcessTraces(Arrays.asList(
      illegalCharEvent,
      createCloudEventOfType(EVENT_A, traceId, now.plusSeconds(10))
    ));
    final List<EventSourceEntryDto<?>> externalSource = Collections.singletonList(
      createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(externalSource);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then the created process is configured correctly
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance modelInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, externalSource, EventProcessState.MAPPED);

    // then a model is generated without error
    assertThat(processMapping.getXml()).isNotNull();
    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(mappings, modelInstance, Arrays.asList(illegalCharEventType, EVENT_A));
    assertThat(modelInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // the ID of the node of the illegal character event has been corrected
    assertThat(mappings).containsKey(expectedIllegalCharEventNodeId);
    final ModelElementInstance modelElementById = modelInstance.getModelElementById(expectedIllegalCharEventNodeId);
    assertThat(modelElementById).isNotNull();
  }

  @Test
  public void createFromExternalSource_twoStartEvents() {
    // given
    final Instant now = Instant.now();
    final String firstTraceId = "firstTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, firstTraceId, now),
      createCloudEventOfType(EVENT_C, firstTraceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_D, firstTraceId, now.plusSeconds(30))
    ));
    final String secondTraceId = "secondTraceTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_B, secondTraceId, now),
      createCloudEventOfType(EVENT_C, secondTraceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_D, secondTraceId, now.plusSeconds(30))
    ));
    final List<EventSourceEntryDto<?>> externalSource = Collections.singletonList(
      createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(externalSource);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then the created process is configured correctly
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance modelInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, externalSource, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(mappings, modelInstance, Arrays.asList(EVENT_A, EVENT_B, EVENT_C, EVENT_D));
    // The extra flow node is the added gateway
    assertThat(modelInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size() + 1);

    // then the model elements are of the correct type and connected to expected nodes correctly
    final String expectedGatewayId = generateGatewayIdForNode(EVENT_C, Converging);
    assertNodeConnection(idOf(EVENT_A), START_EVENT, expectedGatewayId, EXCLUSIVE_GATEWAY, modelInstance);
    assertNodeConnection(idOf(EVENT_B), START_EVENT, expectedGatewayId, EXCLUSIVE_GATEWAY, modelInstance);
    assertNodeConnection(expectedGatewayId, EXCLUSIVE_GATEWAY, idOf(EVENT_C), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_C), INTERMEDIATE_EVENT, idOf(EVENT_D), END_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_D), END_EVENT, null, null, modelInstance);

    // and the expected number of sequence flows exists
    assertThat(modelInstance.getModelElementsByType(SequenceFlow.class)).hasSize(4);

    // and the gateways have the expected source and target events
    final Collection<Gateway> gatewaysInModel = modelInstance.getModelElementsByType(Gateway.class);
    assertThat(gatewaysInModel).hasSize(1);
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_A), idOf(EVENT_B)),
      Arrays.asList(idOf(EVENT_C)),
      getGatewayWithId(gatewaysInModel, expectedGatewayId)
    );
  }

  @Test
  public void createFromExternalSource_twoEndEvents() {
    // given
    final Instant now = Instant.now();
    final String firstTraceId = "firstTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, firstTraceId, now),
      createCloudEventOfType(EVENT_B, firstTraceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_C, firstTraceId, now.plusSeconds(30))
    ));
    final String secondTraceId = "secondTraceTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, secondTraceId, now),
      createCloudEventOfType(EVENT_B, secondTraceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_D, secondTraceId, now.plusSeconds(30))
    ));
    final List<EventSourceEntryDto<?>> externalSource = Collections.singletonList(
      createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(externalSource);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then the created process is configured correctly
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance modelInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, externalSource, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(mappings, modelInstance, Arrays.asList(EVENT_A, EVENT_B, EVENT_C, EVENT_D));
    // The extra flow node is the added gateway
    assertThat(modelInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size() + 1);

    // then the model elements are of the correct type and connected to sequence flows correctly
    final String expectedGatewayId = generateGatewayIdForNode(EVENT_B, Diverging);
    assertNodeConnection(idOf(EVENT_A), START_EVENT, idOf(EVENT_B), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, expectedGatewayId, EXCLUSIVE_GATEWAY, modelInstance);
    assertNodeConnection(expectedGatewayId, EXCLUSIVE_GATEWAY, idOf(EVENT_C), END_EVENT, modelInstance);
    assertNodeConnection(expectedGatewayId, EXCLUSIVE_GATEWAY, idOf(EVENT_D), END_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_D), END_EVENT, null, null, modelInstance);
    // and the expected number of sequence flows exists
    assertThat(modelInstance.getModelElementsByType(SequenceFlow.class)).hasSize(4);

    // and the gateways have the expected source and target events
    final Collection<Gateway> gatewaysInModel = modelInstance.getModelElementsByType(Gateway.class);
    assertThat(gatewaysInModel).hasSize(1);
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_B)),
      Arrays.asList(idOf(EVENT_C), idOf(EVENT_D)),
      getGatewayWithId(gatewaysInModel, generateGatewayIdForNode(EVENT_B, Diverging))
    );
  }

  @Test
  public void createFromExternalSource_twoIntermediateEventsSplitWithGateway() {
    // given
    final Instant now = Instant.now();
    final String firstTraceId = "firstTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, firstTraceId, now),
      createCloudEventOfType(EVENT_B, firstTraceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_D, firstTraceId, now.plusSeconds(30))
    ));
    final String secondTraceId = "secondTraceTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, secondTraceId, now),
      createCloudEventOfType(EVENT_C, secondTraceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_D, secondTraceId, now.plusSeconds(30))
    ));
    final List<EventSourceEntryDto<?>> externalSource = Collections.singletonList(
      createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(externalSource);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then the created process is configured correctly
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance modelInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, externalSource, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(mappings, modelInstance, Arrays.asList(EVENT_A, EVENT_B, EVENT_C, EVENT_D));
    // The extra flow nodes are the added gateways
    assertThat(modelInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size() + 2);

    // then the model elements are of the correct type and connected to sequence flows correctly
    final String divergingGatewayId = generateGatewayIdForNode(EVENT_A, Diverging);
    final String convergingGatewayId = generateGatewayIdForNode(EVENT_D, Converging);
    assertNodeConnection(idOf(EVENT_A), START_EVENT, divergingGatewayId, EXCLUSIVE_GATEWAY, modelInstance);
    assertNodeConnection(divergingGatewayId, EXCLUSIVE_GATEWAY, idOf(EVENT_B), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(divergingGatewayId, EXCLUSIVE_GATEWAY, idOf(EVENT_C), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, convergingGatewayId, EXCLUSIVE_GATEWAY, modelInstance);
    assertNodeConnection(idOf(EVENT_C), INTERMEDIATE_EVENT, convergingGatewayId, EXCLUSIVE_GATEWAY, modelInstance);
    assertNodeConnection(convergingGatewayId, EXCLUSIVE_GATEWAY, idOf(EVENT_D), END_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_D), END_EVENT, null, null, modelInstance);
    // and the expected number of sequence flows exists
    assertThat(modelInstance.getModelElementsByType(SequenceFlow.class)).hasSize(6);

    // and the gateways have the expected source and target events
    final Collection<Gateway> gatewaysInModel = modelInstance.getModelElementsByType(Gateway.class);
    assertThat(gatewaysInModel).hasSize(2);
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_A)),
      Arrays.asList(idOf(EVENT_B), idOf(EVENT_C)),
      getGatewayWithId(gatewaysInModel, divergingGatewayId)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_B), idOf(EVENT_C)),
      Arrays.asList(idOf(EVENT_D)),
      getGatewayWithId(gatewaysInModel, convergingGatewayId)
    );
  }

  @Test
  public void createFromExternalSource_twoUnconnectedPaths() {
    // given
    final Instant now = Instant.now();
    final String firstTraceId = "firstTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, firstTraceId, now),
      createCloudEventOfType(EVENT_B, firstTraceId, now.plusSeconds(20))
    ));
    final String secondTraceId = "secondTraceTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_C, secondTraceId, now),
      createCloudEventOfType(EVENT_D, secondTraceId, now.plusSeconds(30))
    ));
    final List<EventSourceEntryDto<?>> externalSource = Collections.singletonList(
      createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(externalSource);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then the created process is configured correctly
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance modelInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, externalSource, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(mappings, modelInstance, Arrays.asList(EVENT_A, EVENT_B, EVENT_C, EVENT_D));
    assertThat(modelInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are of the correct type and connected to sequence flows correctly
    assertNodeConnection(idOf(EVENT_A), START_EVENT, idOf(EVENT_B), END_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_B), END_EVENT, null, null, modelInstance);
    assertNodeConnection(idOf(EVENT_C), START_EVENT, idOf(EVENT_D), END_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_D), END_EVENT, null, null, modelInstance);
    // and the expected number of sequence flows exists
    assertThat(modelInstance.getModelElementsByType(SequenceFlow.class)).hasSize(2);
  }

  @Test
  public void createFromExternalSource_singleEventForAllTraces() {
    // given
    final Instant now = Instant.now();
    final String firstTraceId = "firstTraceId";
    ingestEventAndProcessTraces(Collections.singletonList(createCloudEventOfType(EVENT_A, firstTraceId, now)));
    final List<EventSourceEntryDto<?>> externalSource = Collections.singletonList(
      createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(externalSource);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then the created process is configured correctly
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance modelInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, externalSource, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(mappings, modelInstance, Collections.singletonList(EVENT_A));
    assertThat(modelInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are of the correct type
    assertNodeConnection(idOf(EVENT_A), START_EVENT, null, null, modelInstance);
    // and the expected number of sequence flows exists
    assertThat(modelInstance.getModelElementsByType(SequenceFlow.class)).isEmpty();
  }

  @Test
  public void createFromExternalSource_modelContainingLoop() {
    // given
    final Instant now = Instant.now();
    final String firstTraceId = "firstTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, firstTraceId, now),
      createCloudEventOfType(EVENT_B, firstTraceId, now.plusSeconds(10)),
      createCloudEventOfType(EVENT_C, firstTraceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_D, firstTraceId, now.plusSeconds(30)),
      createCloudEventOfType(EVENT_E, firstTraceId, now.plusSeconds(40))
    ));
    final String secondTraceId = "secondTraceTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_D, secondTraceId, now),
      createCloudEventOfType(EVENT_B, secondTraceId, now.plusSeconds(10))
    ));
    final List<EventSourceEntryDto<?>> externalSource = Collections.singletonList(
      createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(externalSource);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then the created process is configured correctly
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance modelInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, externalSource, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      modelInstance,
      Arrays.asList(EVENT_A, EVENT_B, EVENT_C, EVENT_D, EVENT_E)
    );
    // The extra flow nodes are the added gateways
    assertThat(modelInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size() + 2);

    // then the model elements are of the correct type and connected to sequence flows correctly
    final String divergingGatewayId = generateGatewayIdForNode(EVENT_D, Diverging);
    final String convergingGatewayId = generateGatewayIdForNode(EVENT_B, Converging);
    assertNodeConnection(idOf(EVENT_A), START_EVENT, convergingGatewayId, EXCLUSIVE_GATEWAY, modelInstance);
    assertNodeConnection(convergingGatewayId, EXCLUSIVE_GATEWAY, idOf(EVENT_B), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, idOf(EVENT_C), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_C), INTERMEDIATE_EVENT, idOf(EVENT_D), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_D), INTERMEDIATE_EVENT, divergingGatewayId, EXCLUSIVE_GATEWAY, modelInstance);
    assertNodeConnection(divergingGatewayId, EXCLUSIVE_GATEWAY, convergingGatewayId, EXCLUSIVE_GATEWAY, modelInstance);
    assertNodeConnection(divergingGatewayId, EXCLUSIVE_GATEWAY, idOf(EVENT_E), END_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_E), END_EVENT, null, null, modelInstance);
    // and the expected number of sequence flows exists
    assertThat(modelInstance.getModelElementsByType(SequenceFlow.class)).hasSize(7);

    // and the gateways have the expected source and target events
    final Collection<Gateway> gateways = modelInstance.getModelElementsByType(Gateway.class);
    assertThat(gateways).hasSize(2);
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_A), divergingGatewayId),
      Arrays.asList(idOf(EVENT_B)),
      getGatewayWithId(gateways, convergingGatewayId)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_D)),
      Arrays.asList(convergingGatewayId, idOf(EVENT_E)),
      getGatewayWithId(gateways, divergingGatewayId)
    );
  }

  @Test
  public void createFromExternalSource_exclusiveGatewaysAdded() {
    // given
    final Instant now = Instant.now();
    final String firstTraceId = "firstTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, firstTraceId, now),
      createCloudEventOfType(EVENT_B, firstTraceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_D, firstTraceId, now.plusSeconds(30))
    ));
    final String secondTraceId = "secondTraceTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, secondTraceId, now),
      createCloudEventOfType(EVENT_C, secondTraceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_D, secondTraceId, now.plusSeconds(30))
    ));
    final List<EventSourceEntryDto<?>> externalSource = Collections.singletonList(
      createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(externalSource);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then the created process is configured correctly
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance modelInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, externalSource, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(mappings, modelInstance, Arrays.asList(EVENT_A, EVENT_B, EVENT_C, EVENT_D));
    // The extra flow nodes are the added gateways
    assertThat(modelInstance.getModelElementsByType(FlowNode.class).size()).isEqualTo(mappings.size() + 2);

    // then the model elements are of the correct type and connected to sequence flows correctly
    final String divergingGatewayId = generateGatewayIdForNode(EVENT_A, Diverging);
    final String convergingGatewayId = generateGatewayIdForNode(EVENT_D, Converging);
    assertNodeConnection(idOf(EVENT_A), START_EVENT, divergingGatewayId, EXCLUSIVE_GATEWAY, modelInstance);
    assertNodeConnection(divergingGatewayId, EXCLUSIVE_GATEWAY, idOf(EVENT_B), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(divergingGatewayId, EXCLUSIVE_GATEWAY, idOf(EVENT_C), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, convergingGatewayId, EXCLUSIVE_GATEWAY, modelInstance);
    assertNodeConnection(idOf(EVENT_C), INTERMEDIATE_EVENT, convergingGatewayId, EXCLUSIVE_GATEWAY, modelInstance);
    assertNodeConnection(convergingGatewayId, EXCLUSIVE_GATEWAY, idOf(EVENT_D), END_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_D), END_EVENT, null, null, modelInstance);
    // and the expected number of sequence flows exists
    assertThat(modelInstance.getModelElementsByType(SequenceFlow.class)).hasSize(6);

    // and the gateways have the expected source and target events
    final Collection<Gateway> gatewaysInModel = modelInstance.getModelElementsByType(Gateway.class);
    assertThat(gatewaysInModel).hasSize(2);
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_A)),
      Arrays.asList(idOf(EVENT_B), idOf(EVENT_C)),
      getGatewayWithId(gatewaysInModel, divergingGatewayId)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_B), idOf(EVENT_C)),
      Arrays.asList(idOf(EVENT_D)),
      getGatewayWithId(gatewaysInModel, convergingGatewayId)
    );
  }

  @Test
  public void createFromExternalSource_parallelGatewaysAdded() {
    // given
    final Instant now = Instant.now();
    final String firstTraceId = "firstTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, firstTraceId, now),
      createCloudEventOfType(EVENT_B, firstTraceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_C, firstTraceId, now.plusSeconds(30)),
      createCloudEventOfType(EVENT_D, firstTraceId, now.plusSeconds(40))
    ));
    final String secondTraceId = "secondTraceTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, secondTraceId, now),
      createCloudEventOfType(EVENT_C, secondTraceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_B, secondTraceId, now.plusSeconds(30)),
      createCloudEventOfType(EVENT_D, secondTraceId, now.plusSeconds(40))
    ));
    final List<EventSourceEntryDto<?>> externalSource = Collections.singletonList(
      createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(externalSource);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then the created process is configured correctly
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance modelInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, externalSource, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(mappings, modelInstance, Arrays.asList(EVENT_A, EVENT_B, EVENT_C, EVENT_D));
    // The extra flow nodes are the added gateways
    assertThat(modelInstance.getModelElementsByType(FlowNode.class).size()).isEqualTo(mappings.size() + 2);

    // then the model elements are of the correct type and connected to sequence flows correctly
    final String divergingGatewayId = generateGatewayIdForNode(EVENT_A, Diverging);
    final String convergingGatewayId = generateGatewayIdForNode(EVENT_D, Converging);
    assertNodeConnection(idOf(EVENT_A), START_EVENT, divergingGatewayId, PARALLEL_GATEWAY, modelInstance);
    assertNodeConnection(divergingGatewayId, PARALLEL_GATEWAY, idOf(EVENT_B), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(divergingGatewayId, PARALLEL_GATEWAY, idOf(EVENT_C), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, convergingGatewayId, PARALLEL_GATEWAY, modelInstance);
    assertNodeConnection(idOf(EVENT_C), INTERMEDIATE_EVENT, convergingGatewayId, PARALLEL_GATEWAY, modelInstance);
    assertNodeConnection(convergingGatewayId, PARALLEL_GATEWAY, idOf(EVENT_D), END_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_D), END_EVENT, null, null, modelInstance);
    // and the expected number of sequence flows exists
    assertThat(modelInstance.getModelElementsByType(SequenceFlow.class)).hasSize(6);

    // and the gateways have the expected source and target events
    final Collection<Gateway> gatewaysInModel = modelInstance.getModelElementsByType(Gateway.class);
    assertThat(gatewaysInModel).hasSize(2);
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_A)),
      Arrays.asList(idOf(EVENT_B), idOf(EVENT_C)),
      getGatewayWithId(gatewaysInModel, divergingGatewayId)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_B), idOf(EVENT_C)),
      Arrays.asList(idOf(EVENT_D)),
      getGatewayWithId(gatewaysInModel, convergingGatewayId)
    );
  }

  @Test
  public void createFromExternalSource_inclusiveGatewaysAdded() {
    // given
    final Instant now = Instant.now();
    final String firstTraceId = "firstTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, firstTraceId, now),
      createCloudEventOfType(EVENT_B, firstTraceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_C, firstTraceId, now.plusSeconds(30)),
      createCloudEventOfType(EVENT_D, firstTraceId, now.plusSeconds(40))
    ));
    final String secondTraceId = "secondTraceTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, secondTraceId, now),
      createCloudEventOfType(EVENT_C, secondTraceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_B, secondTraceId, now.plusSeconds(30)),
      createCloudEventOfType(EVENT_D, secondTraceId, now.plusSeconds(40))
    ));
    final String thirdTraceId = "thirdTraceTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, thirdTraceId, now),
      createCloudEventOfType(EVENT_B, thirdTraceId, now.plusSeconds(30)),
      createCloudEventOfType(EVENT_D, thirdTraceId, now.plusSeconds(40))
    ));
    final List<EventSourceEntryDto<?>> externalSource = Collections.singletonList(
      createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(externalSource);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then the created process is configured correctly
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance modelInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, externalSource, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(mappings, modelInstance, Arrays.asList(EVENT_A, EVENT_B, EVENT_C, EVENT_D));
    // The extra flow nodes are the added gateways
    assertThat(modelInstance.getModelElementsByType(FlowNode.class).size()).isEqualTo(mappings.size() + 2);

    // then the model elements are of the correct type and connected to sequence flows correctly
    final String divergingGatewayId = generateGatewayIdForNode(EVENT_A, Diverging);
    final String convergingGatewayId = generateGatewayIdForNode(EVENT_D, Converging);
    assertNodeConnection(idOf(EVENT_A), START_EVENT, divergingGatewayId, INCLUSIVE_GATEWAY, modelInstance);
    assertNodeConnection(divergingGatewayId, INCLUSIVE_GATEWAY, idOf(EVENT_B), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(divergingGatewayId, INCLUSIVE_GATEWAY, idOf(EVENT_C), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, convergingGatewayId, INCLUSIVE_GATEWAY, modelInstance);
    assertNodeConnection(idOf(EVENT_C), INTERMEDIATE_EVENT, convergingGatewayId, INCLUSIVE_GATEWAY, modelInstance);
    assertNodeConnection(convergingGatewayId, INCLUSIVE_GATEWAY, idOf(EVENT_D), END_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_D), END_EVENT, null, null, modelInstance);
    // and the expected number of sequence flows exists
    assertThat(modelInstance.getModelElementsByType(SequenceFlow.class)).hasSize(6);

    // and the gateways have the expected source and target events
    final Collection<Gateway> gatewaysInModel = modelInstance.getModelElementsByType(Gateway.class);
    assertThat(gatewaysInModel).hasSize(2);
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_A)),
      Arrays.asList(idOf(EVENT_B), idOf(EVENT_C)),
      getGatewayWithId(gatewaysInModel, divergingGatewayId)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_B), idOf(EVENT_C)),
      Arrays.asList(idOf(EVENT_D)),
      getGatewayWithId(gatewaysInModel, convergingGatewayId)
    );
  }

  @Test
  public void createFromExternalSource_onlyCompletedTracesConsideredForGatewaySelection() {
    // given
    final Instant now = Instant.now();
    final String firstTraceId = "firstTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, firstTraceId, now),
      createCloudEventOfType(EVENT_B, firstTraceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_C, firstTraceId, now.plusSeconds(30)),
      createCloudEventOfType(EVENT_D, firstTraceId, now.plusSeconds(40)),
      createCloudEventOfType(EVENT_E, firstTraceId, now.plusSeconds(50))
    ));
    final String secondTraceId = "secondTraceTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, secondTraceId, now),
      createCloudEventOfType(EVENT_C, secondTraceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_B, secondTraceId, now.plusSeconds(30)),
      createCloudEventOfType(EVENT_D, secondTraceId, now.plusSeconds(40)),
      createCloudEventOfType(EVENT_E, secondTraceId, now.plusSeconds(50))
    ));
    // If this trace was considered in the gateway selection, we would expect inclusive gateways as it has no EVENT_C
    final String thirdTraceId = "thirdTraceTraceId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, thirdTraceId, now),
      createCloudEventOfType(EVENT_B, thirdTraceId, now.plusSeconds(30)),
      createCloudEventOfType(EVENT_D, thirdTraceId, now.plusSeconds(40))
    ));
    final List<EventSourceEntryDto<?>> externalSource = Collections.singletonList(
      createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(externalSource);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then the created process is configured correctly
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance modelInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, externalSource, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      modelInstance,
      Arrays.asList(EVENT_A, EVENT_B, EVENT_C, EVENT_D, EVENT_E)
    );
    // The extra flow nodes are the added gateways
    assertThat(modelInstance.getModelElementsByType(FlowNode.class).size()).isEqualTo(mappings.size() + 2);

    // then the model elements are of the correct type and connected to sequence flows correctly
    // The gateways being Parallel shows that the third event trace isn't considered for gateway selection
    final String divergingGatewayId = generateGatewayIdForNode(EVENT_A, Diverging);
    final String convergingGatewayId = generateGatewayIdForNode(EVENT_D, Converging);
    assertNodeConnection(idOf(EVENT_A), START_EVENT, divergingGatewayId, PARALLEL_GATEWAY, modelInstance);
    assertNodeConnection(divergingGatewayId, PARALLEL_GATEWAY, idOf(EVENT_B), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(divergingGatewayId, PARALLEL_GATEWAY, idOf(EVENT_C), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, convergingGatewayId, PARALLEL_GATEWAY, modelInstance);
    assertNodeConnection(idOf(EVENT_C), INTERMEDIATE_EVENT, convergingGatewayId, PARALLEL_GATEWAY, modelInstance);
    assertNodeConnection(convergingGatewayId, PARALLEL_GATEWAY, idOf(EVENT_D), INTERMEDIATE_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_D), INTERMEDIATE_EVENT, idOf(EVENT_E), END_EVENT, modelInstance);
    assertNodeConnection(idOf(EVENT_E), END_EVENT, null, null, modelInstance);
    // and the expected number of sequence flows exists
    assertThat(modelInstance.getModelElementsByType(SequenceFlow.class)).hasSize(7);

    // and the gateways have the expected source and target events
    final Collection<Gateway> gatewaysInModel = modelInstance.getModelElementsByType(Gateway.class);
    assertThat(gatewaysInModel).hasSize(2);
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_A)),
      Arrays.asList(idOf(EVENT_B), idOf(EVENT_C)),
      getGatewayWithId(gatewaysInModel, divergingGatewayId)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_B), idOf(EVENT_C)),
      Arrays.asList(idOf(EVENT_D)),
      getGatewayWithId(gatewaysInModel, convergingGatewayId)
    );
  }

}
