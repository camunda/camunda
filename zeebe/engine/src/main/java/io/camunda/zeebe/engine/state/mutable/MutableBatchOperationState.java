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
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationError;
import java.util.Set;

public interface MutableBatchOperationState extends BatchOperationState {

  /**
   * Stores the batch operation in the state.
   *
   * <p><strong>Deprecation Reason:</strong>
   *
   * <p>This method is deprecated because the {@code BatchOperationIntent.CREATED} command could be
   * processed multiple times for the same batch operation key. This probably occurred because the
   * command was not acknowledged during {@link
   * io.camunda.zeebe.engine.processing.batchoperation.BatchOperationCreateProcessor#processDistributedCommand},
   * leading to duplicate processing. This would cause a new {@link
   * io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation} instance to be created
   * and upserted in the state, overriding the previous instance via {@link
   * io.camunda.zeebe.engine.state.batchoperation.DbBatchOperationState#deprecatedCreate}. This
   * resulted in all {@link io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation}
   * properties being reset.
   *
   * <p>The {@link
   * io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationExecutionScheduler}
   * retrieves pending batch operations from the state and attempts to create chunks for them. Due
   * to the property reset described above, the {@code BatchOperationChunkIntent.CREATED} command
   * would be generated multiple times for the same batch operation key, attempting to create the
   * same batch operation chunk repeatedly.
   *
   * <p>Since the {@code chunkKeyProp} had been reset and was empty, {@link
   * io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation#getMinChunkKey()} would
   * return {@code -1} each time. Consequently, each new chunk would receive the key {@code 0} in
   * {@link io.camunda.zeebe.engine.state.batchoperation.DbBatchOperationState}, resulting in a
   * {@code ZeebeDbInconsistentException} when attempting to insert multiple batch operation chunks
   * with the same key.
   *
   * @param record the batch operation creation record to create
   * @deprecated Use {@link #create(long, BatchOperationCreationRecord)} instead to prevent
   *     duplicate processing issues
   */
  @Deprecated()
  void deprecatedCreate(final long batchOperationKey, final BatchOperationCreationRecord record);

  /**
   * Stores the batch operation in the state.
   *
   * @param record the batch operation creation record to create
   */
  void create(final long batchOperationKey, final BatchOperationCreationRecord record);

  /**
   * Marks a batch operation as started.
   *
   * @param batchOperationKey the key of the batch operation to mark as started
   */
  void transitionToInitialized(final long batchOperationKey);

  /**
   * Sets the next step for the batch operation init phase.
   *
   * @param batchOperationKey the key of the batch operation to mark as started
   * @param searchResultCursor the cursor of the search client to use for the next step
   * @param searchQueryPageSize the page size to use for the next step
   */
  void continueInitialization(
      final long batchOperationKey, final String searchResultCursor, int searchQueryPageSize);

  /**
   * Marks a batch operation as failed. This will delete the batch operation from the state.
   *
   * @param batchOperationKey the key of the batch operation to mark as failed
   */
  void fail(final long batchOperationKey);

  /**
   * Adds the given itemKeys to the given batch operation. The itemKeys are added to the end of the
   * pending itemKeys.
   *
   * @param batchOperationKey the key of the batch operation to which the itemKeys should be added
   * @param itemKeys the set of itemKeys to add to the batch operation
   * @deprecated Use {@link #addChunk(long, long, Set)}
   */
  @Deprecated
  void appendItemKeys(final long batchOperationKey, final Set<Long> itemKeys);

  /**
   * Adds a chunk of itemKeys to the given batch operation. The itemKeys are added to the end of the
   * pending itemKeys.
   *
   * @param chunkKey the key of the chunk being added
   * @param batchOperationKey the key of the batch operation to which the itemKeys should be added
   * @param itemKeys the set of itemKeys to add to the batch operation
   */
  void addChunk(long chunkKey, long batchOperationKey, Set<Long> itemKeys);

  /**
   * Removes the given itemKeys from the given batch operation. The itemKeys are removed from the
   * pending itemKeys.
   *
   * @param batchOperationKey the key of the batch operation
   * @param itemKeys the set of itemKeys to remove from the batch operation
   */
  void removeItemKeys(final long batchOperationKey, final Set<Long> itemKeys);

  /**
   * Marks a batch operation as cancelled.
   *
   * @param batchOperationKey the key of the batch operation to mark as canceled
   */
  void cancel(final long batchOperationKey);

  /**
   * Marks a batch operation as suspended.
   *
   * @param batchOperationKey the key of the batch operation
   */
  void suspend(final long batchOperationKey);

  /**
   * Marks a batch operation as resumed.
   *
   * @param batchOperationKey the key of the batch operation
   */
  void resume(final long batchOperationKey);

  /**
   * Marks a batch operation as completed. This will delete the batch operation from the state.
   *
   * @param batchOperationKey the key of the batch operation
   */
  void complete(final long batchOperationKey);

  void failPartition(
      final long batchOperationKey, int sourcePartitionId, BatchOperationError error);

  /**
   * Marks a partition of a batch operation as finished. This is called when the partition has been
   * completed or failed.
   *
   * @param batchOperationKey the batch operation key
   * @param partitionId the partition ID to mark as finished
   */
  void finishPartition(final long batchOperationKey, int partitionId);
}
