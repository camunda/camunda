/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer.usertask;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.PROCESS_INSTANCE_INDEX;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.RequestType;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.db.repository.IndexRepository;
import org.camunda.optimize.service.db.schema.ScriptData;
import org.camunda.optimize.service.db.writer.DatabaseWriterUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public abstract class AbstractUserTaskWriter {

  protected final IndexRepository indexRepository;
  protected final ObjectMapper objectMapper;

  protected abstract String createInlineUpdateScript();

  public List<ImportRequestDto> generateUserTaskImports(
      final String importItemName, final List<FlowNodeInstanceDto> userTaskInstances) {
    log.debug("Writing [{}] {} to Database.", userTaskInstances.size(), importItemName);

    final Set<String> keys =
        userTaskInstances.stream()
            .map(FlowNodeInstanceDto::getDefinitionKey)
            .collect(Collectors.toSet());
    indexRepository.createMissingIndices(
        PROCESS_INSTANCE_INDEX, Set.of(PROCESS_INSTANCE_MULTI_ALIAS), keys);

    Map<String, List<FlowNodeInstanceDto>> userTaskToProcessInstance = new HashMap<>();
    for (FlowNodeInstanceDto userTask : userTaskInstances) {
      userTaskToProcessInstance.putIfAbsent(userTask.getProcessInstanceId(), new ArrayList<>());
      userTaskToProcessInstance.get(userTask.getProcessInstanceId()).add(userTask);
    }

    return userTaskToProcessInstance.entrySet().stream()
        .map(entry -> createUserTaskUpdateImportRequest(entry, importItemName))
        .collect(Collectors.toList());
  }

  private ScriptData createUpdateScript(final List<FlowNodeInstanceDto> userTasks) {
    final ImmutableMap<String, Object> scriptParameters =
        ImmutableMap.of(FLOW_NODE_INSTANCES, userTasks);
    return DatabaseWriterUtil.createScriptData(
        createInlineUpdateScript(), scriptParameters, objectMapper);
  }

  protected ImportRequestDto createUserTaskUpdateImportRequest(
      final Map.Entry<String, List<FlowNodeInstanceDto>> userTaskInstanceEntry,
      final String importName) {
    final List<FlowNodeInstanceDto> userTasks = userTaskInstanceEntry.getValue();
    final String processInstanceId = userTaskInstanceEntry.getKey();

    final ScriptData updateScriptData = createUpdateScript(userTasks);

    final FlowNodeInstanceDto firstUserTaskInstance =
        userTasks.stream()
            .findFirst()
            .orElseThrow(() -> new OptimizeRuntimeException("No user tasks to import provided"));
    final ProcessInstanceDto procInst =
        ProcessInstanceDto.builder()
            .processInstanceId(processInstanceId)
            .dataSource(new EngineDataSourceDto(firstUserTaskInstance.getEngine()))
            .flowNodeInstances(userTasks)
            .build();

    return ImportRequestDto.builder()
        .indexName(getProcessInstanceIndexAliasName(firstUserTaskInstance.getDefinitionKey()))
        .id(processInstanceId)
        .scriptData(updateScriptData)
        .importName(importName)
        .source(procInst)
        .type(RequestType.UPDATE)
        .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
        .build();
  }
}
