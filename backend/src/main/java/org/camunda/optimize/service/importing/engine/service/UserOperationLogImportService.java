/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.HistoricUserOperationLogDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.UserOperationLogEntryDto;
import org.camunda.optimize.dto.optimize.importing.UserOperationType;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.UserOperationLogElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;
import org.camunda.optimize.service.importing.engine.handler.RunningProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.importing.UserOperationType.NOT_SUSPENSION_RELATED_OPERATION;
import static org.camunda.optimize.dto.optimize.importing.UserOperationType.isSuspensionViaBatchOperation;

@Slf4j
public class UserOperationLogImportService implements ImportService<HistoricUserOperationLogDto> {
  private final RunningProcessInstanceWriter runningProcessInstanceWriter;
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final RunningProcessInstanceImportIndexHandler runningProcessInstanceImportIndexHandler;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  private final ProcessInstanceResolverService processInstanceResolverService;

  public UserOperationLogImportService(final ConfigurationService configurationService,
                                       final RunningProcessInstanceWriter runningProcessInstanceWriter,
                                       final RunningProcessInstanceImportIndexHandler runningProcessInstanceImportIndexHandler,
                                       final ProcessDefinitionResolverService processDefinitionResolverService,
                                       final ProcessInstanceResolverService processInstanceResolverService) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
    this.runningProcessInstanceImportIndexHandler = runningProcessInstanceImportIndexHandler;
    this.processDefinitionResolverService = processDefinitionResolverService;
    this.processInstanceResolverService = processInstanceResolverService;
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
      .filter(this::filterUnknownDefinitionOperations)
      .distinct()
      .collect(Collectors.toList());
  }

  private UserOperationLogEntryDto mapEngineEntityToOptimizeEntity(final HistoricUserOperationLogDto engineEntity) {
    if (engineEntity.getProcessDefinitionKey() == null) {
      // To update instance data, we need to know the definition key (for the index name).
      // Depending on the user operation, the key may not be present in the userOpLog so we need to retrieve it
      // before importing.
      enrichOperationLogDtoWithDefinitionKey(engineEntity);
    }
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

  private void enrichOperationLogDtoWithDefinitionKey(final HistoricUserOperationLogDto engineEntity) {
    Optional<String> definitionKey = Optional.empty();
    if (engineEntity.getProcessDefinitionId() != null) {
      definitionKey = processDefinitionResolverService.getDefinition(
        engineEntity.getProcessDefinitionId(),
        runningProcessInstanceImportIndexHandler.getEngineContext()
      ).map(ProcessDefinitionOptimizeDto::getKey);
    } else if (engineEntity.getProcessInstanceId() != null) {
      definitionKey = processInstanceResolverService.getProcessInstanceDefinitionKey(
        engineEntity.getProcessInstanceId(),
        runningProcessInstanceImportIndexHandler.getEngineContext()
      );
    }
    definitionKey.ifPresent(engineEntity::setProcessDefinitionKey);
  }

  private boolean filterUnknownDefinitionOperations(final UserOperationLogEntryDto userOpLogEntryDto) {
    // If the operation is not a batch operation we need to know the definition key to update the specific instance
    // index. If the defKey is not present, the relevant definition or instance has not yet been imported, so we do
    // not import the suspension operation. Note that this might cause a race conditions in rare edge cases.
    return isSuspensionViaBatchOperation(userOpLogEntryDto.getOperationType()) ||
      userOpLogEntryDto.getProcessDefinitionKey() != null;
  }

}
