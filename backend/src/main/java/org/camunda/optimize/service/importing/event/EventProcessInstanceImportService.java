/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
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

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

  public EventProcessInstanceImportService(final EventProcessPublishStateDto eventProcessPublishStateDto,
                                           final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                           final EventProcessInstanceWriter eventProcessInstanceWriter) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.eventProcessInstanceWriter = eventProcessInstanceWriter;
    this.eventProcessPublishStateDto = eventProcessPublishStateDto;

    this.eventMappingIdToEventMapping = extractMappingByEventIdentifier(eventProcessPublishStateDto);
  }

  @Override
  public void executeImport(final List<EventDto> pageOfEvents, final Runnable callback) {
    log.trace("Importing entities for event process mapping [{}].", eventProcessPublishStateDto.getProcessMappingId());

    boolean newDataIsAvailable = !pageOfEvents.isEmpty();
    if (newDataIsAvailable) {
      final List<ProcessInstanceDto> newOptimizeEntities = mapToProcessInstances(pageOfEvents);
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

  private List<ProcessInstanceDto> mapToProcessInstances(final List<EventDto> engineEntities) {
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

  private ProcessInstanceDto mapToProcessInstanceDto(final Map.Entry<String, List<EventDto>> eventTraceGroup) {
    final String processInstanceId = eventTraceGroup.getKey();

    final ProcessInstanceDto processInstanceDto = ProcessInstanceDto.builder()
      .processInstanceId(processInstanceId)
      .processDefinitionKey(eventProcessPublishStateDto.getProcessMappingId())
      .processDefinitionId(eventProcessPublishStateDto.getProcessMappingId())
      .processDefinitionVersion("1")
      .build();

    processInstanceDto.setEvents(
      eventTraceGroup.getValue()
        .stream()
        .map(this::mapToSimpleEventDto)
        .collect(Collectors.toList())
    );

    processInstanceDto.setVariables(
      eventTraceGroup.getValue()
        .stream()
        .map(this::extractSimpleVariables)
        .flatMap(Collection::stream)
        .distinct()
        .collect(Collectors.toList())
    );

    return processInstanceDto;
  }

  private SimpleEventDto mapToSimpleEventDto(final EventDto eventDto) {
    final EventToFlowNodeMapping eventMapping = eventMappingIdToEventMapping.get(getMappingIdentifier(eventDto));
    final OffsetDateTime eventTimeStampAsOffsetDateTime = OffsetDateTime.ofInstant(
      Instant.ofEpochMilli(eventDto.getTimestamp()), ZoneId.systemDefault()
    );

    final SimpleEventDto activityInstanceDto = SimpleEventDto.builder()
      .id(eventDto.getId())
      .activityId(eventMapping.getFlowNodeId())
      .activityType(eventMapping.getFlowNodeType())
      .build();

    final EventMappingDto flowNodeMapping = eventProcessPublishStateDto.getMappings()
      .get(eventMapping.getFlowNodeId());
    if (flowNodeMapping.getStart() != null && flowNodeMapping.getEnd() != null) {
      // multiple events are mapped to one activity
      // TODO with OPT-3015 we need to correlate start & end on write
      switch (eventMapping.getMappedAs()) {
        case START:
          activityInstanceDto.setStartDate(eventTimeStampAsOffsetDateTime);
          break;
        case END:
          activityInstanceDto.setEndDate(eventTimeStampAsOffsetDateTime);
          break;
        default:
          throw new OptimizeRuntimeException("Unsupported mappedAs type: " + eventMapping.getMappedAs());
      }
    } else {
      // if only a single event is mapped everything can get calculated
      final OffsetDateTime startDate;
      final OffsetDateTime endDate;
      switch (eventMapping.getMappedAs()) {
        case START:
          startDate = eventTimeStampAsOffsetDateTime;
          endDate = Optional.ofNullable(eventDto.getDuration())
            .map(value -> startDate.plus(value, ChronoUnit.MILLIS))
            .orElse(eventTimeStampAsOffsetDateTime);
          break;
        case END:
          endDate = eventTimeStampAsOffsetDateTime;
          startDate = Optional.ofNullable(eventDto.getDuration())
            .map(value -> endDate.minus(value, ChronoUnit.MILLIS))
            .orElse(eventTimeStampAsOffsetDateTime);
          break;
        default:
          throw new OptimizeRuntimeException("Unsupported mappedAs type: " + eventMapping.getMappedAs());
      }
      activityInstanceDto.setStartDate(startDate);
      activityInstanceDto.setEndDate(endDate);
      activityInstanceDto.setDurationInMs(Duration.between(startDate, endDate).toMillis());
    }

    return activityInstanceDto;
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

  private ElasticsearchImportJob<ProcessInstanceDto> createElasticsearchImportJob(List<ProcessInstanceDto> processInstances,
                                                                                  Runnable callback) {
    EventProcessInstanceElasticsearchImportJob importJob = new EventProcessInstanceElasticsearchImportJob(
      eventProcessInstanceWriter, callback
    );
    importJob.setEntitiesToImport(processInstances);
    return importJob;
  }

  private Map<String, EventToFlowNodeMapping> extractMappingByEventIdentifier(
    final EventProcessPublishStateDto eventProcessDefinitionDto) {

    final BpmnModelInstance bpmnModelInstance = parseBpmnModel(eventProcessDefinitionDto.getXml());
    return eventProcessDefinitionDto.getMappings().entrySet()
      .stream()
      .flatMap(flowNodeAndEventMapping -> {
        final EventMappingDto mappingValue = flowNodeAndEventMapping.getValue();
        final String flowNodeId = flowNodeAndEventMapping.getKey();
        return Stream.of(
          convertToFlowNodeMapping(mappingValue.getStart(), MappedEventType.START, flowNodeId, bpmnModelInstance)
            .orElse(null),
          convertToFlowNodeMapping(mappingValue.getEnd(), MappedEventType.END, flowNodeId, bpmnModelInstance)
            .orElse(null)
        ).filter(Objects::nonNull);
      })
      .collect(toMap(EventToFlowNodeMapping::getEventIdentifier, Function.identity()));
  }

  private Optional<EventToFlowNodeMapping> convertToFlowNodeMapping(final EventTypeDto event,
                                                                    final MappedEventType mappedAs,
                                                                    final String flowNodeId,
                                                                    final BpmnModelInstance bpmModel) {
    return Optional.ofNullable(event)
      .map(value -> new EventToFlowNodeMapping(
        getMappingIdentifier(value),
        mappedAs,
        flowNodeId,
        bpmModel.getModelElementById(flowNodeId).getElementType().getTypeName()
      ));
  }

  private String getMappingIdentifier(final EventDto eventDto) {
    return String.join(":", eventDto.getGroup(), eventDto.getSource(), eventDto.getEventName());
  }

  private String getMappingIdentifier(final EventTypeDto eventTypeDto) {
    return String.join(":", eventTypeDto.getGroup(), eventTypeDto.getSource(), eventTypeDto.getEventName());
  }

}
