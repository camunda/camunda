/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.CompletedUserTasksElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.usertask.CompletedUserTaskInstanceWriter;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class CompletedUserTaskInstanceImportService implements ImportService<HistoricUserTaskInstanceDto> {
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final CompletedUserTaskInstanceWriter completedProcessInstanceWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  private final ConfigurationService configurationService;

  public CompletedUserTaskInstanceImportService(final ConfigurationService configurationService,
                                                final CompletedUserTaskInstanceWriter completedProcessInstanceWriter,
                                                final EngineContext engineContext,
                                                final ProcessDefinitionResolverService processDefinitionResolverService) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.engineContext = engineContext;
    this.completedProcessInstanceWriter = completedProcessInstanceWriter;
    this.processDefinitionResolverService = processDefinitionResolverService;
    this.configurationService = configurationService;
  }

  @Override
  public void executeImport(final List<HistoricUserTaskInstanceDto> pageOfEngineEntities,
                            Runnable importCompleteCallback) {
    log.trace("Importing completed user task entities from engine...");

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
                                                                                   final Runnable callback) {
    final CompletedUserTasksElasticsearchImportJob importJob = new CompletedUserTasksElasticsearchImportJob(
      completedProcessInstanceWriter,
      configurationService,
      callback
    );
    importJob.setEntitiesToImport(userTasks);
    return importJob;
  }

  private FlowNodeInstanceDto mapEngineEntityToOptimizeEntity(final HistoricUserTaskInstanceDto engineEntity) {
    return new FlowNodeInstanceDto(
      engineEntity.getProcessDefinitionKey(),
      engineContext.getEngineAlias(),
      engineEntity.getProcessInstanceId(),
      engineEntity.getTaskDefinitionKey(),
      engineEntity.getActivityInstanceId(),
      engineEntity.getId()
    )
      .setTotalDurationInMs(engineEntity.getDuration())
      .setStartDate(engineEntity.getStartTime())
      .setEndDate(engineEntity.getEndTime())
      .setDueDate(engineEntity.getDue())
      .setDeleteReason(engineEntity.getDeleteReason())
      // HistoricUserTaskInstanceDto does not have a bool canceled field. To avoid having to parse the deleteReason,
      // canceled defaults to false and writers do not overwrite existing canceled states.
      // The completedActivityInstanceWriter will overwrite the correct state.
      .setCanceled(false);
  }

}
