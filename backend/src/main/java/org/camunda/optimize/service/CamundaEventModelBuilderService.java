/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.optimize.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.GatewayDirection;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.ProcessBuilder;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.BpmnModelUtility;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.camunda.bpm.model.bpmn.GatewayDirection.Converging;
import static org.camunda.bpm.model.bpmn.GatewayDirection.Diverging;
import static org.camunda.optimize.service.util.BpmnModelUtility.getEndEventsFromInstance;
import static org.camunda.optimize.service.util.BpmnModelUtility.getStartEventsFromInstance;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaProcessEndEventTypeDto;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaProcessStartEventTypeDto;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.DIVERGING_GATEWAY;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateGatewayIdForSource;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateNodeId;

@RequiredArgsConstructor
@Component
@Slf4j
public class CamundaEventModelBuilderService {

  private final DefinitionService definitionService;

  public AbstractFlowNodeBuilder<?, ?> createModelFromEventSource(final ProcessBuilder processBuilder,
                                                                  final Map<String, EventMappingDto> mappings,
                                                                  final EventSourceEntryDto sourceEntryDto) {
    if (EventScopeType.PROCESS_INSTANCE.equals(sourceEntryDto.getEventScope().get(0))) {
      return createProcessStartEndModel(processBuilder, mappings, sourceEntryDto);
    } else {
      return createStartEndEventModel(processBuilder, mappings, sourceEntryDto);
    }
  }

  private AbstractFlowNodeBuilder<?, ?> createStartEndEventModel(final ProcessBuilder processBuilder,
                                                                 final Map<String, EventMappingDto> mappings,
                                                                 final EventSourceEntryDto sourceEntryDto) {
    final BpmnModelInstance modelInstance = getModelInstanceForSourceEntryDefinition(sourceEntryDto);
    final String processDefinitionKey = sourceEntryDto.getProcessDefinitionKey();
    final List<EventTypeDto> startEvents = getStartEventsFromInstance(modelInstance, processDefinitionKey);
    final List<EventTypeDto> endEvents = getEndEventsFromInstance(modelInstance, processDefinitionKey);

    AbstractFlowNodeBuilder<?, ?> nextBuilder = null;
    for (EventTypeDto startEvent : startEvents) {
      nextBuilder = processBuilder.startEvent(generateNodeId(startEvent))
        .message(startEvent.getEventName())
        .name(startEvent.getEventName());
      if (startEvents.size() > 1) {
        nextBuilder = addOrConnectToGateway(nextBuilder, sourceEntryDto, Converging);
      }
      mappings.put(generateNodeId(startEvent), EventMappingDto.builder().start(startEvent).build());
    }
    if (nextBuilder == null) {
      // If there are no start events in the source model, we create an unmapped one
      nextBuilder = processBuilder.startEvent();
    }
    for (EventTypeDto endEvent : endEvents) {
      if (endEvents.size() > 1) {
        nextBuilder = addOrConnectToGateway(nextBuilder, sourceEntryDto, Diverging);
      }
      final String nodeId = generateNodeId(endEvent);
      nextBuilder = nextBuilder.endEvent(nodeId)
        .message(endEvent.getEventName())
        .name(endEvent.getEventName());
      mappings.put(nodeId, EventMappingDto.builder().start(endEvent).build());
    }
    return nextBuilder;
  }

  private AbstractFlowNodeBuilder<?, ?> createProcessStartEndModel(final ProcessBuilder processBuilder,
                                                                   final Map<String, EventMappingDto> mappings,
                                                                   final EventSourceEntryDto sourceEntryDto) {
    final EventTypeDto processInstanceStartProcessEvent =
      createCamundaProcessStartEventTypeDto(sourceEntryDto.getProcessDefinitionKey());
    final EventTypeDto processInstanceEndProcessEvent =
      createCamundaProcessEndEventTypeDto(sourceEntryDto.getProcessDefinitionKey());
    final String processStartEventId = generateNodeId(processInstanceStartProcessEvent);
    final String processEndEventId = generateNodeId(processInstanceEndProcessEvent);
    mappings.put(processStartEventId, EventMappingDto.builder().start(processInstanceStartProcessEvent).build());
    mappings.put(processEndEventId, EventMappingDto.builder().start(processInstanceEndProcessEvent).build());
    // @formatter:off
    return processBuilder
      .startEvent(processStartEventId)
        .message(processInstanceStartProcessEvent.getEventName())
        .name(processInstanceStartProcessEvent.getEventName())
      .endEvent(processEndEventId)
        .message(processInstanceEndProcessEvent.getEventName())
        .name(processInstanceEndProcessEvent.getEventName());
    // @formatter:on
  }

  private AbstractFlowNodeBuilder<?, ?> addOrConnectToGateway(final AbstractFlowNodeBuilder<?, ?> nextBuilder,
                                                              final EventSourceEntryDto source,
                                                              final GatewayDirection direction) {
    final BpmnModelInstance bpmnModelInstance = nextBuilder.done();
    final String gatewayId = generateGatewayIdForSource(source, direction);
    if (bpmnModelInstance.getModelElementById(gatewayId) == null) {
      log.debug("Adding {} gateway with id {} for multiple start events from source with key {}",
                direction.toString().toLowerCase(), gatewayId, source.getProcessDefinitionKey()
      );
      return Diverging.equals(direction) ?
        nextBuilder.exclusiveGateway(gatewayId).name(DIVERGING_GATEWAY) :
        nextBuilder.exclusiveGateway(gatewayId);
    } else {
      log.debug("Connecting to {} gateway with id {}", direction.toString().toLowerCase(), gatewayId);
      return Diverging.equals(direction) ? nextBuilder.moveToNode(gatewayId) : nextBuilder.connectTo(gatewayId);
    }
  }

  private BpmnModelInstance getModelInstanceForSourceEntryDefinition(final EventSourceEntryDto sourceEntryDto) {
    final String definitionXml = definitionService.getDefinitionWithXmlAsService(
      DefinitionType.PROCESS,
      sourceEntryDto.getProcessDefinitionKey(),
      sourceEntryDto.getVersions(),
      sourceEntryDto.getTenants()
    )
      .map(def -> ((ProcessDefinitionOptimizeDto) def).getBpmn20Xml())
      .orElseThrow(() -> new OptimizeRuntimeException(String.format(
        "Process definition with definition key %s could not be loaded", sourceEntryDto.getProcessDefinitionKey())));
    return BpmnModelUtility.parseBpmnModel(definitionXml);
  }

}
