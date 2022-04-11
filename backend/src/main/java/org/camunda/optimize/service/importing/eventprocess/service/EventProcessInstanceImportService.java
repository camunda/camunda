/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.eventprocess.service;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.Query;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.Event;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.optimize.dto.optimize.importing.EventProcessGatewayDto;
import org.camunda.optimize.dto.optimize.query.event.process.CancelableEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventCorrelationStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventToFlowNodeMapping;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceUpdateDto;
import org.camunda.optimize.dto.optimize.query.event.process.MappedEventType;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.EventProcessInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.EventProcessInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.util.BpmnModelUtil.parseBpmnModel;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@AllArgsConstructor
@Slf4j
public class EventProcessInstanceImportService implements ImportService<EventDto> {

  private static final int MAX_TRAVERSAL_DISTANCE = 10;

  private static final String EXCLUSIVE_GATEWAY = "exclusiveGateway";
  private static final String PARALLEL_GATEWAY = "parallelGateway";
  private static final String EVENT_BASED_GATEWAY = "eventBasedGateway";

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EventProcessInstanceWriter eventProcessInstanceWriter;

  private final EventProcessPublishStateDto eventProcessPublishStateDto;
  private final Map<String, EventToFlowNodeMapping> eventMappingIdToEventMapping;
  private final BpmnModelInstance bpmnModelInstance;

