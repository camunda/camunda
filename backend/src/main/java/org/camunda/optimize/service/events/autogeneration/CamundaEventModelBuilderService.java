/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.events.autogeneration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.GatewayDirection;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.ProcessBuilder;
import org.camunda.bpm.model.bpmn.builder.StartEventBuilder;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.BpmnModelUtil;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.bpm.model.bpmn.GatewayDirection.Converging;
import static org.camunda.bpm.model.bpmn.GatewayDirection.Diverging;
import static org.camunda.optimize.service.util.BpmnModelUtil.getEndEventsFromInstance;
import static org.camunda.optimize.service.util.BpmnModelUtil.getStartEventsFromInstance;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaProcessInstanceEndEventSuffix;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaProcessInstanceStartEventSuffix;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaProcessEndEventTypeDto;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaProcessStartEventTypeDto;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.addEndEvent;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.addExclusiveGateway;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.addIntermediateEvent;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.addStartEvent;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateModelGatewayIdForSource;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateNodeId;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateTaskIdForDefinitionKey;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.prepareModelBuilderForCurrentSource;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.prepareModelBuilderForNextSource;

@RequiredArgsConstructor
@Component
@Slf4j
public class CamundaEventModelBuilderService {

  private final DefinitionService definitionService;

  public AbstractFlowNodeBuilder<?, ?> createOrExtendModelWithEventSource(final ProcessBuilder processBuilder,
                                                                          final AbstractFlowNodeBuilder<?, ?> generatedModelBuilder,
                                                                          final Map<String, EventMappingDto> mappings,
                                                                          final CamundaEventSourceEntryDto sourceEntryDto,
                                                                          final boolean isFinalSourceInSeries) {
    if (EventScopeType.PROCESS_INSTANCE.equals(sourceEntryDto.getConfiguration().getEventScope().get(0))) {
      return createProcessStartEndModel(
        processBuilder,
        generatedModelBuilder,
        mappings,
        sourceEntryDto,
        isFinalSourceInSeries
      );
    } else {
      return createStartEndEventModel(
        processBuilder,
        generatedModelBuilder,
        mappings,
        sourceEntryDto,
        isFinalSourceInSeries
      );
    }
  }

  private AbstractFlowNodeBuilder<?, ?> createStartEndEventModel(final ProcessBuilder processBuilder,
                                                                 AbstractFlowNodeBuilder<?, ?> generatedModelBuilder,
                                                                 final Map<String, EventMappingDto> mappings,
                                                                 final CamundaEventSourceEntryDto sourceEntryDto,
                                                                 final boolean isFinalSourceInSeries) {
    final BpmnModelInstance modelInstance = getModelInstanceForSourceEntryDefinition(sourceEntryDto);
    final String processDefinitionKey = sourceEntryDto.getConfiguration().getProcessDefinitionKey();
    final List<EventTypeDto> startEvents = getStartEventsFromInstance(modelInstance, processDefinitionKey);
    final List<EventTypeDto> endEvents = getEndEventsFromInstance(modelInstance, processDefinitionKey);

    // The preparation involves adding a new diverging gateway in the event that this isn't the first source and the
    // current source has multiple start events
    AbstractFlowNodeBuilder<?, ?> nextBuilder = null;
    if (generatedModelBuilder != null) {
      generatedModelBuilder = prepareModelBuilderForCurrentSource(
        generatedModelBuilder,
        startEvents,
        processDefinitionKey
      );
    }

    for (EventTypeDto startEvent : startEvents) {
      if (generatedModelBuilder == null) {
        nextBuilder = addStartEvent(
          startEvent,
          generateNodeId(startEvent),
          processBuilder,
          startEvents.indexOf(startEvent)
        );
      } else {
        nextBuilder = addIntermediateEvent(startEvent, generateNodeId(startEvent), generatedModelBuilder);
      }
      if (startEvents.size() > 1) {
        nextBuilder = addOrConnectToGateway(nextBuilder, sourceEntryDto, Converging);
      }
      mappings.put(generateNodeId(startEvent), EventMappingDto.builder().start(startEvent).build());
    }
    if (nextBuilder == null) {
      // If there are no start events in the source model, we create an unmapped one - should never be the case
      nextBuilder = processBuilder.startEvent();
    }
    for (EventTypeDto endEvent : endEvents) {
      if (endEvents.size() > 1) {
        nextBuilder = addOrConnectToGateway(nextBuilder, sourceEntryDto, Diverging);
      }
      final String nodeId = generateNodeId(endEvent);
      if (isFinalSourceInSeries) {
        nextBuilder = addEndEvent(endEvent, generateNodeId(endEvent), nextBuilder);
      } else {
        nextBuilder = addIntermediateEvent(endEvent, generateNodeId(endEvent), nextBuilder);
      }
      mappings.put(nodeId, EventMappingDto.builder().start(endEvent).build());
    }

    // if this is false, we expect there to be sources to add to the model after this one, so we need to return the
    // builder in a state where it can be extended
    if (!isFinalSourceInSeries) {
      nextBuilder = prepareModelBuilderForNextSource(nextBuilder, endEvents, processDefinitionKey);
    }
    return nextBuilder;
  }

