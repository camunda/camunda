/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.optimize.dto.optimize.persistence.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.FlowNodeInstanceUpdateDto;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventToFlowNodeMapping;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.MappedEventType;
import org.camunda.optimize.dto.optimize.query.event.SimpleEventDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.service.engine.importing.service.ImportService;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.EventProcessInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.EventProcessInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
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
import static org.camunda.optimize.service.engine.importing.BpmnModelUtility.parseBpmnModel;

@AllArgsConstructor
@Slf4j
public class EventProcessInstanceImportService implements ImportService<EventDto> {
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EventProcessInstanceWriter eventProcessInstanceWriter;

  private final EventProcessPublishStateDto eventProcessPublishStateDto;
  private final Map<String, EventToFlowNodeMapping> eventMappingIdToEventMapping;
  private final BpmnModelInstance modelInstance;

  public EventProcessInstanceImportService(final EventProcessPublishStateDto eventProcessPublishStateDto,
                                           final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                           final EventProcessInstanceWriter eventProcessInstanceWriter) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.eventProcessInstanceWriter = eventProcessInstanceWriter;
    this.eventProcessPublishStateDto = eventProcessPublishStateDto;

    this.modelInstance = parseBpmnModel(eventProcessPublishStateDto.getXml());
    this.eventMappingIdToEventMapping = extractMappingByEventIdentifier(
      eventProcessPublishStateDto, this.modelInstance
    );
  }

  @Override
  public void executeImport(final List<EventDto> pageOfEvents, final Runnable callback) {
    log.trace("Importing entities for event process mapping [{}].", eventProcessPublishStateDto.getProcessMappingId());

    boolean newDataIsAvailable = !pageOfEvents.isEmpty();
    if (newDataIsAvailable) {
      final List<EventProcessInstanceDto> newOptimizeEntities = mapToProcessInstances(pageOfEvents);
      elasticsearchImportJobExecutor.executeImportJob(
        createElasticsearchImportJob(newOptimizeEntities, callback)
      );
    } else {
      callback.run();
    }
  }

  public void shutdown() {
    elasticsearchImportJobExecutor.shutdown();
  }

  private List<EventProcessInstanceDto> mapToProcessInstances(final List<EventDto> engineEntities) {
    final Map<String, List<EventDto>> eventsGroupedByTraceId = engineEntities.stream()
      .filter(eventDto -> eventMappingIdToEventMapping.containsKey(getMappingIdentifier(eventDto)))
      // for the same event id we want the last ingested event to win to allow updates
      .sorted(Comparator.comparing(EventDto::getIngestionTimestamp).reversed())
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

    addFlowNodeInstances(eventTraceGroup, processInstanceDto);

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

  private void addFlowNodeInstances(final Map.Entry<String, List<EventDto>> eventTraceGroup,
                                    final EventProcessInstanceDto processInstanceDto) {
    eventTraceGroup.getValue()
      .forEach(eventDto -> {
        final String eventId = eventDto.getId();
        final EventToFlowNodeMapping eventToFlowNodeMapping =
          eventMappingIdToEventMapping.get(getMappingIdentifier(eventDto));
        final OffsetDateTime eventTimeStampAsOffsetDateTime = OffsetDateTime.ofInstant(
          Instant.ofEpochMilli(eventDto.getTimestamp()), ZoneId.systemDefault()
        );

        final SimpleEventDto flowNodeInstance = SimpleEventDto.builder()
          .id(eventId)
          .activityId(eventToFlowNodeMapping.getFlowNodeId())
          .activityType(eventToFlowNodeMapping.getFlowNodeType())
          .build();
        processInstanceDto.getEvents().add(flowNodeInstance);

        final EventMappingDto eventMapping = eventProcessPublishStateDto.getMappings()
          .get(eventToFlowNodeMapping.getFlowNodeId());
        if (eventMapping.getStart() != null && eventMapping.getEnd() != null) {
          // multiple events are mapped to one activity
          // TODO with OPT-3015 we need to correlate start & end on write
          switch (eventToFlowNodeMapping.getMappedAs()) {
            case START:
              flowNodeInstance.setStartDate(eventTimeStampAsOffsetDateTime);
              break;
            case END:
              flowNodeInstance.setEndDate(eventTimeStampAsOffsetDateTime);
              break;
            default:
              throw new OptimizeRuntimeException("Unsupported mappedAs type: " + eventToFlowNodeMapping.getMappedAs());
          }
        } else {
          switch (eventToFlowNodeMapping.getMappedAs()) {
            case START:
              flowNodeInstance.setStartDate(eventTimeStampAsOffsetDateTime);
              if (eventToFlowNodeMapping.getNextMappedFlowNodeIds().isEmpty()) {
                flowNodeInstance.setEndDate(eventTimeStampAsOffsetDateTime);
              }
              break;
            case END:
              flowNodeInstance.setEndDate(eventTimeStampAsOffsetDateTime);
              if (eventToFlowNodeMapping.getPreviousMappedFlowNodeIds().isEmpty()) {
                flowNodeInstance.setStartDate(eventTimeStampAsOffsetDateTime);
              }
              break;
            default:
              throw new OptimizeRuntimeException("Unsupported mappedAs type: " + eventToFlowNodeMapping.getMappedAs());
          }
          updateEndDateTimeForPreviousFlowNodesWithoutOwnEndMapping(
            eventId, eventTimeStampAsOffsetDateTime, eventToFlowNodeMapping, processInstanceDto
          );
          updateStartDateTimeForNextFlowNodesWithoutOwnStartMapping(
            eventId, eventTimeStampAsOffsetDateTime, eventToFlowNodeMapping, processInstanceDto
          );
        }
      });

  }

  private void updateStartDateTimeForNextFlowNodesWithoutOwnStartMapping(final String updateSourceEventId,
                                                                         final OffsetDateTime eventDateTime,
                                                                         final EventToFlowNodeMapping eventToFlowNodeMapping,
                                                                         final EventProcessInstanceDto processInstanceDto) {
    eventToFlowNodeMapping.getNextMappedFlowNodeIds().forEach(nextFlowNodeId -> {
      final EventMappingDto nextFlowNodeMapping = eventProcessPublishStateDto.getMappings().get(nextFlowNodeId);
      if (nextFlowNodeMapping != null && nextFlowNodeMapping.getStart() == null) {
        final FlowNodeInstanceUpdateDto nextFlowNodeInstanceUpdate = FlowNodeInstanceUpdateDto.builder()
          .sourceEventId(updateSourceEventId)
          .flowNodeId(nextFlowNodeId)
          .flowNodeType(getModelElementType(nextFlowNodeId))
          .startDate(eventDateTime)
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
      if (previousFlowNodeMapping != null && previousFlowNodeMapping.getEnd() == null) {
        final FlowNodeInstanceUpdateDto previousFlowNodeUpdate = FlowNodeInstanceUpdateDto.builder()
          .sourceEventId(updateSourceEventId)
          .flowNodeId(previousFlowNodeId)
          .flowNodeType(getModelElementType(previousFlowNodeId))
          .endDate(eventDateTime)
          .build();
        processInstanceDto.getPendingFlowNodeInstanceUpdates().add(previousFlowNodeUpdate);
      }
    });
  }

  private String getModelElementType(final String nextFlowNodeId) {
    return modelInstance.getModelElementById(nextFlowNodeId).getElementType().getTypeName();
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
            stringObjectEntry.getValue().getClass().getSimpleName(),
            stringObjectEntry.getValue().toString(),
            1
          )));
      }
    }
    return result;
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
          .previousMappedFlowNodeIds(
            flowNode.getPreviousNodes().list().stream()
              .map(FlowNode::getId)
              .filter(mappedFlowNodeIds::contains)
              .collect(Collectors.toList())
          )
          .nextMappedFlowNodeIds(
            flowNode.getSucceedingNodes().list().stream()
              .map(FlowNode::getId)
              .filter(mappedFlowNodeIds::contains)
              .collect(Collectors.toList())
          )
          .build();
      });
  }

  private String getMappingIdentifier(final EventDto eventDto) {
    return String.join(":", eventDto.getGroup(), eventDto.getSource(), eventDto.getEventName());
  }

  private String getMappingIdentifier(final EventTypeDto eventTypeDto) {
    return String.join(":", eventTypeDto.getGroup(), eventTypeDto.getSource(), eventTypeDto.getEventName());
  }

}
