/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.immutable;

import io.camunda.zeebe.engine.common.state.batchoperation.PersistedBatchOperation;
import java.util.List;
import java.util.Optional;

public interface BatchOperationState {

  /**
   * Checks if a batch operation with the given key exists.
   *
   * @param batchOperationKey the key of the batch operation to check
   * @return true if the batch operation exists, false otherwise
   */
  boolean exists(long batchOperationKey);

  /**
   * Retrieves a batch operation by its key.
   *
   * @param batchOperationKey the key of the batch operation to retrieve
   * @return an Optional containing the batch operation if it exists, or an empty Optional if it
   *     does not
   */
  Optional<PersistedBatchOperation> get(final long batchOperationKey);

  /**
   * Returns the next pending batch operation, or an empty <code>Optional</code> if there are no
   * pending batch operations.
   *
   * @return the next pending batch operation
   */
  Optional<PersistedBatchOperation> getNextPendingBatchOperation();

  /**
   * Retrieves the next <code>batchSize</code> unprocessed itemKeys for a given batch operation.
   * This method is usually called by the BatchOperationExecuteProcessor to execute the next X
   * items.<br>
   * <br>
   * This method can return fewer than <code>batchSize</code> itemKeys. This does not mean that
   * there are no more itemKeys but depends on the internal structure of the batch operation state.
   * <br>
   * <br>
   * If the returned list is empty, it means that there are no more itemKeys to process for the
   * given batch operation.
   *
   * @param batchOperationKey the key of the batch operation
   * @param batchSize the maximum number of itemKeys to retrieve
   * @return a list of itemKeys for the batch operations, up to the specified batch size
   */
  List<Long> getNextItemKeys(long batchOperationKey, int batchSize);
}
