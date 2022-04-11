/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess.autogeneration;

import com.google.common.collect.ImmutableMap;
import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.ProcessBuilder;
import org.camunda.bpm.model.bpmn.instance.CallActivity;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessState;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.EventProcessService;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.camunda.optimize.service.util.EventDtoBuilderUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.test.optimize.EventProcessClient;

import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateNodeId;
import static org.camunda.optimize.test.optimize.EventProcessClient.createMappedEventDto;

public abstract class AbstractEventProcessAutogenerationIT extends AbstractEventProcessIT {

  protected static final Class<StartEvent> START_EVENT = StartEvent.class;
  protected static final Class<IntermediateCatchEvent> INTERMEDIATE_EVENT = IntermediateCatchEvent.class;
  protected static final Class<CallActivity> CALL_ACTIVITY = CallActivity.class;
  protected static final Class<EndEvent> END_EVENT = EndEvent.class;
  protected static final Class<ExclusiveGateway> EXCLUSIVE_GATEWAY = ExclusiveGateway.class;
  protected static final Class<ParallelGateway> PARALLEL_GATEWAY = ParallelGateway.class;
  protected static final Class<InclusiveGateway> INCLUSIVE_GATEWAY = InclusiveGateway.class;

  protected static final EventTypeDto EVENT_A = createMappedEventDto();
  protected static final EventTypeDto EVENT_B = createMappedEventDto();
  protected static final EventTypeDto EVENT_C = createMappedEventDto();
  protected static final EventTypeDto EVENT_D = createMappedEventDto();
  protected static final EventTypeDto EVENT_E = createMappedEventDto();

  protected static final String PROCESS_ID_1 = "someProcessId";
  protected static final String PROCESS_ID_2 = "someOtherProcessId";
  protected static final String PROCESS_ID_3 = "anotherProcessId";
  protected static final String START_EVENT_ID_1 = "startEvent1";
  protected static final String START_EVENT_ID_2 = "startEvent2";
  protected static final String START_EVENT_ID_3 = "startEvent3";
  protected static final String END_EVENT_ID_1 = "endEvent1";
  protected static final String END_EVENT_ID_2 = "endEvent2";
  protected static final String END_EVENT_ID_3 = "endEvent3";
  protected static final String DEFAULT_VARIABLE = "default";
  protected static final String STRING_VAR = "stringVarName";
  protected static final String STRING_VAR_VAL = "stringVarVal";

  protected void assertProcessMappingConfiguration(final EventProcessMappingResponseDto eventProcessMapping,
                                                   final List<EventSourceEntryDto<?>> externalSources,
                                                   final EventProcessState processState) {
    assertThat(eventProcessMapping.getName()).isEqualTo(EventProcessService.DEFAULT_AUTOGENERATED_PROCESS_NAME);
    assertThat(eventProcessMapping.getState()).isEqualTo(processState);
    assertThat(eventProcessMapping.getEventSources()).extracting(
      source -> source.getConfiguration().getEventScope(),
      EventSourceEntryDto::getSourceType
    ).containsExactlyInAnyOrderElementsOf(
      externalSources.stream()
        .map(source -> Tuple.tuple(source.getConfiguration().getEventScope(), source.getSourceType()))
        .collect(Collectors.toList()));
  }

  protected EventProcessMappingResponseDto autogenerateProcessAndGetMappingResponse(final EventProcessMappingCreateRequestDto createRequestDto) {
    String processId = eventProcessClient.createCreateEventProcessMappingRequest(createRequestDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode()).getId();
    return eventProcessClient.getEventProcessMapping(processId);
  }

  protected EventProcessMappingCreateRequestDto buildAutogenerateCreateRequestDto(final List<EventSourceEntryDto<?>> sources) {
    return EventProcessMappingCreateRequestDto.eventProcessMappingCreateBuilder()
      .eventSources(sources)
      .autogenerate(true)
      .build();
  }