  private AbstractFlowNodeBuilder<?, ?> createProcessStartEndModel(final ProcessBuilder processBuilder,
                                                                   final AbstractFlowNodeBuilder<?, ?> generatedModelBuilder,
                                                                   final Map<String, EventMappingDto> mappings,
                                                                   final CamundaEventSourceEntryDto sourceEntryDto,
                                                                   final boolean isFinalSourceInSeries) {
    final String processDefinitionKey = sourceEntryDto.getConfiguration().getProcessDefinitionKey();
    final String definitionName = getDefinition(sourceEntryDto).map(DefinitionOptimizeResponseDto::getName)
      .orElse(processDefinitionKey);
    final EventTypeDto processInstanceStartProcessEvent = createCamundaProcessStartEventTypeDto(processDefinitionKey);
    final EventTypeDto processInstanceEndProcessEvent = createCamundaProcessEndEventTypeDto(processDefinitionKey);
    final String processStartEventId = generateNodeId(processInstanceStartProcessEvent);
    final String processEndEventId = generateNodeId(processInstanceEndProcessEvent);
    final String processStartNodeName = applyCamundaProcessInstanceStartEventSuffix(definitionName);
    final String processEndNodeName = applyCamundaProcessInstanceEndEventSuffix(definitionName);

    AbstractFlowNodeBuilder<?, ?> builderToReturn;
    // If this is true, this source isn't continuing the build from a previous source so should start the process
    if (generatedModelBuilder == null) {
      final AbstractFlowNodeBuilder<StartEventBuilder, StartEvent> currentBuilder = addStartEvent(
        processStartNodeName, processStartEventId, processBuilder
      );
      if (isFinalSourceInSeries) {
        builderToReturn = addEndEvent(processEndNodeName, processEndEventId, currentBuilder);
      } else {
        builderToReturn = addIntermediateEvent(processEndNodeName, processEndEventId, currentBuilder);
      }
      mappings.put(processStartEventId, EventMappingDto.builder().start(processInstanceStartProcessEvent).build());
      mappings.put(processEndEventId, EventMappingDto.builder().start(processInstanceEndProcessEvent).build());
    } else {
      // If this source isn't the start or end source in the overall model, it gets added as a call activity
      if (!isFinalSourceInSeries) {
        final String nodeId = generateTaskIdForDefinitionKey(processDefinitionKey);
        builderToReturn = generatedModelBuilder.callActivity(nodeId).name(definitionName);
        mappings.put(nodeId, EventMappingDto.builder()
          .start(processInstanceStartProcessEvent)
          .end(processInstanceEndProcessEvent)
          .build());
      } else {
        builderToReturn = addIntermediateEvent(processStartNodeName, processStartEventId, generatedModelBuilder);
        builderToReturn = addEndEvent(processEndNodeName, processEndEventId, builderToReturn);
        mappings.put(processStartEventId, EventMappingDto.builder().start(processInstanceStartProcessEvent).build());
        mappings.put(processEndEventId, EventMappingDto.builder().start(processInstanceEndProcessEvent).build());
      }
    }
    // we don't need to add connections here because it will always be in the correct position at the end element
    // of the process
    return builderToReturn;
  }

  private AbstractFlowNodeBuilder<?, ?> addOrConnectToGateway(final AbstractFlowNodeBuilder<?, ?> nextBuilder,
                                                              final CamundaEventSourceEntryDto source,
                                                              final GatewayDirection direction) {
    final BpmnModelInstance bpmnModelInstance = nextBuilder.done();
    final String gatewayId = generateModelGatewayIdForSource(source, direction);
    if (bpmnModelInstance.getModelElementById(gatewayId) == null) {
      return addExclusiveGateway(direction, gatewayId, nextBuilder);
    } else {
      log.debug("Connecting or moving to {} gateway with id {}", direction.toString().toLowerCase(), gatewayId);
      return Diverging.equals(direction) ? nextBuilder.moveToNode(gatewayId) : nextBuilder.connectTo(gatewayId);
    }
  }

  private BpmnModelInstance getModelInstanceForSourceEntryDefinition(final CamundaEventSourceEntryDto sourceEntryDto) {
    final String definitionXml = getDefinition(sourceEntryDto)
      .map(ProcessDefinitionOptimizeDto.class::cast)
      .map(ProcessDefinitionOptimizeDto::getBpmn20Xml)
      .orElseThrow(() -> new OptimizeRuntimeException(String.format(
        "Process definition with definition key %s could not be loaded",
        sourceEntryDto.getConfiguration().getProcessDefinitionKey()
      )));
    return BpmnModelUtil.parseBpmnModel(definitionXml);
  }

  private Optional<DefinitionOptimizeResponseDto> getDefinition(final CamundaEventSourceEntryDto sourceEntryDto) {
    return definitionService.getDefinitionWithXmlAsService(
      DefinitionType.PROCESS,
      sourceEntryDto.getConfiguration().getProcessDefinitionKey(),
      sourceEntryDto.getConfiguration().getVersions(),
      sourceEntryDto.getConfiguration().getTenants()
    );
  }

}
