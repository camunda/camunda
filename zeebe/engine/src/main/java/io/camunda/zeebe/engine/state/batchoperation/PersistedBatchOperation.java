/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationError;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceMigrationPlan;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceModificationPlan;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.BatchOperationProcessInstanceMigrationPlanValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.BatchOperationProcessInstanceModificationPlanValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.Comparator;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** The persisted record for a batch operation in the rocksDb state. */
public class PersistedBatchOperation extends UnpackedObject implements DbValue {

  /** The key of the batch operation. This is a unique identifier for the batch operation. */
  private final LongProperty keyProp = new LongProperty("key");

  /**
   * The type of the batch operation. This indicates what kind of operation is being performed in
   * the executor.
   */
  private final EnumProperty<BatchOperationType> batchOperationTypeProp =
      new EnumProperty<>("batchOperationType", BatchOperationType.class);

  /** The status of the batch operation. */
  private final EnumProperty<BatchOperationStatus> statusProp =
      new EnumProperty<>("status", BatchOperationStatus.class);

  /**
   * The entity filter for the batch operation. This is a binary property that contains the
   * serialized form of the filter. It needs to be deserialized before it can be used.
   */
  private final BinaryProperty entityFilterProp = new BinaryProperty("entityFilter");

  /**
   * An optional migration plan for the batch operation. Usually only filled for the batch operation
   * type <code>MIGRATE_PROCESS_INSTANCE</code>.
   */
  private final ObjectProperty<BatchOperationProcessInstanceMigrationPlan> migrationPlanProp =
      new ObjectProperty<>("migrationPlan", new BatchOperationProcessInstanceMigrationPlan());

  /**
   * An optional modification plan for the batch operation. Usually only filled for the batch
   * operation type <code>MODIFY_PROCESS_INSTANCE</code>.
   */
  private final ObjectProperty<BatchOperationProcessInstanceModificationPlan> modificationPlanProp =
      new ObjectProperty<>("modificationPlan", new BatchOperationProcessInstanceModificationPlan());

  /**
   * A flag indicating whether the batch operation has been initialized. This flag should be true
   * when the batch operation has been set to the status STARTED at least once. This is used on
   * resumption to mark a batch operation as pending or not pending.
   */
  private final BooleanProperty initializedProp = new BooleanProperty("initialized", false);

  /** The number of total items that are part of the batch operation. */
  private final IntegerProperty numTotalItemsProp = new IntegerProperty("numTotalItems", 0);

  /**
   * The number of items that have been executed so far in the batch operation. This is used for a
   * heartbeat logging in the BatchOperationExecuteProcessor.
   */
  private final IntegerProperty numExecutedItemsProp = new IntegerProperty("numExecutedItems", 0);

  /**
   * The keys of the chunks that are part of the batch operation. A chunk key is a local id
   * (starting from 0) for the chunks related to the batch operation. The combination of
   * batchOperationKey and chunkKey is unique.
   */
  private final ArrayProperty<LongValue> chunkKeysProp =
      new ArrayProperty<>("chunkKeys", LongValue::new);

  /**
   * The authentication claims for the batch operation. This is used to track the original
   * authentication claims of the user which has created the batch operation. This authentication is
   * used for querying the relevant itemKeys.
   */
  private final DocumentProperty authenticationProp = new DocumentProperty("authentication");

  private final StringProperty initializationSearchCursorProp =
      new StringProperty("initializationSearchCursor", "");

  private final IntegerProperty initializationSearchQueryPageSizeProp =
      new IntegerProperty("initializationSearchQueryPageSize", -1);

  /**
   * The partition ids that are part of the batch operation. This is used to track which partitions
   * existed when the batch operation was created and on which partitions the batch operation runs.
   */
  private final ArrayProperty<IntegerValue> partitionsProp =
      new ArrayProperty<>("partitions", IntegerValue::new);

  /**
   * The partition ids that have been finished (completed or failed). This is used to track the
   * distributed progress sof the whole batch operations.
   */
  private final ArrayProperty<IntegerValue> finishedPartitionsProp =
      new ArrayProperty<>("finishedPartitions", IntegerValue::new);

  private final ArrayProperty<BatchOperationError> errorsProp =
      new ArrayProperty<>("errors", BatchOperationError::new);

