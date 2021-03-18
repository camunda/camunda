/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.UserTaskInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.RunningUserTaskElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.usertask.RunningUserTaskInstanceWriter;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class RunningUserTaskInstanceImportService implements ImportService<HistoricUserTaskInstanceDto> {
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final RunningUserTaskInstanceWriter runningUserTaskInstanceWriter;

  public RunningUserTaskInstanceImportService(final RunningUserTaskInstanceWriter runningUserTaskInstanceWriter,
                                              final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                              final EngineContext engineContext) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.runningUserTaskInstanceWriter = runningUserTaskInstanceWriter;
  }

  @Override
  public void executeImport(final List<HistoricUserTaskInstanceDto> pageOfEngineEntities,
                            Runnable importCompleteCallback) {
    log.trace("Importing running user task entities from engine...");

    final boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<UserTaskInstanceDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      final ElasticsearchImportJob<UserTaskInstanceDto> elasticsearchImportJob = createElasticsearchImportJob(
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

  private List<UserTaskInstanceDto> mapEngineEntitiesToOptimizeEntities(final List<HistoricUserTaskInstanceDto> engineEntities) {
    return engineEntities.stream()
      .filter(instance -> instance.getProcessInstanceId() != null)
      .map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<UserTaskInstanceDto> createElasticsearchImportJob(final List<UserTaskInstanceDto> userTasks,
                                                                                   Runnable callback) {
    final RunningUserTaskElasticsearchImportJob importJob = new RunningUserTaskElasticsearchImportJob(
      runningUserTaskInstanceWriter,
      callback
    );
    importJob.setEntitiesToImport(userTasks);
    return importJob;
  }

  private UserTaskInstanceDto mapEngineEntityToOptimizeEntity(final HistoricUserTaskInstanceDto engineEntity) {
    return new UserTaskInstanceDto(
      engineEntity.getId(),
      engineEntity.getProcessInstanceId(),
      engineEntity.getProcessDefinitionKey(),
      engineContext.getEngineAlias(),
      engineEntity.getTaskDefinitionKey(),
      engineEntity.getActivityInstanceId(),
      engineEntity.getStartTime(),
      null,
      engineEntity.getDue(),
      engineEntity.getDeleteReason(),
      null
    );
  }

}