  protected CamundaEventSourceEntryDto deployDefinitionWithInstanceAndCreateEventSource(final BpmnModelInstance modelInstance,
                                                                                        final EventScopeType eventScopeType) {
    return deployDefinitionWithInstanceAndCreateEventSource(
      modelInstance,
      eventScopeType,
      ImmutableMap.of(DEFAULT_VARIABLE, true, STRING_VAR, STRING_VAR_VAL)
    );
  }

  protected CamundaEventSourceEntryDto deployDefinitionWithInstanceAndCreateEventSource(final BpmnModelInstance modelInstance,
                                                                                        final EventScopeType eventScopeType,
                                                                                        final Map<String, Object> variables) {
    return deployDefinitionWithInstanceAndCreateEventSource(modelInstance, eventScopeType, variables, null);
  }

  protected CamundaEventSourceEntryDto deployDefinitionWithInstanceAndCreateEventSource(final BpmnModelInstance modelInstance,
                                                                                        final EventScopeType eventScopeType,
                                                                                        final String businessKey) {
    return deployDefinitionWithInstanceAndCreateEventSource(
      modelInstance,
      eventScopeType,
      ImmutableMap.of(DEFAULT_VARIABLE, true, STRING_VAR, STRING_VAR_VAL),
      businessKey
    );
  }

  protected CamundaEventSourceEntryDto deployDefinitionWithInstanceAndCreateEventSource(final BpmnModelInstance modelInstance,
                                                                                        final EventScopeType eventScopeType,
                                                                                        final Map<String, Object> variables,
                                                                                        final String businessKey) {
    final ProcessInstanceEngineDto processInstanceEngineDto = deployDefinitionWithInstance(
      modelInstance,
      variables,
      businessKey
    );
    return createCamundaSourceEntry(processInstanceEngineDto.getProcessDefinitionKey(), eventScopeType);
  }

  protected ProcessInstanceEngineDto deployDefinitionWithInstance(final BpmnModelInstance modelInstance,
                                                                  final Map<String, Object> variables,
                                                                  final String businessKey) {
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(
        modelInstance,
        variables,
        businessKey,
        null
      );
    importEngineEntities();
    return processInstanceEngineDto;
  }

  protected CamundaEventSourceEntryDto deployDefinitionAndCreateEventSource(final BpmnModelInstance modelInstance,
                                                                            final EventScopeType eventScopeType) {
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployDefinition(modelInstance, null);
    return createCamundaSourceEntry(processDefinitionEngineDto.getKey(), eventScopeType);
  }

  protected ProcessDefinitionEngineDto deployDefinition(final BpmnModelInstance modelInstance,
                                                        final String tenantId) {
    final ProcessDefinitionEngineDto processDefinitionEngineDto =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
    importEngineEntities();
    return processDefinitionEngineDto;
  }

  protected CamundaEventSourceEntryDto createCamundaSourceEntry(final String definitionKey,
                                                                final EventScopeType eventScopeType) {
    final CamundaEventSourceEntryDto camundaEntry =
      EventProcessClient.createSimpleCamundaEventSourceEntry(definitionKey);
    camundaEntry.getConfiguration().setEventScope(Collections.singletonList(eventScopeType));
    return camundaEntry;
  }

  protected void assertNodeConnection(final String firstNodeId,
                                      final Class<? extends FlowNode> expectedNodeType,
                                      final String expectedConnectedNodeId,
                                      final Class<? extends FlowNode> expectedConnectedNodeType,
                                      final BpmnModelInstance modelInstance) {
    final ModelElementInstance element = modelInstance.getModelElementById(firstNodeId);
    assertThat(expectedNodeType.isAssignableFrom(element.getElementType().getInstanceType())).isTrue();
    final Collection<SequenceFlow> outgoingSequenceFlows = expectedNodeType.cast(element).getOutgoing();
    if (expectedConnectedNodeId == null) {
      // We expect no outgoing sequence flows for end events
      assertThat(outgoingSequenceFlows).isEmpty();
    } else {
      final Optional<FlowNode> connectedNode = outgoingSequenceFlows.stream()
        .map(SequenceFlow::getTarget)
        .filter(targetNodes -> targetNodes.getId().equals(expectedConnectedNodeId))
        .findFirst();
      assertThat(connectedNode).isPresent();
      assertThat(expectedConnectedNodeType.isAssignableFrom(connectedNode.get().getElementType().getInstanceType()))
        .isTrue();
    }
  }

