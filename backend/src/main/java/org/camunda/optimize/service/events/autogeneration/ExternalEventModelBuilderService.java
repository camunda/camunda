/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.events.autogeneration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.GatewayDirection;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.ProcessBuilder;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.optimize.dto.optimize.query.event.autogeneration.AutogenerationAdjacentEventTypesDto;
import org.camunda.optimize.dto.optimize.query.event.autogeneration.AutogenerationEventGraphDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static org.camunda.bpm.model.bpmn.GatewayDirection.Converging;
import static org.camunda.bpm.model.bpmn.GatewayDirection.Diverging;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.addEndEvent;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.addExclusiveGateway;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.addInclusiveGateway;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.addIntermediateEvent;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.addParallelGateway;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.addStartEvent;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateGatewayIdForNode;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateNodeId;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.prepareModelBuilderForCurrentSource;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.prepareModelBuilderForNextSource;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

@RequiredArgsConstructor
@Component
@Slf4j
public class ExternalEventModelBuilderService {

  public AbstractFlowNodeBuilder<?, ?> createOrExtendModelWithExternalEventSource(final AutogenerationEventGraphDto autogenerationEventGraphDto,
                                                                                  final List<List<EventTypeDto>> sampleTraceLists,
                                                                                  final ProcessBuilder processBuilder,
                                                                                  AbstractFlowNodeBuilder<?, ?> currentBuilder,
                                                                                  final Map<String, EventMappingDto> mappings,
                                                                                  final boolean isFinalSourceInSeries) {
    if (!canGenerateModelFromGraph(autogenerationEventGraphDto)) {
      return currentBuilder;
    }

    // The preparation involves adding a new diverging gateway in the event that this isn't the first source and the
    // current source has multiple start events
    AbstractFlowNodeBuilder<?, ?> builderToReturn = null;
    if (currentBuilder != null) {
      currentBuilder = prepareModelBuilderForCurrentSource(
        currentBuilder,
        autogenerationEventGraphDto.getStartEvents(),
        EXTERNAL_EVENTS_INDEX_SUFFIX
      );
    }

    final List<EventTypeDto> startEvents = autogenerationEventGraphDto.getStartEvents();
    for (EventTypeDto rootNode : startEvents) {
      final String nodeId = generateNodeId(rootNode);
      final AbstractFlowNodeBuilder<?, ?> nextBuilder;
      if (currentBuilder == null) {
        nextBuilder = addStartEvent(rootNode, nodeId, processBuilder, startEvents.indexOf(rootNode));
      } else {
        nextBuilder = addIntermediateEvent(rootNode, nodeId, currentBuilder);
      }
      mappings.put(nodeId, EventMappingDto.builder().start(rootNode).build());
      builderToReturn = depthTraverseGraph(
        rootNode,
        autogenerationEventGraphDto.getAdjacentEventTypesDtoMap()
          .get(rootNode)
          .getSucceedingEvents(),
        autogenerationEventGraphDto,
        sampleTraceLists,
        nextBuilder,
        mappings,
        isFinalSourceInSeries
      );
    }

    // if this is false, we expect there to be sources to add to the model after this one, so we need to return the
    // builder in a state where it can be extended
    if (builderToReturn != null && !isFinalSourceInSeries) {
      builderToReturn = prepareModelBuilderForNextSource(
        builderToReturn,
        autogenerationEventGraphDto.getEndEvents(),
        EXTERNAL_EVENTS_INDEX_SUFFIX
      );
    }
    return builderToReturn;
  }

  private AbstractFlowNodeBuilder<?, ?> depthTraverseGraph(final EventTypeDto previouslyAddedNode,
                                                           final List<EventTypeDto> nodesToAdd,
                                                           final AutogenerationEventGraphDto graphDto,
                                                           final List<List<EventTypeDto>> sampleTraceLists,
                                                           AbstractFlowNodeBuilder<?, ?> currentNodeBuilder,
                                                           final Map<String, EventMappingDto> mappings,
                                                           final boolean isFinalSourceInSeries) {
    for (EventTypeDto nodeToAdd : nodesToAdd) {
      final Optional<String> precedingDivergingGatewayId = getPrecedingDivergingGatewayId(nodeToAdd, graphDto);
      if (precedingDivergingGatewayId.isPresent() &&
        !nodeAlreadyAddedToModel(precedingDivergingGatewayId.get(), currentNodeBuilder)) {
        currentNodeBuilder = addGateway(currentNodeBuilder, previouslyAddedNode, Diverging, graphDto, sampleTraceLists);
      }
      if (graphDto.getEndEvents().contains(nodeToAdd)) {
        currentNodeBuilder = addOrConnectToEndNode(
          graphDto,
          sampleTraceLists,
          currentNodeBuilder,
          mappings,
          nodeToAdd,
          isFinalSourceInSeries
        );
      } else {
        if (!nodesExistInParallel(nodeToAdd, previouslyAddedNode, graphDto, sampleTraceLists)) {
          currentNodeBuilder = addOrConnectToIntermediateEventNode(
            graphDto,
            sampleTraceLists,
            currentNodeBuilder,
            mappings,
            nodeToAdd,
            isFinalSourceInSeries
          );
        } else {
          depthTraverseOrConnect(
            graphDto,
            sampleTraceLists,
            currentNodeBuilder,
            mappings,
            nodeToAdd,
            isFinalSourceInSeries
          );
        }
      }
    }
    return currentNodeBuilder;
  }