  public PersistedBatchOperation() {
    super(16);
    declareProperty(keyProp)
        .declareProperty(batchOperationTypeProp)
        .declareProperty(statusProp)
        .declareProperty(entityFilterProp)
        .declareProperty(migrationPlanProp)
        .declareProperty(modificationPlanProp)
        .declareProperty(chunkKeysProp)
        .declareProperty(initializedProp)
        .declareProperty(initializationSearchCursorProp)
        .declareProperty(initializationSearchQueryPageSizeProp)
        .declareProperty(authenticationProp)
        .declareProperty(partitionsProp)
        .declareProperty(finishedPartitionsProp)
        .declareProperty(numTotalItemsProp)
        .declareProperty(numExecutedItemsProp)
        .declareProperty(errorsProp);
  }

  public PersistedBatchOperation wrap(final BatchOperationCreationRecord record) {
    setKey(record.getBatchOperationKey());
    setBatchOperationType(record.getBatchOperationType());
    setEntityFilter(record.getEntityFilterBuffer());
    setMigrationPlan(record.getMigrationPlan());
    setModificationPlan(record.getModificationPlan());
    setAuthentication(record.getAuthenticationBuffer());
    setPartitions(record.getPartitionIds());
    return this;
  }

  /** Marks this batch operation as initialized. */
  public void markAsInitialized() {
    initializedProp.setValue(true);
  }

  /**
   * Returns true if the batch operation is initialized and ready for execution. A batch operation
   * is considered initialized if it has been started once.
   *
   * @return true if the batch operation is initialized, false otherwise
   */
  public boolean isInitialized() {
    return initializedProp.getValue();
  }

  public boolean canCancel() {
    return getStatus() == BatchOperationStatus.CREATED
        || getStatus() == BatchOperationStatus.INITIALIZED
        || getStatus() == BatchOperationStatus.SUSPENDED;
  }

  /**
   * Checks if the batch operation can be suspended. A batch operation can be suspended if it is
   * actively running.
   *
   * @return true if the batch operation can be suspended, false otherwise
   */
  public boolean canSuspend() {
    return getStatus() == BatchOperationStatus.CREATED
        || getStatus() == BatchOperationStatus.INITIALIZED;
  }

  /**
   * Checks if the batch operation can be resumed. A batch operation can be resumed if it is
   * currently suspended.
   *
   * @return true if the batch operation can be resumed, false otherwise
   */
  public boolean canResume() {
    return isSuspended();
  }

  /**
   * Checks if the batch operation is currently suspended.
   *
   * @return true if the batch operation is currently suspended, false otherwise
   */
  public boolean isSuspended() {
    return getStatus() == BatchOperationStatus.SUSPENDED;
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public PersistedBatchOperation setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }

  public BatchOperationType getBatchOperationType() {
    return batchOperationTypeProp.getValue();
  }

  public PersistedBatchOperation setBatchOperationType(
      final BatchOperationType batchOperationType) {
    batchOperationTypeProp.setValue(batchOperationType);
    return this;
  }

  public BatchOperationStatus getStatus() {
    return statusProp.getValue();
  }

  public PersistedBatchOperation setStatus(final BatchOperationStatus status) {
    statusProp.setValue(status);
    return this;
  }

  public BatchOperationProcessInstanceModificationPlan getModificationPlan() {
    return modificationPlanProp.getValue();
  }

  public PersistedBatchOperation setModificationPlan(
      final BatchOperationProcessInstanceModificationPlanValue modificationPlan) {
    modificationPlanProp.getValue().wrap(modificationPlan);
    return this;
  }

  public String getEntityFilter() {
    return MsgPackConverter.convertToJson(entityFilterProp.getValue());
  }

  public PersistedBatchOperation setEntityFilter(final DirectBuffer filter) {
    entityFilterProp.setValue(new UnsafeBuffer(filter));
    return this;
  }

  public CamundaAuthentication getAuthentication() {
    if (authenticationProp.getValue() != null) {
      return MsgPackConverter.convertToObject(
          authenticationProp.getValue(), CamundaAuthentication.class);
    } else {
      return CamundaAuthentication.none();
    }
  }

  public PersistedBatchOperation setAuthentication(final DirectBuffer authentication) {
    authenticationProp.setValue(authentication);
    return this;
  }

  public String getInitializationSearchCursor() {
    return bufferAsString(initializationSearchCursorProp.getValue());
  }

  public PersistedBatchOperation setInitializationSearchCursor(final String cursor) {
    initializationSearchCursorProp.setValue(cursor);
    return this;
  }

  public int getInitializationSearchQueryPageSize() {
    return initializationSearchQueryPageSizeProp.getValue();
  }