  protected void assertCorrectMappingsAndContainsEvents(final Map<String, EventMappingDto> mappings,
                                                        final BpmnModelInstance bpmnModelInstance,
                                                        final List<EventTypeDto> expectedMappedEvents) {
    assertThat(mappings.values().stream()
                 .flatMap(mapping -> Stream.of(mapping.getStart(), mapping.getEnd()))
                 .filter(Objects::nonNull)
                 .collect(Collectors.toList()))
      .containsExactlyInAnyOrderElementsOf(expectedMappedEvents);
    assertThat(mappings)
      .allSatisfy((id, mapping) -> assertThat(bpmnModelInstance.getModelElementById(id).getElementType()).isNotNull());
  }

  protected void assertGatewayWithSourcesAndTargets(final List<String> expectedSourceNodeIds,
                                                    final List<String> expectedTargetNodeIds,
                                                    final Gateway gateway) {
    final List<String> incomingNodeIds = gateway.getIncoming()
      .stream()
      .map(SequenceFlow::getSource)
      .map(FlowNode::getId)
      .collect(Collectors.toList());
    final List<String> outgoingNodeIds = gateway.getOutgoing()
      .stream()
      .map(SequenceFlow::getTarget)
      .map(FlowNode::getId)
      .collect(Collectors.toList());
    assertThat(incomingNodeIds).containsExactlyInAnyOrderElementsOf(expectedSourceNodeIds);
    assertThat(outgoingNodeIds).containsExactlyInAnyOrderElementsOf(expectedTargetNodeIds);
  }

  protected Gateway getGatewayWithId(final Collection<Gateway> gateways, final String gatewayId) {
    return gateways.stream()
      .filter(gateway -> gateway.getId().equals(gatewayId))
      .findFirst()
      .get();
  }

  protected void processEventCountAndTraces() {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    embeddedOptimizeExtension.processEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected CloudEventRequestDto createCloudEventOfType(final EventTypeDto eventType,
                                                        final String traceId,
                                                        final Instant now) {
    return ingestionClient.createCloudEventDto().toBuilder()
      .group(eventType.getGroup())
      .source(eventType.getSource())
      .type(eventType.getEventName())
      .traceid(traceId)
      .time(now).build();
  }

  protected void ingestEventAndProcessTraces(List<CloudEventRequestDto> eventsToIngest) {
    ingestionClient.ingestEventBatch(eventsToIngest);
    processEventCountAndTraces();
  }

  protected List<EventTypeDto> getMappedEventTypeDtosFromMappings(final Map<String, EventMappingDto> mappings) {
    return mappings.values()
      .stream()
      .flatMap(mapping -> Stream.of(mapping.getStart(), mapping.getEnd()))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  protected List<EventTypeDto> getEventCountsAsEventTypeDtos(final List<EventSourceEntryDto<?>> camundaEventSourceEntryDtos) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildPostEventCountRequest(EventCountRequestDto.builder().eventSources(camundaEventSourceEntryDtos).build()
      ).executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode())
      .stream()
      .map(EventDtoBuilderUtil::fromEventCountDto)
      .collect(Collectors.toList());
  }

