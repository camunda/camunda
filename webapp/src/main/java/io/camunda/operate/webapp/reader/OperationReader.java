/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import java.util.List;
import java.util.Map;

public interface OperationReader {
  List<OperationEntity> acquireOperations(int batchSize);

  Map<Long, List<OperationEntity>> getOperationsPerProcessInstanceKey(
      List<Long> processInstanceKeys);

  Map<Long, List<OperationEntity>> getOperationsPerIncidentKey(String processInstanceId);

  Map<String, List<OperationEntity>> getUpdateOperationsPerVariableName(
      Long processInstanceKey, Long scopeKey);

  List<OperationEntity> getOperationsByProcessInstanceKey(Long processInstanceKey);

  // this query will be extended
  List<BatchOperationEntity> getBatchOperations(int pageSize);

  List<OperationDto> getOperationsByBatchOperationId(String batchOperationId);

  List<OperationDto> getOperations(
      OperationType operationType, String processInstanceId, String scopeId, String variableName);
}
