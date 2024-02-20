/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.writer;

import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import java.util.List;

public interface BatchOperationWriter {
  List<OperationEntity> lockBatch() throws PersistenceException;

  void updateOperation(OperationEntity operation) throws PersistenceException;

  BatchOperationEntity scheduleBatchOperation(CreateBatchOperationRequestDto batchOperationRequest);

  BatchOperationEntity scheduleSingleOperation(
      long processInstanceKey, CreateOperationRequestDto operationRequest);

  BatchOperationEntity scheduleModifyProcessInstance(ModifyProcessInstanceRequestDto modifyRequest);

  BatchOperationEntity scheduleDeleteDecisionDefinition(
      DecisionDefinitionEntity decisionDefinitionEntity);

  BatchOperationEntity scheduleDeleteProcessDefinition(ProcessEntity processEntity);
}