  protected void processEventTracesAndSequences() {
    embeddedOptimizeExtension.processEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected String idOf(EventTypeDto eventTypeDto) {
    return generateNodeId(eventTypeDto);
  }

  protected static BpmnModelInstance singleStartSingleEndModel() {
    return singleStartSingleEndModel(PROCESS_ID_1, START_EVENT_ID_1, END_EVENT_ID_1);
  }

  protected static BpmnModelInstance singleStartSingleEndModel(final String processId,
                                                               final String startEventId,
                                                               final String endEventId) {
    return Bpmn.createExecutableProcess(processId)
      .startEvent(startEventId)
      .endEvent(endEventId)
      .done();
  }

  protected static BpmnModelInstance singleStartSingleEndUserTaskModel(final String processId,
                                                                       final String startEventId,
                                                                       final String endEventId) {
    return Bpmn.createExecutableProcess(processId)
      .startEvent(startEventId)
      .userTask(BPMN_INTERMEDIATE_EVENT_ID)
      .endEvent(endEventId)
      .done();
  }

  protected static BpmnModelInstance embeddedSubprocessModel() {
    return Bpmn.createExecutableProcess(PROCESS_ID_1)
      .startEvent(START_EVENT_ID_1)
      .subProcess()
      .embeddedSubProcess()
      .startEvent()
      .endEvent()
      .subProcessDone()
      .endEvent(END_EVENT_ID_1)
      .done();
  }

  protected static BpmnModelInstance multipleEmbeddedSubprocessModel() {
    return Bpmn.createExecutableProcess(PROCESS_ID_1)
      .startEvent(START_EVENT_ID_1)
      .subProcess()
      .embeddedSubProcess()
      .startEvent()
      .endEvent()
      .subProcessDone()
      .subProcess()
      .embeddedSubProcess()
      .startEvent()
      .endEvent()
      .subProcessDone()
      .endEvent(END_EVENT_ID_1)
      .done();
  }

  protected static BpmnModelInstance multipleStartSingleEndModel() {
    return multipleStartSingleEndModel(PROCESS_ID_1);
  }

  protected static BpmnModelInstance multipleStartSingleEndModel(final String processId) {
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(processId);
    final String gateway = "someGatewayId";
    processBuilder
      .startEvent(START_EVENT_ID_1)
      .exclusiveGateway(gateway)
      .endEvent(END_EVENT_ID_1);
    processBuilder.startEvent(START_EVENT_ID_2).message(START_EVENT_ID_2)
      .connectTo(gateway);
    return processBuilder.done();
  }

  protected static BpmnModelInstance singleStartMultipleEndModel() {
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(PROCESS_ID_1);
    final String gateway = "someGatewayId";
    processBuilder
      .startEvent(START_EVENT_ID_1)
      .exclusiveGateway(gateway)
      .condition("yes", "${default}")
      .endEvent(END_EVENT_ID_1)
      .moveToNode(gateway)
      .condition("no", "${!default}")
      .endEvent(END_EVENT_ID_2)
      .done();
    return processBuilder.done();
  }

  protected static BpmnModelInstance singleStartNoEndModel() {
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(PROCESS_ID_1);
    processBuilder
      .startEvent(START_EVENT_ID_1)
      .done();
    return processBuilder.done();
  }

  protected static BpmnModelInstance multipleStartMultipleEndModel() {
    return multipleStartMultipleEndModel(PROCESS_ID_1);
  }

  protected static BpmnModelInstance multipleStartMultipleEndModel(final String processId) {
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(processId);
    final String convergingGateway = "convergingGatewayId";
    final String divergingGateway = "divergingGatewayId";
    processBuilder
      .startEvent(START_EVENT_ID_1)
      .exclusiveGateway(convergingGateway)
      .exclusiveGateway(divergingGateway)
      .condition("yes", "${default}")
      .endEvent(END_EVENT_ID_1);
    processBuilder.startEvent(START_EVENT_ID_2).message(IdGenerator.getNextId())
      .connectTo(convergingGateway)
      .moveToNode(divergingGateway)
      .condition("no", "${!default}")
      .endEvent(END_EVENT_ID_2)
      .done();
    return processBuilder.done();
  }

  protected static BpmnModelInstance multipleStartNoEndModel() {
    return multipleStartNoEndModel(PROCESS_ID_1);
  }

  protected static BpmnModelInstance multipleStartNoEndModel(final String processId) {
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(processId);
    processBuilder
      .startEvent(START_EVENT_ID_1);
    processBuilder.startEvent(START_EVENT_ID_2).message(IdGenerator.getNextId())
      .done();
    return processBuilder.done();
  }

}
