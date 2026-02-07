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

  /**
   * Updates the document with the given id in the given index using the provided script and
   * parameters.
   *
   * <p>Warning! If the document with the given id does not exist in the given index, this update
   * will fail exceptionally, resulting in a blocked exporter until the document is restored. For
   * some indices this exception will be swallowed. The list of these indices can be found in {@link
   * io.camunda.exporter.DefaultExporterResourceProvider#init}. See also <a
   * href="https://github.com/camunda/camunda/issues/44356">https://github.com/camunda/camunda/issues/44356</a>
   */
  void updateWithScript(
      String index, String batchOperationId, String script, Map<String, Object> parameters);

  BatchRequest newBatchRequest();
}