  private void depthTraverseOrConnect(final AutogenerationEventGraphDto graphDto,
                                      final List<List<EventTypeDto>> sampleTraceLists,
                                      final AbstractFlowNodeBuilder<?, ?> currentNodeBuilder,
                                      final Map<String, EventMappingDto> mappings,
                                      final EventTypeDto nodeToAdd,
                                      final boolean isFinalSourceInSeries) {
    final List<EventTypeDto> succeedingEvents = graphDto.getAdjacentEventTypesDtoMap()
      .get(nodeToAdd).getSucceedingEvents();
    final List<EventTypeDto> nonParallel = succeedingEvents
      .stream()
      .filter(nextNode -> !nodesExistInParallel(nextNode, nodeToAdd, graphDto, sampleTraceLists))
      .collect(Collectors.toList());
    if (!nonParallel.isEmpty()) {
      depthTraverseGraph(
        nodeToAdd,
        nonParallel,
        graphDto,
        sampleTraceLists,
        currentNodeBuilder,
        mappings,
        isFinalSourceInSeries
      );
    } else {
      // if we can't find a non-parallel nodes, we try to connect to any already modelled next nodes
      final Optional<EventTypeDto> nodeToConnect = findAlreadyModelledNextNodeId(currentNodeBuilder, succeedingEvents);
      if (nodeToConnect.isPresent()) {
        final EventTypeDto nextNode = nodeToConnect.get();
        log.debug(
          "Could not find a non-parallel next node after node with id {}, connecting to {}",
          generateNodeId(nodeToAdd),
          generateNodeId(nextNode)
        );
        connectToExistingNode(currentNodeBuilder, nextNode);
      } else {
        // We've run out of options now, so we just leave an orphaned node
        log.debug("Cannot connect node {} to its next node as it could not be found", generateNodeId(nodeToAdd));
      }
    }
  }

  private AbstractFlowNodeBuilder<?, ?> addOrConnectToEndNode(final AutogenerationEventGraphDto graphDto,
                                                              final List<List<EventTypeDto>> sampleTraceLists,
                                                              AbstractFlowNodeBuilder<?, ?> currentNodeBuilder,
                                                              final Map<String, EventMappingDto> mappings,
                                                              final EventTypeDto nodeToAdd,
                                                              final boolean isFinalSourceInSeries) {
    final String nodeId = generateNodeId(nodeToAdd);
    if (!nodeAlreadyAddedToModel(nodeId, currentNodeBuilder)) {
      if (nodeSucceedsConvergingGateway(nodeToAdd, graphDto)) {
        currentNodeBuilder = addGateway(currentNodeBuilder, nodeToAdd, Converging, graphDto, sampleTraceLists);
      }
      if (isFinalSourceInSeries) {
        addEndEvent(nodeToAdd, nodeId, currentNodeBuilder);
      } else {
        addIntermediateEvent(nodeToAdd, nodeId, currentNodeBuilder);
      }
      mappings.put(nodeId, EventMappingDto.builder().start(nodeToAdd).build());
    } else {
      final String existingGatewayId = generateGatewayIdForNode(nodeToAdd, Converging);
      // In some cases, there may not be a gateway
      if (nodeSucceedsConvergingGateway(nodeToAdd, graphDto)
        && nodeAlreadyAddedToModel(existingGatewayId, currentNodeBuilder)) {
        log.debug("Connecting to gateway with id {}", existingGatewayId);
        currentNodeBuilder.connectTo(existingGatewayId);
      } else {
        // If the gateway hasn't been added, we connect directly to the node
        log.debug("Connecting to end event with id {}", nodeId);
        currentNodeBuilder.connectTo(nodeId);
      }
    }
    return currentNodeBuilder;
  }

