/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.UserTaskInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.CompletedUserTasksElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.usertask.CompletedUserTaskInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class CompletedUserTaskInstanceImportService implements ImportService<HistoricUserTaskInstanceDto> {
  private static final Logger logger = LoggerFactory.getLogger(CompletedUserTaskInstanceImportService.class);

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final CompletedUserTaskInstanceWriter completedProcessInstanceWriter;

  public CompletedUserTaskInstanceImportService(final CompletedUserTaskInstanceWriter completedProcessInstanceWriter,
                                                final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                                final EngineContext engineContext) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.completedProcessInstanceWriter = completedProcessInstanceWriter;
  }

  @Override
  public void executeImport(final List<HistoricUserTaskInstanceDto> pageOfEngineEntities,
                            Runnable importCompleteCallback) {
    logger.trace("Importing completed user task entities from engine...");

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
    final CompletedUserTasksElasticsearchImportJob importJob = new CompletedUserTasksElasticsearchImportJob(
      completedProcessInstanceWriter,
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
      engineEntity.getEndTime(),
      engineEntity.getDue(),
      engineEntity.getDeleteReason(),
      engineEntity.getDuration()
    );
  }

}
