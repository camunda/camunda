/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.HistoricUserOperationLogDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.UserOperationLogEntryDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.UserOperationLogElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class UserOperationLogImportService implements ImportService<HistoricUserOperationLogDto> {
  private final RunningProcessInstanceWriter runningProcessInstanceWriter;
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  public UserOperationLogImportService(final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                       final RunningProcessInstanceWriter runningProcessInstanceWriter) {
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
  }

  @Override
  public void executeImport(final List<HistoricUserOperationLogDto> pageOfEngineEntities,
                            Runnable importCompleteCallback) {
    log.trace("Importing user operation logs from engine...");

    final boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<UserOperationLogEntryDto> newOptimizeEntities =
        mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      final ElasticsearchImportJob<UserOperationLogEntryDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  private ElasticsearchImportJob<UserOperationLogEntryDto> createElasticsearchImportJob(
    final List<UserOperationLogEntryDto> userOperationLogs,
    Runnable callback) {
    final UserOperationLogElasticsearchImportJob importJob = new UserOperationLogElasticsearchImportJob(
      runningProcessInstanceWriter,
      callback
    );
    importJob.setEntitiesToImport(userOperationLogs);
    return importJob;
  }

  private void addElasticsearchImportJobToQueue(final ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<UserOperationLogEntryDto> mapEngineEntitiesToOptimizeEntities(final List<HistoricUserOperationLogDto> engineEntities) {
    return engineEntities.stream()
      .filter(userOpLog -> userOpLog.getProcessInstanceId() != null)
      .filter(userOpLog -> userOpLog.getProperty().equalsIgnoreCase(ProcessInstanceDto.Fields.state))
      .map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private UserOperationLogEntryDto mapEngineEntityToOptimizeEntity(final HistoricUserOperationLogDto engineEntity) {
    return new UserOperationLogEntryDto(
      engineEntity.getId(),
      engineEntity.getProcessInstanceId(),
      engineEntity.getOperationType(),
      engineEntity.getProperty(),
      engineEntity.getNewValue(),
      engineEntity.getTimestamp()
    );
  }

}