  private AbstractFlowNodeBuilder<?, ?> addOrConnectToIntermediateEventNode(final AutogenerationEventGraphDto graphDto,
                                                                            final List<List<EventTypeDto>> sampleTraceLists,
                                                                            AbstractFlowNodeBuilder<?, ?> currentNodeBuilder,
                                                                            final Map<String, EventMappingDto> mappings,
                                                                            final EventTypeDto nodeToAdd,
                                                                            final boolean isFinalSourceInSeries) {
    final String nodeId = generateNodeId(nodeToAdd);
    if (!nodeAlreadyAddedToModel(nodeId, currentNodeBuilder)) {
      if (nodeSucceedsConvergingGateway(nodeToAdd, graphDto)) {
        currentNodeBuilder = addGateway(currentNodeBuilder, nodeToAdd, Converging, graphDto, sampleTraceLists);
      }
      AbstractFlowNodeBuilder<?, ?> nextBuilder = addIntermediateEvent(nodeToAdd, nodeId, currentNodeBuilder);
      mappings.put(nodeId, EventMappingDto.builder().start(nodeToAdd).build());
      depthTraverseOrConnect(graphDto, sampleTraceLists, nextBuilder, mappings, nodeToAdd, isFinalSourceInSeries);
    } else {
      connectToExistingNode(currentNodeBuilder, nodeToAdd);
    }
    return currentNodeBuilder;
  }

  private void connectToExistingNode(final AbstractFlowNodeBuilder<?, ?> currentNodeBuilder,
                                     final EventTypeDto nodeToConnectTo) {
    final String existingGatewayId = generateGatewayIdForNode(nodeToConnectTo, Converging);
    if (nodeAlreadyAddedToModel(existingGatewayId, currentNodeBuilder)) {
      log.debug("Connecting to gateway with id {}", existingGatewayId);
      currentNodeBuilder.connectTo(existingGatewayId);
    } else {
      // If the gateway hasn't been added, we connect directly to the node
      log.debug("Connecting to node with id {}", existingGatewayId);
      currentNodeBuilder.connectTo(generateNodeId(nodeToConnectTo));
    }
  }

  private Optional<EventTypeDto> findAlreadyModelledNextNodeId(final AbstractFlowNodeBuilder<?, ?> currentNodeBuilder,
                                                               final List<EventTypeDto> nextNodes) {
    for (EventTypeDto nextNode : nextNodes) {
      final String nextNodeId = generateNodeId(nextNode);
      if (nodeAlreadyAddedToModel(nextNodeId, currentNodeBuilder)) {
        return Optional.of(nextNode);
      }
    }
    return Optional.empty();
  }

  private AbstractFlowNodeBuilder<?, ?> addGateway(AbstractFlowNodeBuilder<?, ?> currentNodeBuilder,
                                                   final EventTypeDto nodeToAdd,
                                                   final GatewayDirection gatewayDirection,
                                                   final AutogenerationEventGraphDto graphDto,
                                                   final List<List<EventTypeDto>> sampleTraceLists) {
    final List<EventTypeDto> adjacentEventsToLookup;
    final AutogenerationAdjacentEventTypesDto adjacentEventTypesDto = graphDto.getAdjacentEventTypesDtoMap()
      .get(nodeToAdd);
    if (Diverging.equals(gatewayDirection)) {
      adjacentEventsToLookup = adjacentEventTypesDto.getSucceedingEvents();
    } else {
      adjacentEventsToLookup = adjacentEventTypesDto.getPrecedingEvents();
    }
    final Map<Integer, Long> traceCountsByAdjacentEventOccurrence =
      extractEventTraceCountByAdjacentEventOccurrence(
        nodeToAdd,
        gatewayDirection,
        adjacentEventsToLookup,
        sampleTraceLists
      );

    final boolean isParallelGateway = traceCountsByAdjacentEventOccurrence.get(adjacentEventsToLookup.size()) != null
      && traceCountsByAdjacentEventOccurrence.size() == 1;
    final boolean isExclusiveGateway = traceCountsByAdjacentEventOccurrence.get(1) != null
      && traceCountsByAdjacentEventOccurrence.size() == 1;
    final String gatewayId = generateGatewayIdForNode(nodeToAdd, gatewayDirection);
    if (isParallelGateway) {
      return addParallelGateway(gatewayDirection, gatewayId, currentNodeBuilder);
    } else if (isExclusiveGateway) {
      return addExclusiveGateway(gatewayDirection, gatewayId, currentNodeBuilder);
    } else {
      return addInclusiveGateway(gatewayDirection, gatewayId, currentNodeBuilder);
    }
  }

