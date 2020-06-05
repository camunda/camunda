/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.eventprocess;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.ProcessBuilder;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessState;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingResponseDto;
import org.camunda.optimize.rest.eventprocess.autogeneration.AbstractEventProcessAutogenerationIT;
import org.camunda.optimize.service.util.BpmnModelUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.model.bpmn.GatewayDirection.Converging;
import static org.camunda.bpm.model.bpmn.GatewayDirection.Diverging;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateGatewayIdForSource;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaEventTypeDto;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaProcessEndEventTypeDto;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaProcessStartEventTypeDto;

public class EventBasedProcessAutogenerationCamundaSourceIT extends AbstractEventProcessAutogenerationIT {

  private static final String PROCESS_ID = "someProcessId";
  private static final String START_EVENT_ID_1 = "startEvent1";
  private static final String START_EVENT_ID_2 = "startEvent2";
  private static final String END_EVENT_ID_1 = "endEvent1";
  private static final String END_EVENT_ID_2 = "endEvent2";

  @BeforeEach
  public void init() {
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessConfiguration()
      .getEventImport()
      .setEnabled(true);
  }

  @Test
  public void createFromCamundaSource_startEndEvents_singleStartSingleEndEvents() {
    // given
    BpmnModelInstance modelInstance = singleStartSingleEndModel();
    final EventSourceEntryDto eventSource = deployDefinitionAndCreateEventSource(
      modelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto expectedStartEvent = createCamundaEventTypeDto(PROCESS_ID, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto expectedEndEvent = createCamundaEventTypeDto(PROCESS_ID, END_EVENT_ID_1, END_EVENT_ID_1);
    final List<EventSourceEntryDto> sources = Collections.singletonList(eventSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtility.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      generatedInstance,
      Arrays.asList(expectedStartEvent, expectedEndEvent)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are of the correct type and connected to expected nodes correctly
    assertNodeConnection(idOf(expectedStartEvent), START_EVENT, idOf(expectedEndEvent), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(expectedEndEvent), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(1);
  }

  @Test
  public void createFromCamundaSource_startEndEvents_multipleStartSingleEndEvents() {
    // given
    final BpmnModelInstance modelInstance = multipleStartSingleEndModel();
    final EventSourceEntryDto eventSource = deployDefinitionAndCreateEventSource(
      modelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto expectedStartEvent1 = createCamundaEventTypeDto(PROCESS_ID, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto expectedStartEvent2 = createCamundaEventTypeDto(PROCESS_ID, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto expectedEndEvent = createCamundaEventTypeDto(PROCESS_ID, END_EVENT_ID_1, END_EVENT_ID_1);
    final List<EventSourceEntryDto> sources = Collections.singletonList(eventSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtility.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      generatedInstance,
      Arrays.asList(expectedStartEvent1, expectedStartEvent2, expectedEndEvent)
    );
    // The additional element is the converging gateway for the multiple start events
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size() + 1);

    // then the model elements are of the correct type and connected to expected nodes correctly
    final String gatewayId = generateGatewayIdForSource(eventSource, Converging);
    assertNodeConnection(idOf(expectedStartEvent1), START_EVENT, gatewayId, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(idOf(expectedStartEvent2), START_EVENT, gatewayId, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(gatewayId, EXCLUSIVE_GATEWAY, idOf(expectedEndEvent), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(expectedEndEvent), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(3);

    // and the gateways have the expected source and target events
    final Collection<Gateway> gatewaysInModel = generatedInstance.getModelElementsByType(Gateway.class);
    assertThat(gatewaysInModel).hasSize(1);
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(expectedStartEvent1), idOf(expectedStartEvent2)),
      Arrays.asList(idOf(expectedEndEvent)),
      getGatewayWithId(gatewaysInModel, gatewayId)
    );
  }

  @Test
  public void createFromCamundaSource_startEndEvents_singleStartMultipleEndEvents() {
    // given
    final BpmnModelInstance modelInstance = singleStartMultipleEndModel();
    final EventSourceEntryDto eventSource = deployDefinitionAndCreateEventSource(
      modelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto expectedStartEvent = createCamundaEventTypeDto(PROCESS_ID, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto expectedEndEvent1 = createCamundaEventTypeDto(PROCESS_ID, END_EVENT_ID_1, END_EVENT_ID_1);
    final EventTypeDto expectedEndEvent2 = createCamundaEventTypeDto(PROCESS_ID, END_EVENT_ID_2, END_EVENT_ID_2);
    final List<EventSourceEntryDto> sources = Collections.singletonList(eventSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtility.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      generatedInstance,
      Arrays.asList(expectedStartEvent, expectedEndEvent1, expectedEndEvent2)
    );
    // The additional element is the converging gateway for the multiple end events
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size() + 1);

    // then the model elements are of the correct type and connected to expected nodes correctly
    final String gatewayId = generateGatewayIdForSource(eventSource, Diverging);
    assertNodeConnection(idOf(expectedStartEvent), START_EVENT, gatewayId, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(gatewayId, EXCLUSIVE_GATEWAY, idOf(expectedEndEvent1), END_EVENT, generatedInstance);
    assertNodeConnection(gatewayId, EXCLUSIVE_GATEWAY, idOf(expectedEndEvent2), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(expectedEndEvent1), END_EVENT, null, null, generatedInstance);
    assertNodeConnection(idOf(expectedEndEvent2), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(3);

    // and the gateways have the expected source and target events
    final Collection<Gateway> gatewaysInModel = generatedInstance.getModelElementsByType(Gateway.class);
    assertThat(gatewaysInModel).hasSize(1);
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(expectedStartEvent)),
      Arrays.asList(idOf(expectedEndEvent1), idOf(expectedEndEvent2)),
      getGatewayWithId(gatewaysInModel, gatewayId)
    );
  }

  @Test
  public void createFromCamundaSource_startEndEvents_singleStartNoEndEvents() {
    // given
    final BpmnModelInstance modelInstance = singleStartNoEndModel();
    final EventSourceEntryDto eventSource = deployDefinitionAndCreateEventSource(
      modelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto expectedStartEvent = createCamundaEventTypeDto(PROCESS_ID, START_EVENT_ID_1, START_EVENT_ID_1);
    final List<EventSourceEntryDto> sources = Collections.singletonList(eventSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtility.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      generatedInstance,
      Collections.singletonList(expectedStartEvent)
    );
    // The additional element is the converging gateway for the multiple end events
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are of the correct type and connected to expected nodes correctly
    assertNodeConnection(idOf(expectedStartEvent), START_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).isEmpty();
  }

  @Test
  public void createFromCamundaSource_startEndEvents_multipleStartMultipleEndEvents() {
    // given
    final BpmnModelInstance modelInstance = multipleStartMultipleEndModel();
    final EventSourceEntryDto eventSource = deployDefinitionAndCreateEventSource(
      modelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto expectedStartEvent1 = createCamundaEventTypeDto(PROCESS_ID, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto expectedStartEvent2 = createCamundaEventTypeDto(PROCESS_ID, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto expectedEndEvent1 = createCamundaEventTypeDto(PROCESS_ID, END_EVENT_ID_1, END_EVENT_ID_1);
    final EventTypeDto expectedEndEvent2 = createCamundaEventTypeDto(PROCESS_ID, END_EVENT_ID_2, END_EVENT_ID_2);
    final List<EventSourceEntryDto> sources = Collections.singletonList(eventSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtility.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      generatedInstance,
      Arrays.asList(expectedStartEvent1, expectedStartEvent2, expectedEndEvent1, expectedEndEvent2)
    );
    // The additional elements are the gateways for the multiple start/end events
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size() + 2);

    // then the model elements are of the correct type and connected to expected nodes correctly
    final String convergingId = generateGatewayIdForSource(eventSource, Converging);
    final String divergingId = generateGatewayIdForSource(eventSource, Diverging);
    assertNodeConnection(idOf(expectedStartEvent1), START_EVENT, convergingId, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(idOf(expectedStartEvent2), START_EVENT, convergingId, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(convergingId, EXCLUSIVE_GATEWAY, divergingId, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(divergingId, EXCLUSIVE_GATEWAY, idOf(expectedEndEvent1), END_EVENT, generatedInstance);
    assertNodeConnection(divergingId, EXCLUSIVE_GATEWAY, idOf(expectedEndEvent2), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(expectedEndEvent1), END_EVENT, null, null, generatedInstance);
    assertNodeConnection(idOf(expectedEndEvent2), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(5);

    // and the gateways have the expected source and target events
    final Collection<Gateway> gatewaysInModel = generatedInstance.getModelElementsByType(Gateway.class);
    assertThat(gatewaysInModel).hasSize(2);
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(expectedStartEvent1), idOf(expectedStartEvent2)),
      Arrays.asList(divergingId),
      getGatewayWithId(gatewaysInModel, convergingId)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(convergingId),
      Arrays.asList(idOf(expectedEndEvent1), idOf(expectedEndEvent2)),
      getGatewayWithId(gatewaysInModel, divergingId)
    );
  }

  @Test
  public void createFromCamundaSource_startEndEvents_multipleStartNoEndEvents() {
    // given
    final BpmnModelInstance modelInstance = multipleStartNoEndModel();
    final EventSourceEntryDto eventSource = deployDefinitionAndCreateEventSource(
      modelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto expectedStartEvent1 = createCamundaEventTypeDto(PROCESS_ID, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto expectedStartEvent2 = createCamundaEventTypeDto(PROCESS_ID, START_EVENT_ID_2, START_EVENT_ID_2);
    final List<EventSourceEntryDto> sources = Collections.singletonList(eventSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtility.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      generatedInstance,
      Arrays.asList(expectedStartEvent1, expectedStartEvent2)
    );
    // The additional element is the gateway for the multiple start events
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size() + 1);

    // then the model elements are of the correct type and connected to expected nodes correctly
    final String gatewayId = generateGatewayIdForSource(eventSource, Converging);
    assertNodeConnection(idOf(expectedStartEvent1), START_EVENT, gatewayId, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(idOf(expectedStartEvent2), START_EVENT, gatewayId, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(gatewayId, EXCLUSIVE_GATEWAY, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(2);
  }

  @ParameterizedTest
  @MethodSource("camundaModels")
  public void createFromCamundaSource_processStartEndEvents(final BpmnModelInstance modelInstance) {
    // given
    final EventSourceEntryDto eventSource = deployDefinitionAndCreateEventSource(
      modelInstance,
      EventScopeType.PROCESS_INSTANCE
    );
    final EventTypeDto expectedStartEvent = createCamundaProcessStartEventTypeDto(PROCESS_ID);
    final EventTypeDto expectedEndEvent = createCamundaProcessEndEventTypeDto(PROCESS_ID);
    final List<EventSourceEntryDto> sources = Collections.singletonList(eventSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtility.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      generatedInstance,
      Arrays.asList(expectedStartEvent, expectedEndEvent)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are of the correct type and connected to expected nodes correctly
    assertNodeConnection(idOf(expectedStartEvent), START_EVENT, idOf(expectedEndEvent), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(expectedEndEvent), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(1);
  }

  private static Stream<BpmnModelInstance> camundaModels() {
    return Stream.of(
      singleStartSingleEndModel(),
      multipleStartSingleEndModel(),
      singleStartMultipleEndModel(),
      singleStartNoEndModel(),
      multipleStartMultipleEndModel(),
      multipleStartNoEndModel()
    );
  }

  private static BpmnModelInstance singleStartSingleEndModel() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
      .startEvent(START_EVENT_ID_1)
      .userTask(BPMN_INTERMEDIATE_EVENT_ID)
      .endEvent(END_EVENT_ID_1)
      .done();
  }

  private static BpmnModelInstance multipleStartSingleEndModel() {
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(PROCESS_ID);
    final String gateway = "someGatewayId";
    processBuilder
      .startEvent(START_EVENT_ID_1).message(START_EVENT_ID_1)
      .exclusiveGateway(gateway)
      .userTask(BPMN_INTERMEDIATE_EVENT_ID)
      .endEvent(END_EVENT_ID_1);
    processBuilder.startEvent(START_EVENT_ID_2).message(START_EVENT_ID_2)
      .connectTo(gateway);
    return processBuilder.done();
  }

  private static BpmnModelInstance singleStartMultipleEndModel() {
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(PROCESS_ID);
    final String gateway = "someGatewayId";
    processBuilder
      .startEvent(START_EVENT_ID_1)
      .exclusiveGateway(gateway)
      .condition("no", "${!goToEndEvent2}")
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent(END_EVENT_ID_1)
      .moveToNode(gateway)
      .condition("yes", "${goToEndEvent2}")
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent(END_EVENT_ID_2)
      .done();
    return processBuilder.done();
  }

  private static BpmnModelInstance singleStartNoEndModel() {
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(PROCESS_ID);
    processBuilder
      .startEvent(START_EVENT_ID_1)
      .done();
    return processBuilder.done();
  }

  private static BpmnModelInstance multipleStartMultipleEndModel() {
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(PROCESS_ID);
    final String convergingGateway = "convergingGatewayId";
    final String divergingGateway = "divergingGatewayId";
    processBuilder
      .startEvent(START_EVENT_ID_1).message(START_EVENT_ID_1)
      .exclusiveGateway(convergingGateway)
      .exclusiveGateway(divergingGateway)
      .condition("no", "${!goToEndEvent2}")
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent(END_EVENT_ID_1)
      .moveToNode(divergingGateway)
      .condition("yes", "${goToEndEvent2}")
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent(END_EVENT_ID_2);
    processBuilder.startEvent(START_EVENT_ID_2).message(START_EVENT_ID_2)
      .connectTo(convergingGateway)
      .done();
    return processBuilder.done();
  }

  private static BpmnModelInstance multipleStartNoEndModel() {
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(PROCESS_ID);
    processBuilder
      .startEvent(START_EVENT_ID_1).message(START_EVENT_ID_1);
    processBuilder.startEvent(START_EVENT_ID_2).message(START_EVENT_ID_2)
      .done();
    return processBuilder.done();
  }

}
