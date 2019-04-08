/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.UserOperationLogEntryEngineDto;
import org.camunda.optimize.dto.optimize.importing.UserOperationLogEntryDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.UserOperationEntryElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.UserOperationsLogEntryWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class UserOperationLogImportService implements ImportService<UserOperationLogEntryEngineDto>{
  private static final Logger logger = LoggerFactory.getLogger(UserOperationLogImportService.class);

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final UserOperationsLogEntryWriter userOperationsLogEntryWriter;

  public UserOperationLogImportService(final UserOperationsLogEntryWriter userOperationsLogEntryWriter,
                                       final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                       final EngineContext engineContext) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.userOperationsLogEntryWriter = userOperationsLogEntryWriter;
  }

  @Override
  public void executeImport(final List<UserOperationLogEntryEngineDto> pageOfEngineEntities, Runnable callback) {
    logger.trace("Importing user operation log entries from engine...");

    final boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<UserOperationLogEntryDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(
        pageOfEngineEntities);
      final ElasticsearchImportJob<UserOperationLogEntryDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, callback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  private void addElasticsearchImportJobToQueue(final ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<UserOperationLogEntryDto> mapEngineEntitiesToOptimizeEntities(
    final List<UserOperationLogEntryEngineDto> engineEntities) {
    return engineEntities.stream()
      .filter(operationLogEntry -> operationLogEntry.getProcessInstanceId() != null)
      .map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<UserOperationLogEntryDto> createElasticsearchImportJob(final
                                                                                        List<UserOperationLogEntryDto> userTasks,
                                                                                        Runnable callback) {
    final UserOperationEntryElasticsearchImportJob importJob = new UserOperationEntryElasticsearchImportJob(
      userOperationsLogEntryWriter,
      callback
    );
    importJob.setEntitiesToImport(userTasks);
    return importJob;
  }

  private UserOperationLogEntryDto mapEngineEntityToOptimizeEntity(final UserOperationLogEntryEngineDto engineEntity) {
    final UserOperationLogEntryDto userTaskInstanceDto = new UserOperationLogEntryDto(
      engineEntity.getId(),
      engineEntity.getProcessDefinitionId(),
      engineEntity.getProcessDefinitionKey(),
      engineEntity.getProcessInstanceId(),
      engineEntity.getTaskId(),
      engineEntity.getUserId(),
      engineEntity.getTimestamp(),
      engineEntity.getOperationType(),
      engineEntity.getProperty(),
      engineEntity.getOrgValue(),
      engineEntity.getNewValue(),
      engineContext.getEngineAlias()
    );
    return userTaskInstanceDto;
  }

}