  private Map<Integer, Long> extractEventTraceCountByAdjacentEventOccurrence(final EventTypeDto nodeToAdd,
                                                                             final GatewayDirection gatewayDirection,
                                                                             final List<EventTypeDto> adjacentEventsToLookup,
                                                                             final List<List<EventTypeDto>> sampleTraceLists) {
    return sampleTraceLists
      .stream()
      .filter(eventTypeTrace -> eventTypeTrace.contains(nodeToAdd))
      .map(eventTrace -> {
        if (Diverging.equals(gatewayDirection)) {
          // if diverging, we only care about the occurrences of adjacent events after the last occurring target event
          return eventTrace.subList(eventTrace.lastIndexOf(nodeToAdd), eventTrace.size());
        } else {
          // if converging, we only care about the occurrences of adjacent events before the first occurring target
          // event
          return eventTrace.subList(0, eventTrace.indexOf(nodeToAdd));
        }
      })
      .collect(groupingBy(
        trace -> CollectionUtils.intersection(trace, adjacentEventsToLookup).size(),
        Collectors.counting()
      ));
  }

  private boolean nodeAlreadyAddedToModel(final String nodeId, AbstractFlowNodeBuilder<?, ?> currentNodeBuilder) {
    BpmnModelInstance currentFlowNodeInstance = currentNodeBuilder.done();
    ModelElementInstance existingModelElement = currentFlowNodeInstance.getModelElementById(nodeId);
    return existingModelElement != null;
  }

  private boolean nodeSucceedsConvergingGateway(final EventTypeDto node, final AutogenerationEventGraphDto graphDto) {
    return getPrecedingNodes(node, graphDto).size() > 1;
  }

  private Optional<String> getPrecedingDivergingGatewayId(final EventTypeDto node,
                                                          final AutogenerationEventGraphDto graphDto) {
    final List<Map.Entry<EventTypeDto, AutogenerationAdjacentEventTypesDto>> precedingNodes = getPrecedingNodes(
      node,
      graphDto
    );
    if (precedingNodes.size() == 1 && precedingNodes.get(0).getValue().getSucceedingEvents().size() > 1) {
      return Optional.of(generateGatewayIdForNode(precedingNodes.get(0).getKey(), Diverging));
    }
    return Optional.empty();
  }

  private List<Map.Entry<EventTypeDto, AutogenerationAdjacentEventTypesDto>> getPrecedingNodes(final EventTypeDto node,
                                                                                               final AutogenerationEventGraphDto graphDto) {
    return graphDto.getAdjacentEventTypesDtoMap()
      .entrySet()
      .stream()
      .filter(entry -> entry.getValue().getSucceedingEvents().contains(node))
      .filter(entry -> !graphDto.getAdjacentEventTypesDtoMap()
        .get(node)
        .getSucceedingEvents()
        .contains(entry.getKey()))
      .collect(Collectors.toList());
  }

  private boolean nodesExistInParallel(final EventTypeDto nodeToAdd,
                                       final EventTypeDto alreadyAddedNode,
                                       final AutogenerationEventGraphDto graphDto,
                                       final List<List<EventTypeDto>> sampleTraceLists) {
    // if a node is adjacent to a gateway, we can try to determine whether it is part of a parallel path to the
    // previously added node to avoid additional redundant gateway creation

    // if either node are start or end events, they are not parallel
    final boolean startOrEnd = Stream.concat(graphDto.getStartEvents().stream(), graphDto.getEndEvents().stream())
      .anyMatch(event -> event.equals(nodeToAdd) || event.equals(alreadyAddedNode));
    if (startOrEnd) {
      return false;
    }

    // if  both nodes precede and succeed each other in our trace samples, they are parallel
    final List<List<EventTypeDto>> containsBothEvents = sampleTraceLists.stream()
      .filter(eventTrace -> eventTrace.containsAll(Arrays.asList(nodeToAdd, alreadyAddedNode)))
      .collect(Collectors.toList());
    final boolean aBeforeB = containsBothEvents.stream()
      .anyMatch(trace -> trace.indexOf(nodeToAdd) == trace.indexOf(alreadyAddedNode) + 1);
    final boolean bBeforeA = containsBothEvents.stream()
      .anyMatch(trace -> trace.indexOf(alreadyAddedNode) == trace.indexOf(nodeToAdd) + 1);
    if (aBeforeB && bBeforeA) {
      return true;
    }

    // If both nodes share a preceding diverging gateway, we know they are parallel
    return getPrecedingDivergingGatewayId(nodeToAdd, graphDto)
      .map(gatewayId -> {
        final Optional<String> existingGatewayId = getPrecedingDivergingGatewayId(alreadyAddedNode, graphDto);
        return existingGatewayId.isPresent() && existingGatewayId.get().equals(gatewayId);
      }).orElse(false);
  }

  private boolean canGenerateModelFromGraph(final AutogenerationEventGraphDto autogenerationEventGraphDto) {
    if (autogenerationEventGraphDto.getStartEvents().isEmpty()) {
      log.warn("Cannot generate an external events model as no eligible start events found");
      return false;
    }
    return true;
  }

}
