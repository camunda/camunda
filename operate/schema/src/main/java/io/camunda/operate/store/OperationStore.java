/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface OperationStore {

  Map<String, String> getIndexNameForAliasAndIds(final String alias, final Collection<String> ids);

  List<OperationEntity> getOperationsFor(
      Long zeebeCommandKey, Long processInstanceKey, Long incidentKey, OperationType operationType);

  String add(BatchOperationEntity batchOperationEntity) throws PersistenceException;

  void update(OperationEntity operation, boolean refreshImmediately) throws PersistenceException;

  void updateWithScript(
      String index, String batchOperationId, String script, Map<String, Object> parameters);

  BatchRequest newBatchRequest();
}
