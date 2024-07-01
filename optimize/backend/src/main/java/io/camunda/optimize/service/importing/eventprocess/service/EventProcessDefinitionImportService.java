/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.eventprocess.service;

import io.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessState;
import io.camunda.optimize.service.EventProcessDefinitionService;
import io.camunda.optimize.service.db.EventProcessInstanceIndexManager;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.util.BpmnModelUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class EventProcessDefinitionImportService {

  private final EventProcessDefinitionService eventProcessDefinitionService;
  private final ReportService reportService;
  private final EventProcessInstanceIndexManager eventProcessInstanceIndexManager;

  public void syncPublishedEventProcessDefinitions() {
    final Set<String> publishedStateProcessIds = new HashSet<>();
    final List<EventProcessPublishStateDto> publishedEventProcesses =
        eventProcessInstanceIndexManager.getPublishedInstanceStates().stream()
            .filter(
                eventProcessPublishStateDto ->
                    EventProcessState.PUBLISHED.equals(eventProcessPublishStateDto.getState()))
            .peek(
                eventProcessPublishStateDto ->
                    publishedStateProcessIds.add(eventProcessPublishStateDto.getId()))
            .toList();

    final Set<String> existingEventProcessDefinitionIds =
        eventProcessDefinitionService.getAllEventProcessesDefinitionsOmitXml().stream()
            .map(EventProcessDefinitionDto::getId)
            .collect(Collectors.toSet());

    final List<EventProcessDefinitionDto> newOrUpdatedDefinitions =
        publishedEventProcesses.stream()
            .filter(
                eventProcessPublishStateDto ->
                    !existingEventProcessDefinitionIds.contains(
                        eventProcessPublishStateDto.getId()))
            .map(this::createEventProcessDefinitionDto)
            .toList();
    if (!newOrUpdatedDefinitions.isEmpty()) {
      eventProcessDefinitionService.importEventProcessDefinitions(newOrUpdatedDefinitions);
      updateDefinitionXmlsInReports(newOrUpdatedDefinitions);
    }

    final Set<String> definitionIdsToDelete =
        existingEventProcessDefinitionIds.stream()
            .filter(definitionId -> !publishedStateProcessIds.contains(definitionId))
            .collect(Collectors.toSet());
    if (!definitionIdsToDelete.isEmpty()) {
      eventProcessDefinitionService.deleteEventProcessDefinitions(definitionIdsToDelete);
    }
  }

  private void updateDefinitionXmlsInReports(final List<EventProcessDefinitionDto> definitions) {
    definitions.forEach(
        eventProcessDefinitionDto ->
            reportService.updateDefinitionXmlOfProcessReports(
                eventProcessDefinitionDto.getKey(), eventProcessDefinitionDto.getBpmn20Xml()));
  }

  private EventProcessDefinitionDto createEventProcessDefinitionDto(
      final EventProcessPublishStateDto eventProcessPublishStateDto) {
    final BpmnModelInstance bpmnModelInstance =
        BpmnModelUtil.parseBpmnModel(eventProcessPublishStateDto.getXml());
    return EventProcessDefinitionDto.eventProcessBuilder()
        .id(eventProcessPublishStateDto.getId())
        .key(eventProcessPublishStateDto.getProcessMappingId())
        .version("1")
        .name(eventProcessPublishStateDto.getName())
        .tenantId(null)
        .bpmn20Xml(eventProcessPublishStateDto.getXml())
        .deleted(false)
        .onboarded(true)
        .flowNodeData(BpmnModelUtil.extractFlowNodeData(bpmnModelInstance))
        .userTaskNames(BpmnModelUtil.extractUserTaskNames(bpmnModelInstance))
        .build();
  }
}
