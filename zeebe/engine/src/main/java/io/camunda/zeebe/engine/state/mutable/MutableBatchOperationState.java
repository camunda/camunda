/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationSubbatchRecord;

public interface MutableBatchOperationState extends BatchOperationState {

  /**
   * Stores the item keys in the state.
   *
   * @param record the batch operation creation record to create
   */
  void create(final long batchKey, final BatchOperationCreationRecord record);

  void removeFromPending(final long batchKey, final BatchOperationExecutionRecord record);

  void appendKeys(final long batchKey, final BatchOperationSubbatchRecord record);

  void removeKeys(final long batchKey, final BatchOperationExecutionRecord record);

  void pause(final long batchKey, final BatchOperationExecutionRecord record);

  void resume(final long batchKey, final BatchOperationExecutionRecord record);

  void cancel(final long batchKey, final BatchOperationExecutionRecord record);

  void complete(final long batchKey, final BatchOperationExecutionRecord record);
}
