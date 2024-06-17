/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.ACTIVE_STATE;
import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static io.camunda.optimize.dto.optimize.importing.UserOperationType.isSuspensionByDefinitionIdOperation;
import static io.camunda.optimize.dto.optimize.importing.UserOperationType.isSuspensionByDefinitionKeyOperation;
import static io.camunda.optimize.dto.optimize.importing.UserOperationType.isSuspensionByInstanceIdOperation;
import static java.util.stream.Collectors.toList;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.importing.UserOperationLogEntryDto;
import io.camunda.optimize.dto.optimize.importing.UserOperationType;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.RunningProcessInstanceWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserOperationLogDatabaseImportJob extends DatabaseImportJob<UserOperationLogEntryDto> {

  private final RunningProcessInstanceWriter runningProcessInstanceWriter;

  public UserOperationLogDatabaseImportJob(
      final RunningProcessInstanceWriter runningProcessInstanceWriter,
      final Runnable callback,
      final DatabaseClient databaseClient) {
    super(callback, databaseClient);
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
  }

  @Override
  protected void persistEntities(final List<UserOperationLogEntryDto> newOptimizeEntities) {
    runningProcessInstanceWriter.importProcessInstancesFromUserOperationLogs(
        filterAndMapInstanceSuspensionByInstanceIdOperationsLogsToProcessInstanceDtos(
            newOptimizeEntities));
    runningProcessInstanceWriter.importProcessInstancesForProcessDefinitionKeys(
        filterAndMapDefinitionKeySuspensionUserOperationsLogsToMap(newOptimizeEntities));
    runningProcessInstanceWriter.importProcessInstancesForProcessDefinitionIds(
        filterAndMapDefinitionIdSuspensionUserOperationsLogsToMap(newOptimizeEntities));
  }

  private List<ProcessInstanceDto>
      filterAndMapInstanceSuspensionByInstanceIdOperationsLogsToProcessInstanceDtos(
          final List<UserOperationLogEntryDto> userOperationLogEntryDtos) {
    return userOperationLogEntryDtos.stream()
        .filter(userOpLog -> isSuspensionByInstanceIdOperation(userOpLog.getOperationType()))
        .map(
            userOpLog ->
                ProcessInstanceDto.builder()
                    .processInstanceId(userOpLog.getProcessInstanceId())
                    .processDefinitionKey(userOpLog.getProcessDefinitionKey())
                    .state(resolveNewStateFromOperationType(userOpLog.getOperationType()))
                    .build())
        .distinct()
        .collect(toList());
  }

  private Map<String, String> filterAndMapDefinitionKeySuspensionUserOperationsLogsToMap(
      final List<UserOperationLogEntryDto> userOperationLogEntryDtos) {
    Map<String, String> definitionSuspensionOperationMap = new HashMap<>();
    for (UserOperationLogEntryDto userOpLog : userOperationLogEntryDtos) {
      if (isSuspensionByDefinitionKeyOperation(userOpLog.getOperationType())) {
        definitionSuspensionOperationMap.putIfAbsent(
            userOpLog.getProcessDefinitionKey(),
            resolveNewStateFromOperationType(userOpLog.getOperationType()));
      }
    }
    return definitionSuspensionOperationMap;
  }

  private Map<String, Map<String, String>>
      filterAndMapDefinitionIdSuspensionUserOperationsLogsToMap(
          final List<UserOperationLogEntryDto> userOperationLogEntryDtos) {
    // definitionSuspensionOperationMap contains <definitionKey, Map<definitionId, newState>>
    // because the key is needed
    // to find the appropriate instance index to update
    Map<String, Map<String, String>> definitionSuspensionOperationMap = new HashMap<>();
    for (UserOperationLogEntryDto userOpLog : userOperationLogEntryDtos) {
      if (isSuspensionByDefinitionIdOperation(userOpLog.getOperationType())) {
        definitionSuspensionOperationMap.putIfAbsent(
            userOpLog.getProcessDefinitionKey(), new HashMap<>());
        definitionSuspensionOperationMap
            .get(userOpLog.getProcessDefinitionKey())
            .put(
                userOpLog.getProcessDefinitionId(),
                resolveNewStateFromOperationType(userOpLog.getOperationType()));
      }
    }
    return definitionSuspensionOperationMap;
  }

  public String resolveNewStateFromOperationType(final UserOperationType operationType) {
    return operationType.isSuspendOperation() ? SUSPENDED_STATE : ACTIVE_STATE;
  }
}
