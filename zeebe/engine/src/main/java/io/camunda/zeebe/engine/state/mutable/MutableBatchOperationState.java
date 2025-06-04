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
import java.util.Set;

public interface MutableBatchOperationState extends BatchOperationState {

  /**
   * Stores the batch operation in the state.
   *
   * @param record the batch operation creation record to create
   */
  void create(final long batchOperationKey, final BatchOperationCreationRecord record);

  void start(final long batchOperationKey);

  void fail(final long batchOperationKey);

  void appendItemKeys(final long batchOperationKey, final Set<Long> itemKeys);

  void removeItemKeys(final long batchOperationKey, final Set<Long> itemKeys);

  void cancel(final long batchOperationKey);

  void suspend(final long batchOperationKey);

  void resume(final long batchOperationKey);

  void complete(final long batchOperationKey);

  /**
   * Marks a partition of a batch operation as finished. This is called when the partition has been
   * completed or failed.
   *
   * @param batchOperationKey the batch operation key
   * @param partitionId the partition ID
   */
  void finishPartition(final long batchOperationKey, int partitionId);
}
