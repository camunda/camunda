/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess.autogeneration;

import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessState;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingResponseDto;
import org.camunda.optimize.service.util.BpmnModelUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaEventTypeDto;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateModelGatewayIdForSource;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

public class EventBasedProcessAutogenerationCamundaSourceSingleAndMultipleStartIT extends AbstractEventProcessAutogenerationIT {

  @SuppressWarnings(UNUSED)
  @ParameterizedTest(name = "modelDescription: {0}, tracedByBusinessKey: {2}")
  @MethodSource("singleStartEndModels")
  public void createFromCamundaSource_startEndEvents_singleStartSingleEndEvents(final String modelDescription,
                                                                                final BpmnModelInstance modelInstance,
                                                                                final boolean tracedByBusinessKey) {
    // given
    final String varName = "varName";
    final CamundaEventSourceEntryDto eventSource = deployDefinitionWithInstanceAndCreateEventSource(
      modelInstance,
      EventScopeType.START_END,
      ImmutableMap.of(varName, "varVa1")
    );
    processEventTracesAndSequences();
    eventSource.getConfiguration().setTracedByBusinessKey(tracedByBusinessKey);
    if (!tracedByBusinessKey) {
      eventSource.getConfiguration().setTraceVariable(varName);
    }
    final EventTypeDto expectedStartEvent = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto expectedEndEvent = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);
    final List<EventSourceEntryDto<?>> sources = Collections.singletonList(eventSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
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

    // and that the mapped events all exist in the returned event counts list
    final List<EventTypeDto> eventCounts = getEventCountsAsEventTypeDtos(sources);
    assertThat(eventCounts).containsAll(getMappedEventTypeDtosFromMappings(mappings));
  }

  @Test
  public void createFromCamundaSource_startEndEvents_multipleStartSingleEndEvents() {
    // given
    final BpmnModelInstance modelInstance = multipleStartSingleEndModel();
    final CamundaEventSourceEntryDto eventSource = deployDefinitionWithInstanceAndCreateEventSource(
      modelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto expectedStartEvent1 = createCamundaEventTypeDto(
      PROCESS_ID_1,
      START_EVENT_ID_1,
      START_EVENT_ID_1
    );
    final EventTypeDto expectedStartEvent2 = createCamundaEventTypeDto(
      PROCESS_ID_1,
      START_EVENT_ID_2,
      START_EVENT_ID_2
    );
    final EventTypeDto expectedEndEvent = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);
    final List<EventSourceEntryDto<?>> sources = Collections.singletonList(eventSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
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
    final String gatewayId = generateModelGatewayIdForSource(eventSource, Converging);
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
    final CamundaEventSourceEntryDto eventSource = deployDefinitionWithInstanceAndCreateEventSource(
      modelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto expectedStartEvent = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto expectedEndEvent1 = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);
    final EventTypeDto expectedEndEvent2 = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_2, END_EVENT_ID_2);
    final List<EventSourceEntryDto<?>> sources = Collections.singletonList(eventSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
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
    final String gatewayId = generateModelGatewayIdForSource(eventSource, Diverging);
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

  private static Stream<Arguments> singleStartEndModels() {
    return Stream.of(
      Arguments.of("singleStartSingleEndModel", singleStartSingleEndModel(), true),
      Arguments.of("singleStartSingleEndModel", singleStartSingleEndModel(), false),
      Arguments.of("embeddedSubprocessModel", embeddedSubprocessModel(), true),
      Arguments.of("embeddedSubprocessModel", embeddedSubprocessModel(), false),
      Arguments.of("multipleEmbeddedSubprocessModel", multipleEmbeddedSubprocessModel(), true),
      Arguments.of("multipleEmbeddedSubprocessModel", multipleEmbeddedSubprocessModel(), false)
    );
  }
}
