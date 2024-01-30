/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store;

import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.PersistenceException;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface OperationStore {

  Map<String,String> getIndexNameForAliasAndIds(final String alias,final Collection<String> ids);

  List<OperationEntity> getOperationsFor(Long zeebeCommandKey, Long processInstanceKey, Long incidentKey, OperationType operationType);
  String add(BatchOperationEntity batchOperationEntity) throws PersistenceException;
  void update(OperationEntity operation, boolean refreshImmediately) throws PersistenceException;
  void updateWithScript(String index, String batchOperationId, String script, Map<String, Object> parameters);

  BatchRequest newBatchRequest();
}
