/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.eventprocess.service;

import lombok.AllArgsConstructor;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessState;
import org.camunda.optimize.service.EventProcessDefinitionService;
import org.camunda.optimize.service.importing.eventprocess.EventProcessInstanceIndexManager;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.util.BpmnModelUtil;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
public class EventProcessDefinitionImportService {

  private final EventProcessDefinitionService eventProcessDefinitionService;
  private final ReportService reportService;
  private final EventProcessInstanceIndexManager eventProcessInstanceIndexManager;

  public void syncPublishedEventProcessDefinitions() {
    final Set<String> publishedStateProcessIds = new HashSet<>();
    final List<EventProcessPublishStateDto> publishedEventProcesses = eventProcessInstanceIndexManager
      .getPublishedInstanceStates()
      .stream()
      .filter(eventProcessPublishStateDto -> EventProcessState.PUBLISHED.equals(eventProcessPublishStateDto.getState()))
      .peek(eventProcessPublishStateDto -> publishedStateProcessIds.add(eventProcessPublishStateDto.getId()))
      .collect(Collectors.toList());

    final Set<String> existingEventProcessDefinitionIds =
      eventProcessDefinitionService.getAllEventProcessesDefinitionsOmitXml()
        .stream()
        .map(EventProcessDefinitionDto::getId)
        .collect(Collectors.toSet());

    final List<EventProcessDefinitionDto> newOrUpdatedDefinitions = publishedEventProcesses.stream()
      .filter(eventProcessPublishStateDto -> !existingEventProcessDefinitionIds.contains(eventProcessPublishStateDto.getId()))
      .map(this::createEventProcessDefinitionDto)
      .collect(Collectors.toList());
    if (!newOrUpdatedDefinitions.isEmpty()) {
      eventProcessDefinitionService.importEventProcessDefinitions(newOrUpdatedDefinitions);
      updateDefinitionXmlsInReports(newOrUpdatedDefinitions);
    }

    final Set<String> definitionIdsToDelete = existingEventProcessDefinitionIds.stream()
      .filter(definitionId -> !publishedStateProcessIds.contains(definitionId))
      .collect(Collectors.toSet());
    if (!definitionIdsToDelete.isEmpty()) {
      eventProcessDefinitionService.deleteEventProcessDefinitions(definitionIdsToDelete);
    }
  }

  private void updateDefinitionXmlsInReports(final List<EventProcessDefinitionDto> definitions) {
    definitions.forEach(eventProcessDefinitionDto -> {
      reportService.updateDefinitionXmlOfProcessReports(
        eventProcessDefinitionDto.getKey(), eventProcessDefinitionDto.getBpmn20Xml()
      );
    });
  }

  private EventProcessDefinitionDto createEventProcessDefinitionDto(final EventProcessPublishStateDto eventProcessPublishStateDto) {
    final BpmnModelInstance bpmnModelInstance = BpmnModelUtil.parseBpmnModel(eventProcessPublishStateDto.getXml());
    return EventProcessDefinitionDto.eventProcessBuilder()
      .id(eventProcessPublishStateDto.getId())
      .key(eventProcessPublishStateDto.getProcessMappingId())
      .version("1")
      .name(eventProcessPublishStateDto.getName())
      .tenantId(null)
      .bpmn20Xml(eventProcessPublishStateDto.getXml())
      .deleted(false)
      .flowNodeData(BpmnModelUtil.extractFlowNodeData(bpmnModelInstance))
      .userTaskNames(BpmnModelUtil.extractUserTaskNames(bpmnModelInstance))
      .build();
  }

}