  public PersistedBatchOperation setInitializationSearchQueryPageSize(final int pageSize) {
    initializationSearchQueryPageSizeProp.setValue(pageSize);
    return this;
  }

  public int getInitializationSearchQueryPageSize(final int defaultValue) {
    if (initializationSearchQueryPageSizeProp.getValue() == -1) {
      return defaultValue;
    }

    return initializationSearchQueryPageSizeProp.getValue();
  }

  public <T> T getEntityFilter(final Class<T> clazz) {
    return MsgPackConverter.convertToObject(entityFilterProp.getValue(), clazz);
  }

  public BatchOperationProcessInstanceMigrationPlan getMigrationPlan() {
    return migrationPlanProp.getValue();
  }

  public PersistedBatchOperation setMigrationPlan(
      final BatchOperationProcessInstanceMigrationPlanValue migrationPlan) {
    migrationPlanProp.getValue().wrap(migrationPlan);
    return this;
  }

  public List<Integer> getPartitions() {
    return partitionsProp.stream().map(IntegerValue::getValue).toList();
  }

  public PersistedBatchOperation setPartitions(final List<Integer> partitions) {
    partitionsProp.reset();
    for (final var partition : partitions) {
      partitionsProp.add().setValue(partition);
    }
    return this;
  }

  public PersistedBatchOperation addFinishedPartition(final int partitionId) {
    finishedPartitionsProp.add().setValue(partitionId);
    return this;
  }

  public List<Integer> getFinishedPartitions() {
    return finishedPartitionsProp.stream().map(IntegerValue::getValue).toList();
  }

  public PersistedBatchOperation addError(final BatchOperationError error) {
    errorsProp.add().wrap(error);
    return this;
  }

  public List<BatchOperationError> getErrors() {
    return errorsProp.stream().toList();
  }

  public int getNumTotalItems() {
    return numTotalItemsProp.getValue();
  }

  public PersistedBatchOperation setNumTotalItems(final int numTotalItems) {
    numTotalItemsProp.setValue(numTotalItems);
    return this;
  }

  public int getNumExecutedItems() {
    return numExecutedItemsProp.getValue();
  }

  public PersistedBatchOperation setNumExecutedItems(final int numExecutedItems) {
    numExecutedItemsProp.setValue(numExecutedItems);
    return this;
  }

  /**
   * Returns the next chunk key for this batch operation. If there are no chunks yet, <code>0</code>
   * will be returned.
   *
   * @return the next chunk key
   */
  public long nextChunkKey() {
    return getMaxChunkKey() + 1;
  }

  /**
   * Adds a chunk key to the batch operation. Usually that key has been returned from <code>
   * nextChunkKey()</code> before.
   *
   * @param chunkKey the chunk key to add
   * @return this instance for method chaining
   */
  public PersistedBatchOperation addChunkKey(final Long chunkKey) {
    chunkKeysProp.add().setValue(chunkKey);
    return this;
  }

  /**
   * Removes a chunk key from the batch operation. If the chunk key does not exist, nothing happens.
   *
   * @param chunkKey the chunk key to remove
   * @return this instance for method chaining
   */
  public PersistedBatchOperation removeChunkKey(final Long chunkKey) {
    final var newKeys =
        chunkKeysProp.stream().map(LongValue::getValue).filter(k -> !k.equals(chunkKey)).toList();

    chunkKeysProp.reset();

    for (final var key : newKeys) {
      chunkKeysProp.add().setValue(key);
    }

    return this;
  }

  /**
   * Returns the smallest chunk key for this batch operation. If there are no chunks, <code>-1
   * </code> will be returned.
   *
   * @return the minimum chunk key or <code>-1</code> if there are no chunks
   */
  public long getMinChunkKey() {
    return chunkKeysProp.stream()
        .min(Comparator.comparing(LongValue::getValue))
        .map(LongValue::getValue)
        .orElse(-1L);
  }

  public boolean hasChunks() {
    return getMinChunkKey() != -1L;
  }

  /**
   * Returns the latest chunk key for this batch operation. If there are no chunks, <code>-1</code>
   * will be returned.
   *
   * @return the maximum chunk key or <code>-1</code> if there are no chunks
   */
  public long getMaxChunkKey() {
    return chunkKeysProp.stream()
        .max(Comparator.comparing(LongValue::getValue))
        .map(LongValue::getValue)
        .orElse(-1L);
  }

  public enum BatchOperationStatus {
    CREATED,
    INITIALIZED,
    SUSPENDED,
    CANCELED,
    FAILED
  }
}
