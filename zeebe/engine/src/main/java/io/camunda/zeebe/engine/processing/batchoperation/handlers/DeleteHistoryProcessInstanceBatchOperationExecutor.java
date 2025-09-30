/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.handlers;

import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;

public class DeleteHistoryProcessInstanceBatchOperationExecutor implements BatchOperationExecutor {

  @Override
  public void execute(final long itemKey, final PersistedBatchOperation batchOperation) {
    // TODO delete the historic data for a the given item key
    System.out.println(itemKey);
  }
}
