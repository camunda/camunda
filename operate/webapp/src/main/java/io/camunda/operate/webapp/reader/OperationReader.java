/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
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

  List<OperationDto> getOperationsByBatchOperationId(String batchOperationId);

  List<OperationDto> getOperations(
      OperationType operationType, String processInstanceId, String scopeId, String variableName);
}
