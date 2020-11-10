/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
  private RunningProcessInstanceWriter runningProcessInstanceWriter;

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
    final Map<String, Map<String, String>> definitionSuspensionMap =
      filterAndMapDefinitionSuspensionUserOperationsLogsToMap(newOptimizeEntities);
    if (definitionSuspensionMap.containsKey(ProcessInstanceDto.Fields.processDefinitionKey)) {
      runningProcessInstanceWriter.importProcessInstancesForProcessDefinitionKeys(
        definitionSuspensionMap.get(ProcessInstanceDto.Fields.processDefinitionKey)
      );
    }
    if (definitionSuspensionMap.containsKey(ProcessInstanceDto.Fields.processDefinitionId)) {
      runningProcessInstanceWriter.importProcessInstancesForProcessDefinitionIds(
        definitionSuspensionMap.get(ProcessInstanceDto.Fields.processDefinitionId)
      );
    }
  }

  private List<ProcessInstanceDto> filterAndMapInstanceSuspensionByInstanceIdOperationsLogsToProcessInstanceDtos(
    final List<UserOperationLogEntryDto> userOperationLogEntryDtos) {
    return userOperationLogEntryDtos.stream()
      .filter(userOpLog -> isSuspensionByInstanceIdOperation(userOpLog.getOperationType()))
      .map(userOpLog -> ProcessInstanceDto.builder()
        .processInstanceId(userOpLog.getProcessInstanceId())
        .state(resolveNewStateFromOperationType(userOpLog.getOperationType()))
        .build()
      )
      .distinct()
      .collect(toList());
  }

  /**
   * Filters suspension operations on instances based on definition ID or definition key and creates a map
   * of suspensionIdentifier("ProcessDefinitionKey" or "ProcessDefinitionId") to a map of definitionIdentifier
   * (the key or the ID) to the latest new state of said definition.
   * For example: Suspending process definition (incl. process instances) with ID "someID" results in the entry:
   * ["ProcessDefinitionId", "someID", "Suspended"]
   *
   * @param userOperationLogEntryDtos A List of UserOperationLogEntryDtos to map.
   * @return A map of the new state of the instances belonging to the suspended/activated definitions.
   */
  private Map<String, Map<String, String>> filterAndMapDefinitionSuspensionUserOperationsLogsToMap(
    final List<UserOperationLogEntryDto> userOperationLogEntryDtos) {
    Map<String, Map<String, String>> definitionSuspensionOperationMap = new HashMap<>();
    for (UserOperationLogEntryDto userOpLog : userOperationLogEntryDtos) {
      if (isSuspensionByDefinitionIdOperation(userOpLog.getOperationType())) {
        addDefinitionSuspensionOperationToMap(
          definitionSuspensionOperationMap,
          ProcessInstanceDto.Fields.processDefinitionId,
          userOpLog.getProcessDefinitionId(),
          resolveNewStateFromOperationType(userOpLog.getOperationType())
        );
      } else if (isSuspensionByDefinitionKeyOperation(userOpLog.getOperationType())) {
        addDefinitionSuspensionOperationToMap(
          definitionSuspensionOperationMap,
          ProcessInstanceDto.Fields.processDefinitionKey,
          userOpLog.getProcessDefinitionKey(),
          resolveNewStateFromOperationType(userOpLog.getOperationType())
        );
      }
    }
    return definitionSuspensionOperationMap;
  }

  private void addDefinitionSuspensionOperationToMap(
    Map<String, Map<String, String>> definitionSuspensionOperationMap,
    final String suspensionIdentifier,
    final String processDefinitionIdOrKey,
    final String newState) {
    definitionSuspensionOperationMap.putIfAbsent(suspensionIdentifier, new HashMap<>());
    definitionSuspensionOperationMap
      .get(suspensionIdentifier)
      .put(processDefinitionIdOrKey, newState);
  }

  public String resolveNewStateFromOperationType(final UserOperationType operationType) {
    return operationType.isSuspendOperation()
      ? SUSPENDED_STATE
      : ACTIVE_STATE;
  }
}
