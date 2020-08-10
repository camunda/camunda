/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.HistoricUserOperationLogDto;
import org.camunda.optimize.dto.optimize.importing.UserOperationLogEntryDto;
import org.camunda.optimize.dto.optimize.importing.UserOperationType;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.UserOperationLogElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;
import org.camunda.optimize.service.importing.engine.handler.RunningProcessInstanceImportIndexHandler;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.importing.UserOperationType.NOT_SUSPENSION_RELATED_OPERATION;
import static org.camunda.optimize.dto.optimize.importing.UserOperationType.isSuspensionViaBatchOperation;

@Slf4j
public class UserOperationLogImportService implements ImportService<HistoricUserOperationLogDto> {
  private final RunningProcessInstanceWriter runningProcessInstanceWriter;
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final RunningProcessInstanceImportIndexHandler runningProcessInstanceImportIndexHandler;

  public UserOperationLogImportService(final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                       final RunningProcessInstanceWriter runningProcessInstanceWriter,
                                       final RunningProcessInstanceImportIndexHandler runningProcessInstanceImportIndexHandler) {
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.runningProcessInstanceImportIndexHandler = runningProcessInstanceImportIndexHandler;
  }

  /**
   * Triggers a reimport of relevant process instances for suspension of instances and/or suspension of definitions.
   * Batch suspension operations are handled by restarting the running process instance import from scratch as it is
   * not possible to determine the affected instances otherwise.
   */
  @Override
  public void executeImport(final List<HistoricUserOperationLogDto> pageOfEngineEntities,
                            Runnable importCompleteCallback) {
    log.trace("Importing suspension related user operation logs from engine...");

    final boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<UserOperationLogEntryDto> newOptimizeEntities =
        filterSuspensionOperationsAndMapToOptimizeEntities(pageOfEngineEntities);
      if (containsBatchOperation(newOptimizeEntities)) {
        // since we do not know which instances were suspended, restart entire running process instance import
        log.info("Batch suspension operation occurred. Restarting running process instance import.");
        runningProcessInstanceImportIndexHandler.resetImportIndex();
        importCompleteCallback.run();
      } else {
        final ElasticsearchImportJob<UserOperationLogEntryDto> elasticsearchImportJob =
          createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback);
        addElasticsearchImportJobToQueue(elasticsearchImportJob);
      }
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
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

  private List<UserOperationLogEntryDto> filterSuspensionOperationsAndMapToOptimizeEntities(
    final List<HistoricUserOperationLogDto> engineEntities) {
    return engineEntities.stream()
      .filter(historicUserOpLog ->
                !UserOperationType.fromHistoricUserOperationLog(historicUserOpLog)
                  .equals(NOT_SUSPENSION_RELATED_OPERATION))
      .map(this::mapEngineEntityToOptimizeEntity)
      .distinct()
      .collect(Collectors.toList());
  }

  private UserOperationLogEntryDto mapEngineEntityToOptimizeEntity(final HistoricUserOperationLogDto engineEntity) {
    return UserOperationLogEntryDto.builder()
      .id(engineEntity.getId())
      .processInstanceId(engineEntity.getProcessInstanceId())
      .processDefinitionId(engineEntity.getProcessDefinitionId())
      .processDefinitionKey(engineEntity.getProcessDefinitionKey())
      .operationType(UserOperationType.fromHistoricUserOperationLog(engineEntity))
      .build();
  }

  private boolean containsBatchOperation(List<UserOperationLogEntryDto> userOperationLogEntryDtos) {
    return userOperationLogEntryDtos.stream()
      .anyMatch(userOpLog -> isSuspensionViaBatchOperation(userOpLog.getOperationType()));
  }

}
