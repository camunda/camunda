/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import java.util.Optional;

public interface BatchOperationState {

  boolean exists(long batchKey);

  Optional<PersistedBatchOperation> get(final long batchKey);

  void foreachPendingBatchOperation(BatchOperationVisitor visitor);

  Optional<Long> getNextItemKey(long batchOperationKey);

  @FunctionalInterface
  interface BatchOperationVisitor {

    void visit(PersistedBatchOperation batchOperation);
  }
}
