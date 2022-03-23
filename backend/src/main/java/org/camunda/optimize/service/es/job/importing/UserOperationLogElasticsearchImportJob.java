/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.UserOperationLogEntryDto;
import org.camunda.optimize.dto.optimize.importing.UserOperationType;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.ACTIVE_STATE;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static org.camunda.optimize.dto.optimize.importing.UserOperationType.isSuspensionByDefinitionIdOperation;
import static org.camunda.optimize.dto.optimize.importing.UserOperationType.isSuspensionByDefinitionKeyOperation;
import static org.camunda.optimize.dto.optimize.importing.UserOperationType.isSuspensionByInstanceIdOperation;

public class UserOperationLogElasticsearchImportJob extends ElasticsearchImportJob<UserOperationLogEntryDto> {
  private final RunningProcessInstanceWriter runningProcessInstanceWriter;

  public UserOperationLogElasticsearchImportJob(final RunningProcessInstanceWriter runningProcessInstanceWriter,
                                                Runnable callback) {
    super(callback);
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
  }

  @Override
  protected void persistEntities(final List<UserOperationLogEntryDto> newOptimizeEntities) {
    runningProcessInstanceWriter.importProcessInstancesFromUserOperationLogs(
      filterAndMapInstanceSuspensionByInstanceIdOperationsLogsToProcessInstanceDtos(newOptimizeEntities)
    );
    runningProcessInstanceWriter.importProcessInstancesForProcessDefinitionKeys(
      filterAndMapDefinitionKeySuspensionUserOperationsLogsToMap(newOptimizeEntities)
    );
    runningProcessInstanceWriter.importProcessInstancesForProcessDefinitionIds(
      filterAndMapDefinitionIdSuspensionUserOperationsLogsToMap(newOptimizeEntities)
    );
  }

  private List<ProcessInstanceDto> filterAndMapInstanceSuspensionByInstanceIdOperationsLogsToProcessInstanceDtos(
    final List<UserOperationLogEntryDto> userOperationLogEntryDtos) {
    return userOperationLogEntryDtos.stream()
      .filter(userOpLog -> isSuspensionByInstanceIdOperation(userOpLog.getOperationType()))
      .map(userOpLog -> ProcessInstanceDto.builder()
        .processInstanceId(userOpLog.getProcessInstanceId())
        .processDefinitionKey(userOpLog.getProcessDefinitionKey())
        .state(resolveNewStateFromOperationType(userOpLog.getOperationType()))
        .build()
      )
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
          resolveNewStateFromOperationType(userOpLog.getOperationType())
        );
      }
    }
    return definitionSuspensionOperationMap;
  }

  private Map<String, Map<String, String>> filterAndMapDefinitionIdSuspensionUserOperationsLogsToMap(
    final List<UserOperationLogEntryDto> userOperationLogEntryDtos) {
    // definitionSuspensionOperationMap contains <definitionKey, Map<definitionId, newState>> because the key is needed
    // to find the appropriate instance index to update
    Map<String, Map<String, String>> definitionSuspensionOperationMap = new HashMap<>();
    for (UserOperationLogEntryDto userOpLog : userOperationLogEntryDtos) {
      if (isSuspensionByDefinitionIdOperation(userOpLog.getOperationType())) {
        definitionSuspensionOperationMap.putIfAbsent(userOpLog.getProcessDefinitionKey(), new HashMap<>());
        definitionSuspensionOperationMap
          .get(userOpLog.getProcessDefinitionKey())
          .put(userOpLog.getProcessDefinitionId(), resolveNewStateFromOperationType(userOpLog.getOperationType()));
      }
    }
    return definitionSuspensionOperationMap;
  }

  public String resolveNewStateFromOperationType(final UserOperationType operationType) {
    return operationType.isSuspendOperation()
      ? SUSPENDED_STATE
      : ACTIVE_STATE;
  }
}
