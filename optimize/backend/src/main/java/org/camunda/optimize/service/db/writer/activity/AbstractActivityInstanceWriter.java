/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer.activity;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.PROCESS_INSTANCE_INDEX;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.RequestType;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.db.repository.IndexRepository;
import org.camunda.optimize.service.db.schema.ScriptData;
import org.camunda.optimize.service.db.writer.DatabaseWriterUtil;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public abstract class AbstractActivityInstanceWriter {
  private final ObjectMapper objectMapper;
  private final IndexRepository indexRepository;

  public List<ImportRequestDto> generateActivityInstanceImports(
      final List<FlowNodeEventDto> activityInstances) {
    final String importItemName = "activity instances";
    log.debug("Creating imports for {} [{}].", activityInstances.size(), importItemName);

    final Set<String> keys =
        activityInstances.stream()
            .map(FlowNodeEventDto::getProcessDefinitionKey)
            .collect(Collectors.toSet());

    indexRepository.createMissingIndices(
        PROCESS_INSTANCE_INDEX, Set.of(PROCESS_INSTANCE_MULTI_ALIAS), keys);

    Map<String, List<FlowNodeEventDto>> processInstanceToEvents = new HashMap<>();
    for (FlowNodeEventDto e : activityInstances) {
      if (!processInstanceToEvents.containsKey(e.getProcessInstanceId())) {
        processInstanceToEvents.put(e.getProcessInstanceId(), new ArrayList<>());
      }
      processInstanceToEvents.get(e.getProcessInstanceId()).add(e);
    }

    return processInstanceToEvents.entrySet().stream()
        .map(entry -> createImportRequestForActivityInstance(entry, importItemName))
        .collect(Collectors.toList());
  }

  public FlowNodeInstanceDto fromActivityInstance(final FlowNodeEventDto activityInstance) {
    return new FlowNodeInstanceDto(
            activityInstance.getProcessDefinitionKey(),
            activityInstance.getProcessDefinitionVersion(),
            activityInstance.getTenantId(),
            activityInstance.getEngineAlias(),
            activityInstance.getProcessInstanceId(),
            activityInstance.getActivityId(),
            activityInstance.getActivityType(),
            activityInstance.getId(),
            activityInstance.getTaskId())
        .setTotalDurationInMs(activityInstance.getDurationInMs())
        .setStartDate(activityInstance.getStartDate())
        .setEndDate(activityInstance.getEndDate())
        .setCanceled(activityInstance.getCanceled());
  }

  protected abstract String createInlineUpdateScript();

  private ImportRequestDto createImportRequestForActivityInstance(
      final Map.Entry<String, List<FlowNodeEventDto>> activitiesByProcessInstance,
      final String importItemName) {
    final List<FlowNodeEventDto> activityInstances = activitiesByProcessInstance.getValue();
    final String processInstanceId = activitiesByProcessInstance.getKey();

    final List<FlowNodeInstanceDto> flowNodeInstanceDtos =
        convertToFlowNodeInstanceDtos(activityInstances);
    final Map<String, Object> params = new HashMap<>();
    params.put(FLOW_NODE_INSTANCES, flowNodeInstanceDtos);
    final ScriptData updateScript =
        DatabaseWriterUtil.createScriptData(createInlineUpdateScript(), params, objectMapper);

    final ProcessInstanceDto procInst =
        ProcessInstanceDto.builder()
            .processInstanceId(processInstanceId)
            .dataSource(new EngineDataSourceDto(activityInstances.get(0).getEngineAlias()))
            .processDefinitionKey(activityInstances.get(0).getProcessDefinitionKey())
            .flowNodeInstances(flowNodeInstanceDtos)
            .build();
    return ImportRequestDto.builder()
        .indexName(getProcessInstanceIndexAliasName(procInst.getProcessDefinitionKey()))
        .id(processInstanceId)
        .scriptData(updateScript)
        .type(RequestType.UPDATE)
        .importName(importItemName)
        .source(procInst)
        .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
        .build();
  }

  private List<FlowNodeInstanceDto> convertToFlowNodeInstanceDtos(
      final List<FlowNodeEventDto> activityInstances) {
    return activityInstances.stream().map(this::fromActivityInstance).collect(Collectors.toList());
  }
}