  public EventProcessInstanceImportService(final ConfigurationService configurationService,
                                           final EventProcessPublishStateDto eventProcessPublishStateDto,
                                           final EventProcessInstanceWriter eventProcessInstanceWriter) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.eventProcessPublishStateDto = eventProcessPublishStateDto;
    this.bpmnModelInstance = parseBpmnModel(eventProcessPublishStateDto.getXml());
    this.eventMappingIdToEventMapping = extractMappingByEventIdentifier(eventProcessPublishStateDto, bpmnModelInstance);
    this.eventProcessInstanceWriter = eventProcessInstanceWriter;
    this.eventProcessInstanceWriter.setGatewayLookup(buildGatewayLookup(
      eventProcessPublishStateDto, bpmnModelInstance
    ));
  }

  @Override
  public void executeImport(final List<EventDto> pageOfEvents, final Runnable importCompleteCallback) {
    log.trace("Importing entities for event process mapping [{}].", eventProcessPublishStateDto.getProcessMappingId());

    boolean newDataIsAvailable = !pageOfEvents.isEmpty();
    if (newDataIsAvailable) {
      final List<EventProcessInstanceDto> newOptimizeEntities = mapToProcessInstances(pageOfEvents);
      elasticsearchImportJobExecutor.executeImportJob(
        createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback)
      );
    } else {
      importCompleteCallback.run();
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private List<EventProcessInstanceDto> mapToProcessInstances(final List<EventDto> importedEvents) {
    final Map<String, List<EventDto>> eventsGroupedByTraceId = importedEvents.stream()
      .filter(eventDto -> eventMappingIdToEventMapping.containsKey(getMappingIdentifier(eventDto)))
      // For the same event id we want the last ingested event to win to allow updates.
      // Canceled flow nodes have the same timestamp, so we take the canceled node as they can never be uncanceled
      .sorted(Comparator.comparing(EventDto::getIngestionTimestamp)
                .thenComparing(event -> getCanceledState(event).orElse(false))
                .reversed())
      .distinct()
      .collect(groupingBy(EventDto::getTraceId));

    return eventsGroupedByTraceId
      .entrySet()
      .stream()
      .map(this::mapToProcessInstanceDto)
      .collect(Collectors.toList());
  }

  private EventProcessInstanceDto mapToProcessInstanceDto(final Map.Entry<String, List<EventDto>> eventTraceGroup) {
    final String processInstanceId = eventTraceGroup.getKey();

    final EventProcessInstanceDto processInstanceDto = EventProcessInstanceDto.eventProcessInstanceBuilder()
      .processInstanceId(processInstanceId)
      .processDefinitionKey(eventProcessPublishStateDto.getProcessMappingId())
      .processDefinitionId(eventProcessPublishStateDto.getProcessMappingId())
      .processDefinitionVersion("1")
      .build();

    addFlowNodeInstances(eventTraceGroup.getValue(), processInstanceDto);

    addVariables(eventTraceGroup, processInstanceDto);

    return processInstanceDto;
  }

  private void addVariables(final Map.Entry<String, List<EventDto>> eventTraceGroup,
                            final EventProcessInstanceDto processInstanceDto) {
    processInstanceDto.setVariables(
      eventTraceGroup.getValue()
        .stream()
        .map(this::extractSimpleVariables)
        .flatMap(Collection::stream)
        .distinct()
        .collect(Collectors.toList())
    );
  }

  private void addFlowNodeInstances(final List<EventDto> eventTraceGroup,
                                    final EventProcessInstanceDto processInstanceDto) {
    eventTraceGroup
      .forEach(eventDto -> {
        final String eventId = eventDto.getId();
        final EventToFlowNodeMapping eventToFlowNodeMapping =
          eventMappingIdToEventMapping.get(getMappingIdentifier(eventDto));
        final OffsetDateTime eventTimeStampAsOffsetDateTime = OffsetDateTime.ofInstant(
          Instant.ofEpochMilli(eventDto.getTimestamp()), ZoneId.systemDefault()
        );

        final EventCorrelationStateDto eventCorrelationStateDto = new EventCorrelationStateDto();
        processInstanceDto.getCorrelatedEventsById().put(eventId, eventCorrelationStateDto);

        final FlowNodeInstanceDto flowNodeInstance = new FlowNodeInstanceDto(
          processInstanceDto.getProcessDefinitionKey(),
          processInstanceDto.getProcessDefinitionVersion(),
          processInstanceDto.getTenantId(),
          processInstanceDto.getProcessInstanceId(),
          eventToFlowNodeMapping.getFlowNodeId(),
          eventToFlowNodeMapping.getFlowNodeType(),
          eventId
        ).setCanceled(getCanceledState(eventDto).orElse(null));

        final EventMappingDto eventMapping = eventProcessPublishStateDto.getMappings()
          .get(eventToFlowNodeMapping.getFlowNodeId());
        if (eventMapping.getStart() != null && eventMapping.getEnd() != null) {
          updateInstanceForStartAndEndMappedNodeEvent(
            processInstanceDto,
            eventId,
            eventToFlowNodeMapping,
            eventTimeStampAsOffsetDateTime,
            eventCorrelationStateDto,
            flowNodeInstance
          );
        } else if (isZeroDurationBpmnEvent(eventToFlowNodeMapping.getFlowNodeId())) {
          updateInstanceForSingleMappedBpmnNodeEvent(
            processInstanceDto,
            eventId,
            eventToFlowNodeMapping,
            eventTimeStampAsOffsetDateTime,
            eventCorrelationStateDto,
            flowNodeInstance
          );
        } else {
          updateInstanceForSingleMappedFlowNodeEvent(
            processInstanceDto,
            eventId,
            eventToFlowNodeMapping,
            eventTimeStampAsOffsetDateTime,
            eventCorrelationStateDto,
            flowNodeInstance
          );
        }
      });
  }

  private Optional<Boolean> getCanceledState(final EventDto eventDto) {
    if (eventDto instanceof CancelableEventDto) {
      final CancelableEventDto cancelableEvent = (CancelableEventDto) eventDto;
      return Optional.of(cancelableEvent.isCanceled());
    }
    return Optional.empty();
  }

  private void updateInstanceForSingleMappedFlowNodeEvent(final EventProcessInstanceDto processInstanceDto,
                                                          final String eventId,
                                                          final EventToFlowNodeMapping eventToFlowNodeMapping,
                                                          final OffsetDateTime eventTimeStampAsOffsetDateTime,
                                                          final EventCorrelationStateDto eventCorrelationStateDto,
                                                          final FlowNodeInstanceDto flowNodeInstance) {
    switch (eventToFlowNodeMapping.getMappedAs()) {
      case START:
        eventCorrelationStateDto.getCorrelatedAsToFlowNodeInstanceIds()
          .put(MappedEventType.START, Sets.newHashSet(flowNodeInstance.getFlowNodeInstanceId()));
        flowNodeInstance.setStartDate(eventTimeStampAsOffsetDateTime);
        // if there are no start mappings present on any next adjacent node we default to end being start time
        // as otherwise there is no guarantee the end is ever set on this flow node instance
        if (noStartMappingOnAtLeastOneOfTheNextMappedFlowNodes(eventToFlowNodeMapping)) {
          flowNodeInstance.setEndDate(eventTimeStampAsOffsetDateTime);
          eventCorrelationStateDto.getCorrelatedAsToFlowNodeInstanceIds()
            .put(MappedEventType.END, Sets.newHashSet(flowNodeInstance.getFlowNodeInstanceId()));
          // we also propagate start time updates to any following flow nodes without an own start mapping
          // as the start date is now the endDate of this flowNodeInstance as well
          updateStartDateTimeForNextFlowNodesWithoutOwnStartMapping(
            eventId, eventTimeStampAsOffsetDateTime, eventToFlowNodeMapping, processInstanceDto
          );
        }
        updateEndDateTimeForPreviousFlowNodesWithoutOwnEndMapping(
          eventId, eventTimeStampAsOffsetDateTime, eventToFlowNodeMapping, processInstanceDto
        );
        break;
      case END:
        eventCorrelationStateDto.getCorrelatedAsToFlowNodeInstanceIds()
          .put(MappedEventType.END, Sets.newHashSet(flowNodeInstance.getFlowNodeInstanceId()));
        flowNodeInstance.setEndDate(eventTimeStampAsOffsetDateTime);
        if (eventToFlowNodeMapping.getPreviousMappedFlowNodeIds().isEmpty()) {
          eventCorrelationStateDto.getCorrelatedAsToFlowNodeInstanceIds()
            .put(MappedEventType.START, Sets.newHashSet(flowNodeInstance.getFlowNodeInstanceId()));
          flowNodeInstance.setStartDate(eventTimeStampAsOffsetDateTime);
        }
        updateStartDateTimeForNextFlowNodesWithoutOwnStartMapping(
          eventId, eventTimeStampAsOffsetDateTime, eventToFlowNodeMapping, processInstanceDto
        );
        break;
      default:
        throw new OptimizeRuntimeException("Unsupported mappedAs type: " + eventToFlowNodeMapping.getMappedAs());
    }
    processInstanceDto.getFlowNodeInstances().add(flowNodeInstance);
  }

  private void updateInstanceForSingleMappedBpmnNodeEvent(final EventProcessInstanceDto processInstanceDto,
                                                          final String eventId,
                                                          final EventToFlowNodeMapping eventToFlowNodeMapping,
                                                          final OffsetDateTime eventTimeStampAsOffsetDateTime,
                                                          final EventCorrelationStateDto eventCorrelationStateDto,
                                                          final FlowNodeInstanceDto flowNodeInstance) {
    // BPMN Events that do not have a timer property should have a duration of 0
    flowNodeInstance.setStartDate(eventTimeStampAsOffsetDateTime);
    eventCorrelationStateDto.getCorrelatedAsToFlowNodeInstanceIds()
      .put(MappedEventType.START, Sets.newHashSet(flowNodeInstance.getFlowNodeInstanceId()));
    flowNodeInstance.setEndDate(eventTimeStampAsOffsetDateTime);
    eventCorrelationStateDto.getCorrelatedAsToFlowNodeInstanceIds()
      .put(MappedEventType.END, Sets.newHashSet(flowNodeInstance.getFlowNodeInstanceId()));
    updateEndDateTimeForPreviousFlowNodesWithoutOwnEndMapping(
      eventId, eventTimeStampAsOffsetDateTime, eventToFlowNodeMapping, processInstanceDto
    );
    updateStartDateTimeForNextFlowNodesWithoutOwnStartMapping(
      eventId, eventTimeStampAsOffsetDateTime, eventToFlowNodeMapping, processInstanceDto
    );
    processInstanceDto.getFlowNodeInstances().add(flowNodeInstance);
  }

  private void updateInstanceForStartAndEndMappedNodeEvent(final EventProcessInstanceDto processInstanceDto,
                                                           final String eventId,
                                                           final EventToFlowNodeMapping eventToFlowNodeMapping,
                                                           final OffsetDateTime eventTimeStampAsOffsetDateTime,
                                                           final EventCorrelationStateDto eventCorrelationStateDto,
                                                           final FlowNodeInstanceDto flowNodeInstance) {
    switch (eventToFlowNodeMapping.getMappedAs()) {
      case START:
        eventCorrelationStateDto.getCorrelatedAsToFlowNodeInstanceIds()
          .put(MappedEventType.START, Sets.newHashSet(flowNodeInstance.getFlowNodeInstanceId()));
        // start events will create actual flow node instances
        flowNodeInstance.setStartDate(eventTimeStampAsOffsetDateTime);
        processInstanceDto.getFlowNodeInstances().add(flowNodeInstance);
        updateEndDateTimeForPreviousFlowNodesWithoutOwnEndMapping(
          eventId, eventTimeStampAsOffsetDateTime, eventToFlowNodeMapping, processInstanceDto
        );
        break;
      case END:
        eventCorrelationStateDto.getCorrelatedAsToFlowNodeInstanceIds()
          .put(MappedEventType.END, Sets.newHashSet(flowNodeInstance.getFlowNodeInstanceId()));
        // end events we only handle as updates to not create duplicate instances
        flowNodeInstance.setEndDate(eventTimeStampAsOffsetDateTime);
        final FlowNodeInstanceUpdateDto previousMappingEventUpdate = FlowNodeInstanceUpdateDto.builder()
          .sourceEventId(eventId)
          .flowNodeId(eventToFlowNodeMapping.getFlowNodeId())
          .flowNodeType(eventToFlowNodeMapping.getFlowNodeType())
          .mappedAs(MappedEventType.END)
          .date(eventTimeStampAsOffsetDateTime)
          .build();
        processInstanceDto.getPendingFlowNodeInstanceUpdates().add(previousMappingEventUpdate);
        updateStartDateTimeForNextFlowNodesWithoutOwnStartMapping(
          eventId, eventTimeStampAsOffsetDateTime, eventToFlowNodeMapping, processInstanceDto
        );
        break;
      default:
        throw new OptimizeRuntimeException("Unsupported mappedAs type: " + eventToFlowNodeMapping.getMappedAs());
    }
  }

  private boolean noStartMappingOnAtLeastOneOfTheNextMappedFlowNodes(final EventToFlowNodeMapping eventToFlowNodeMapping) {
    return eventToFlowNodeMapping.getNextMappedFlowNodeIds().isEmpty()
      || eventToFlowNodeMapping.getNextMappedFlowNodeIds()
      .stream()
      .anyMatch(nextFlowNodeId -> {
        final EventMappingDto nextFlowNodeMapping = eventProcessPublishStateDto.getMappings().get(nextFlowNodeId);
        return nextFlowNodeMapping != null && nextFlowNodeMapping.getStart() == null;
      });
  }

  private boolean isZeroDurationBpmnEvent(final String flowNodeId) {
    final Class<? extends ModelElementInstance> nodeClass = bpmnModelInstance.getModelElementById(flowNodeId)
      .getClass();
    return Event.class.isAssignableFrom(nodeClass) && !IntermediateCatchEvent.class.isAssignableFrom(nodeClass);
  }

  private void updateStartDateTimeForNextFlowNodesWithoutOwnStartMapping(final String updateSourceEventId,
                                                                         final OffsetDateTime eventDateTime,
                                                                         final EventToFlowNodeMapping eventToFlowNodeMapping,
                                                                         final EventProcessInstanceDto processInstanceDto) {
    eventToFlowNodeMapping.getNextMappedFlowNodeIds().forEach(nextFlowNodeId -> {
      final EventMappingDto nextFlowNodeMapping = eventProcessPublishStateDto.getMappings().get(nextFlowNodeId);
      if (nextFlowNodeMapping != null && nextFlowNodeMapping.getStart() == null && !isZeroDurationBpmnEvent(
        nextFlowNodeId)) {
        final FlowNodeInstanceUpdateDto nextFlowNodeInstanceUpdate = FlowNodeInstanceUpdateDto.builder()
          .sourceEventId(updateSourceEventId)
          .flowNodeId(nextFlowNodeId)
          .flowNodeType(getModelElementType(nextFlowNodeId))
          .mappedAs(MappedEventType.START)
          .date(eventDateTime)
          .build();
        processInstanceDto.getPendingFlowNodeInstanceUpdates().add(nextFlowNodeInstanceUpdate);
      }
    });
  }

  private void updateEndDateTimeForPreviousFlowNodesWithoutOwnEndMapping(final String updateSourceEventId,
                                                                         final OffsetDateTime eventDateTime,
                                                                         final EventToFlowNodeMapping eventToFlowNodeMapping,
                                                                         final EventProcessInstanceDto processInstanceDto) {
    eventToFlowNodeMapping.getPreviousMappedFlowNodeIds().forEach(previousFlowNodeId -> {
      final EventMappingDto previousFlowNodeMapping = eventProcessPublishStateDto.getMappings().get(previousFlowNodeId);
      if (previousFlowNodeMapping != null && previousFlowNodeMapping.getEnd() == null && !isZeroDurationBpmnEvent(
        previousFlowNodeId)) {
        final FlowNodeInstanceUpdateDto previousFlowNodeUpdate = FlowNodeInstanceUpdateDto.builder()
          .sourceEventId(updateSourceEventId)
          .flowNodeId(previousFlowNodeId)
          .flowNodeType(getModelElementType(previousFlowNodeId))
          .mappedAs(MappedEventType.END)
          .date(eventDateTime)
          .build();
        processInstanceDto.getPendingFlowNodeInstanceUpdates().add(previousFlowNodeUpdate);
      }
    });
  }

  private String getModelElementType(final String nextFlowNodeId) {
    return bpmnModelInstance.getModelElementById(nextFlowNodeId).getElementType().getTypeName();
  }

  private List<SimpleProcessVariableDto> extractSimpleVariables(final EventDto eventDto) {
    final List<SimpleProcessVariableDto> result = new ArrayList<>();
    if (eventDto.getData() != null) {
      if (eventDto.getData() instanceof Map) {
        final Map<String, Object> data = (Map<String, Object>) eventDto.getData();
        data.entrySet().stream()
          .filter(stringObjectEntry -> stringObjectEntry.getValue() != null)
          .forEach(stringObjectEntry -> result.add(new SimpleProcessVariableDto(
            stringObjectEntry.getKey(),
            stringObjectEntry.getKey(),
            extractVariableType(stringObjectEntry.getValue()),
            extractVariableValue(stringObjectEntry.getValue()),
            1
          )));
      }
    }
    return result;
  }

  @SuppressWarnings(UNCHECKED_CAST)
  private String extractVariableType(final Object variableValue) {
    if (variableValue instanceof Collection) {
      List<Object> valueList = (List<Object>) variableValue;
      return valueList.isEmpty()
        ? VariableType.STRING.getId()
        : getVariableTypeFromValuesList((List<Object>) variableValue);
    } else {
      return variableValue.getClass().getSimpleName();
    }
  }

  private String getVariableTypeFromValuesList(final List<Object> variableValues) {
    if (variableValues.get(0) instanceof Number) {
      return VariableType.DOUBLE.getId();
    } else {
      return variableValues.get(0).getClass().getSimpleName();
    }
  }

  @SuppressWarnings(UNCHECKED_CAST)
  private List<String> extractVariableValue(final Object variableValue) {
    if (variableValue instanceof Collection) {
      return (List<String>) variableValue;
    } else {
      return Collections.singletonList(variableValue.toString());
    }
  }

  private ElasticsearchImportJob<EventProcessInstanceDto> createElasticsearchImportJob(List<EventProcessInstanceDto> processInstances,
                                                                                       Runnable callback) {
    EventProcessInstanceElasticsearchImportJob importJob = new EventProcessInstanceElasticsearchImportJob(
      eventProcessInstanceWriter, callback
    );
    importJob.setEntitiesToImport(processInstances);
    return importJob;
  }

  private Map<String, EventToFlowNodeMapping> extractMappingByEventIdentifier(
    final EventProcessPublishStateDto eventProcessPublishState,
    final BpmnModelInstance bpmnModelInstance) {

    final Set<String> mappedFlowNodeIds = eventProcessPublishState.getMappings().keySet();
    return eventProcessPublishState.getMappings().entrySet()
      .stream()
      .flatMap(flowNodeAndEventMapping -> {
        final EventMappingDto mappingValue = flowNodeAndEventMapping.getValue();
        final String flowNodeId = flowNodeAndEventMapping.getKey();
        return Stream.of(
          convertToFlowNodeMapping(
            mappingValue.getStart(), MappedEventType.START, flowNodeId, bpmnModelInstance, mappedFlowNodeIds
          )
            .orElse(null),
          convertToFlowNodeMapping(
            mappingValue.getEnd(), MappedEventType.END, flowNodeId, bpmnModelInstance, mappedFlowNodeIds
          )
            .orElse(null)
        ).filter(Objects::nonNull);
      })
      .collect(toMap(EventToFlowNodeMapping::getEventIdentifier, Function.identity()));
  }

  private Optional<EventToFlowNodeMapping> convertToFlowNodeMapping(final EventTypeDto event,
                                                                    final MappedEventType mappedAs,
                                                                    final String flowNodeId,
                                                                    final BpmnModelInstance bpmModel,
                                                                    final Set<String> mappedFlowNodeIds) {
    return Optional.ofNullable(event)
      .map(value -> {
        final FlowNode flowNode = bpmModel.getModelElementById(flowNodeId);
        return EventToFlowNodeMapping.builder()
          .eventIdentifier(getMappingIdentifier(value))
          .mappedAs(mappedAs)
          .flowNodeId(flowNodeId)
          .flowNodeType(flowNode.getElementType().getTypeName())
          .previousMappedFlowNodeIds(getPreviousMappedFlowNodeIdsForFlowNode(flowNode, mappedFlowNodeIds))
          .nextMappedFlowNodeIds(getNextMappedFlowNodeIdsForFlowNode(flowNode, mappedFlowNodeIds))
          .build();
      });
  }

  private List<String> getPreviousMappedFlowNodeIdsForFlowNode(final FlowNode flowNode,
                                                               final Set<String> mappedFlowNodeIds) {
    return new ArrayList<>(findMappedNodes(
      Sets.newHashSet(),
      Sets.newHashSet(flowNode),
      mappedFlowNodeIds,
      MAX_TRAVERSAL_DISTANCE,
      FlowNode::getPreviousNodes
    ));
  }

  private List<String> getNextMappedFlowNodeIdsForFlowNode(final FlowNode flowNode,
                                                           final Set<String> mappedFlowNodeIds) {
    return new ArrayList<>(findMappedNodes(
      Sets.newHashSet(),
      Sets.newHashSet(flowNode),
      mappedFlowNodeIds,
      MAX_TRAVERSAL_DISTANCE,
      FlowNode::getSucceedingNodes
    ));
  }

  private Set<String> findMappedNodes(Set<String> currentMappedFound,
                                      Set<FlowNode> nodesToCheck,
                                      final Set<String> mappedFlowNodeIds,
                                      int distanceRemaining,
                                      Function<FlowNode, Query<FlowNode>> getNeighbourNodeFunction) {
    if (nodesToCheck.isEmpty() || distanceRemaining == 0) {
      return currentMappedFound;
    }
    List<String> newFoundMappedIds = nodesToCheck.stream()
      .flatMap(gateway -> getNeighbourNodeFunction.apply(gateway).list().stream())
      .map(FlowNode::getId)
      .filter(mappedFlowNodeIds::contains)
      .collect(Collectors.toList());
    currentMappedFound.addAll(newFoundMappedIds);
    nodesToCheck = nodesToCheck.stream()
      .flatMap(gateway -> getNeighbourNodeFunction.apply(gateway).list().stream())
      .filter(node -> Gateway.class.isAssignableFrom(node.getClass()))
      .collect(Collectors.toSet());
    return findMappedNodes(currentMappedFound, nodesToCheck, mappedFlowNodeIds,
                           distanceRemaining - 1, getNeighbourNodeFunction
    );
  }

  private String getMappingIdentifier(final EventTypeDto eventTypeDto) {
    return String.join(":", eventTypeDto.getGroup(), eventTypeDto.getSource(), eventTypeDto.getEventName());
  }

  private String getMappingIdentifier(final EventDto eventDto) {
    return String.join(":", eventDto.getGroup(), eventDto.getSource(), eventDto.getEventName());
  }

  private List<EventProcessGatewayDto> buildGatewayLookup(final EventProcessPublishStateDto eventProcessPublishState,
                                                          final BpmnModelInstance bpmnModelInstance) {
    List<EventProcessGatewayDto> eventProcessGatewayDtos = new ArrayList<>();

    final Set<String> mappedFlowNodeIds = eventProcessPublishState.getMappings().keySet();
    if (mappedFlowNodeIds.isEmpty() || bpmnModelInstance.getModelElementsByType(Gateway.class).isEmpty()) {
      log.debug("Either no mappings or no gateways exist, no gateway lookup required");
      return Collections.emptyList();
    }

    Collection<Gateway> gatewaysInModel = bpmnModelInstance.getModelElementsByType(Gateway.class);
    for (Gateway gatewayInModel : gatewaysInModel) {
      String gatewayType = gatewayInModel.getElementType().getTypeName();
      if (!isSupportedGatewayType(gatewayType)) {
        log.debug("Gateway type {} not supported. Gateways will not be added for gateway {}",
                  gatewayType, gatewayInModel.getId()
        );
      } else {
        List<String> previousFlowNodeIds = getSupportedIdsFromFlowNodeList(gatewayInModel.getPreviousNodes().list());
        List<String> nextFlowNodeIds = getSupportedIdsFromFlowNodeList(gatewayInModel.getSucceedingNodes().list());
        if (previousFlowNodeIds.size() == 1 || nextFlowNodeIds.size() == 1) {
          eventProcessGatewayDtos.add(
            EventProcessGatewayDto.builder()
              .id(gatewayInModel.getId())
              .type(gatewayInModel.getElementType().getTypeName())
              .previousNodeIds(previousFlowNodeIds)
              .nextNodeIds(nextFlowNodeIds)
              .build()
          );
        }
      }
    }
    return eventProcessGatewayDtos;
  }

  private boolean isSupportedGatewayType(String gatewayType) {
    return gatewayType.equals(EXCLUSIVE_GATEWAY)
      || gatewayType.equals(PARALLEL_GATEWAY)
      || gatewayType.equals(EVENT_BASED_GATEWAY);
  }

  private List<String> getSupportedIdsFromFlowNodeList(final List<FlowNode> flowNodes) {
    return flowNodes.stream()
      .filter(flowNode -> Event.class.isAssignableFrom(flowNode.getClass())
        || Activity.class.isAssignableFrom(flowNode.getClass())
        || Gateway.class.isAssignableFrom(flowNode.getClass()))
      .map(BaseElement::getId)
      .collect(Collectors.toList());
  }

}
