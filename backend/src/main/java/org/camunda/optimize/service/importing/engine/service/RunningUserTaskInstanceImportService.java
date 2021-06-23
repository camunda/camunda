/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.RunningUserTaskElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.usertask.RunningUserTaskInstanceWriter;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;

@Slf4j
public class RunningUserTaskInstanceImportService implements ImportService<HistoricUserTaskInstanceDto> {
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final RunningUserTaskInstanceWriter runningUserTaskInstanceWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;

  public RunningUserTaskInstanceImportService(final RunningUserTaskInstanceWriter runningUserTaskInstanceWriter,
                                              final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                              final EngineContext engineContext,
                                              final ProcessDefinitionResolverService processDefinitionResolverService) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.runningUserTaskInstanceWriter = runningUserTaskInstanceWriter;
    this.processDefinitionResolverService = processDefinitionResolverService;
  }

  @Override
  public void executeImport(final List<HistoricUserTaskInstanceDto> pageOfEngineEntities,
                            Runnable importCompleteCallback) {
    log.trace("Importing running user task entities from engine...");

    final boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<FlowNodeInstanceDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      final ElasticsearchImportJob<FlowNodeInstanceDto> elasticsearchImportJob = createElasticsearchImportJob(
        newOptimizeEntities, importCompleteCallback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private void addElasticsearchImportJobToQueue(final ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<FlowNodeInstanceDto> mapEngineEntitiesToOptimizeEntities(final List<HistoricUserTaskInstanceDto> engineEntities) {
    return engineEntities.stream()
      .filter(instance -> instance.getProcessInstanceId() != null)
      .map(userTask -> processDefinitionResolverService.enrichEngineDtoWithDefinitionKey(
        engineContext,
        userTask,
        HistoricUserTaskInstanceDto::getProcessDefinitionKey,
        HistoricUserTaskInstanceDto::getProcessDefinitionId,
        HistoricUserTaskInstanceDto::setProcessDefinitionKey
      ))
      .filter(userTask -> userTask.getProcessDefinitionKey() != null)
      .map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<FlowNodeInstanceDto> createElasticsearchImportJob(final List<FlowNodeInstanceDto> userTasks,
                                                                                   Runnable callback) {
    final RunningUserTaskElasticsearchImportJob importJob = new RunningUserTaskElasticsearchImportJob(
      runningUserTaskInstanceWriter,
      callback
    );
    importJob.setEntitiesToImport(userTasks);
    return importJob;
  }

  private FlowNodeInstanceDto mapEngineEntityToOptimizeEntity(final HistoricUserTaskInstanceDto engineEntity) {
    return FlowNodeInstanceDto.builder()
      .userTaskInstanceId(engineEntity.getId())
      .flowNodeId(engineEntity.getTaskDefinitionKey())
      .flowNodeInstanceId(engineEntity.getActivityInstanceId())
      .processInstanceId(engineEntity.getProcessInstanceId())
      .processDefinitionKey(engineEntity.getProcessDefinitionKey())
      .flowNodeType(FLOW_NODE_TYPE_USER_TASK)
      .engine(engineContext.getEngineAlias())
      .startDate(engineEntity.getStartTime())
      .dueDate(engineEntity.getDue())
      .deleteReason(engineEntity.getDeleteReason())
      // HistoricUserTaskInstanceDto does not have a bool canceled field. To avoid having to parse the deleteReason,
      // canceled defaults to false and writers do not overwrite existing canceled states.
      // The completedActivityInstanceWriter will overwrite the correct state.
      .canceled(false)
      .build();
  }

}
