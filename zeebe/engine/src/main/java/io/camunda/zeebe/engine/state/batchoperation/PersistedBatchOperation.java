/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.NestedRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationError;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationJobUpdatePlan;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceMigrationPlan;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceModificationPlan;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.BatchOperationJobUpdatePlanValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.BatchOperationProcessInstanceMigrationPlanValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.BatchOperationProcessInstanceModificationPlanValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.util.ProjectionOf;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.LongArrayList;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The persisted record for a batch operation in the rocksDb state. */
@MsgpackPojo
@NullMarked
public final class PersistedBatchOperation extends MsgpackBufferValue<PersistedBatchOperation>
    implements DbValue, ProjectionOf<BatchOperationCreationRecord, PersistedBatchOperation> {

  /** The key of the batch operation. This is a unique identifier for the batch operation. */
  final RequiredLong key = new RequiredLong();

  /**
   * The type of the batch operation. This indicates what kind of operation is being performed in
   * the executor.
   */
  @Nullable BatchOperationType batchOperationType;

  /** The storage ordinal the batch operation inherited from the active ordinal at creation. */
  int storageOrdinal = 0;

  /** The status of the batch operation. */
  @Nullable BatchOperationStatus status;

  /**
   * The entity filter for the batch operation. This is a binary property that contains the
   * serialized form of the filter. It needs to be deserialized before it can be used.
   */
  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer entityFilter;

  /**
   * An optional migration plan for the batch operation. Usually only filled for the batch operation
   * type <code>MIGRATE_PROCESS_INSTANCE</code>.
   */
  final BatchOperationProcessInstanceMigrationPlan migrationPlan =
      new BatchOperationProcessInstanceMigrationPlan();

  /**
   * An optional modification plan for the batch operation. Usually only filled for the batch
   * operation type <code>MODIFY_PROCESS_INSTANCE</code>.
   */
  final BatchOperationProcessInstanceModificationPlan modificationPlan =
      new BatchOperationProcessInstanceModificationPlan();

  /**
   * An optional job update plan for the batch operation. Usually only filled for the batch
   * operation type <code>UPDATE_JOB</code>.
   */
  final BatchOperationJobUpdatePlan jobUpdatePlan = new BatchOperationJobUpdatePlan();

  /**
   * The keys of the chunks that are part of the batch operation. A chunk key is a local id
   * (starting from 0) for the chunks related to the batch operation. The combination of
   * batchOperationKey and chunkKey is unique.
   */
  final LongArrayList chunkKeys = new LongArrayList();

  /**
   * A flag indicating whether the batch operation has been initialized. This flag should be true
   * when the batch operation has been set to the status STARTED at least once. This is used on
   * resumption to mark a batch operation as pending or not pending.
   */
  boolean initialized = false;

  DirectBuffer initializationSearchCursor = Decoding.EMPTY;

  int initializationSearchQueryPageSize = -1;

  /**
   * The authentication claims for the batch operation. This is used to track the original
   * authentication claims of the user which has created the batch operation. This authentication is
   * used for querying the relevant itemKeys.
   */
  @MsgpackProperty(document = true)
  DirectBuffer authentication = DocumentValue.EMPTY_DOCUMENT;

  /**
   * The partition ids that are part of the batch operation. This is used to track which partitions
   * existed when the batch operation was created and on which partitions the batch operation runs.
   */
  final IntArrayList partitions = new IntArrayList();

  /**
   * The partition ids that have been finished (completed or failed). This is used to track the
   * distributed progress sof the whole batch operations.
   */
  final IntArrayList finishedPartitions = new IntArrayList();

  /** The number of total items that are part of the batch operation. */
  int numTotalItems = 0;

  /**
   * The number of items that have been executed so far in the batch operation. This is used for a
   * heartbeat logging in the BatchOperationExecuteProcessor.
   */
  int numExecutedItems = 0;

  final List<BatchOperationError> errors = new ArrayList<>();

  final NestedRecord followUpCommand = new NestedRecord();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public PersistedBatchOperation wrap(final BatchOperationCreationRecord record) {
    setKey(record.getBatchOperationKey());
    setBatchOperationType(record.getBatchOperationType());
    setStorageOrdinal(record.getStorageOrdinal());
    setEntityFilter(record.getEntityFilterBuffer());
    setMigrationPlan(record.getMigrationPlan());
    setModificationPlan(record.getModificationPlan());
    setJobUpdatePlan(record.getJobUpdatePlan());
    setAuthentication(record.getAuthenticationBuffer());
    setPartitions(record.getPartitionIds());
    setFollowUpCommand(record.getFollowUpCommand());
    return this;
  }

  /** Marks this batch operation as initialized. */
  public void markAsInitialized() {
    initialized = true;
  }

  /**
   * Returns true if the batch operation is initialized and ready for execution. A batch operation
   * is considered initialized if it has been started once.
   *
   * @return true if the batch operation is initialized, false otherwise
   */
  public boolean isInitialized() {
    return initialized;
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
    return key.get();
  }

  public PersistedBatchOperation setKey(final long key) {
    this.key.set(key);
    return this;
  }

  public BatchOperationType getBatchOperationType() {
    return Decoding.required(batchOperationType, "batchOperationType");
  }

  public PersistedBatchOperation setBatchOperationType(
      final BatchOperationType batchOperationType) {
    this.batchOperationType = batchOperationType;
    return this;
  }

  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public PersistedBatchOperation setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  public BatchOperationStatus getStatus() {
    return Decoding.required(status, "status");
  }

  public PersistedBatchOperation setStatus(final BatchOperationStatus status) {
    this.status = status;
    return this;
  }

  public BatchOperationProcessInstanceModificationPlan getModificationPlan() {
    return modificationPlan;
  }

  public PersistedBatchOperation setModificationPlan(
      final BatchOperationProcessInstanceModificationPlanValue modificationPlan) {
    this.modificationPlan.wrap(modificationPlan);
    return this;
  }

  public String getEntityFilter() {
    return MsgPackConverter.convertToJson(Decoding.required(entityFilter, "entityFilter"));
  }

  public PersistedBatchOperation setEntityFilter(final DirectBuffer filter) {
    this.entityFilter = new UnsafeBuffer(filter);
    return this;
  }

  public CamundaAuthentication getAuthentication() {
    if (authentication != null) {
      return MsgPackConverter.convertToObject(authentication, CamundaAuthentication.class);
    } else {
      return CamundaAuthentication.none();
    }
  }

  public PersistedBatchOperation setAuthentication(final DirectBuffer authentication) {
    this.authentication = Decoding.document(authentication);
    return this;
  }

  public String getInitializationSearchCursor() {
    return bufferAsString(initializationSearchCursor);
  }

  public PersistedBatchOperation setInitializationSearchCursor(final String cursor) {
    this.initializationSearchCursor = BufferUtil.wrapString(cursor);
    return this;
  }

  public int getInitializationSearchQueryPageSize() {
    return initializationSearchQueryPageSize;
  }

  public PersistedBatchOperation setInitializationSearchQueryPageSize(final int pageSize) {
    this.initializationSearchQueryPageSize = pageSize;
    return this;
  }

  public int getInitializationSearchQueryPageSize(final int defaultValue) {
    if (initializationSearchQueryPageSize == -1) {
      return defaultValue;
    }

    return initializationSearchQueryPageSize;
  }

  public <T> T getEntityFilter(final Class<T> clazz) {
    return MsgPackConverter.convertToObject(Decoding.required(entityFilter, "entityFilter"), clazz);
  }

  public BatchOperationProcessInstanceMigrationPlan getMigrationPlan() {
    return migrationPlan;
  }

  public PersistedBatchOperation setMigrationPlan(
      final BatchOperationProcessInstanceMigrationPlanValue migrationPlan) {
    this.migrationPlan.wrap(migrationPlan);
    return this;
  }

  public BatchOperationJobUpdatePlan getJobUpdatePlan() {
    return jobUpdatePlan;
  }

  public PersistedBatchOperation setJobUpdatePlan(
      final BatchOperationJobUpdatePlanValue jobUpdatePlan) {
    this.jobUpdatePlan.wrap(jobUpdatePlan);
    return this;
  }

  public List<Integer> getPartitions() {
    return partitions.stream().toList();
  }

  public PersistedBatchOperation setPartitions(final List<Integer> partitions) {
    this.partitions.clear();
    for (final var partition : partitions) {
      this.partitions.addInt(partition);
    }
    return this;
  }

  public NestedRecord getFollowUpCommand() {
    return followUpCommand;
  }

  public PersistedBatchOperation setFollowUpCommand(final NestedRecord command) {
    followUpCommand.wrap(command);
    return this;
  }

  public boolean hasFollowUpCommand() {
    return !followUpCommand.getValueType().equals(ValueType.NULL_VAL);
  }

  public PersistedBatchOperation addFinishedPartition(final int partitionId) {
    finishedPartitions.addInt(partitionId);
    return this;
  }

  public List<Integer> getFinishedPartitions() {
    return finishedPartitions.stream().toList();
  }

  public PersistedBatchOperation addError(final BatchOperationError error) {
    errors.add(new BatchOperationError().wrap(error));
    return this;
  }

  public List<BatchOperationError> getErrors() {
    return errors.stream().toList();
  }

  public int getNumTotalItems() {
    return numTotalItems;
  }

  public PersistedBatchOperation setNumTotalItems(final int numTotalItems) {
    this.numTotalItems = numTotalItems;
    return this;
  }

  public int getNumExecutedItems() {
    return numExecutedItems;
  }

  public PersistedBatchOperation setNumExecutedItems(final int numExecutedItems) {
    this.numExecutedItems = numExecutedItems;
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
    chunkKeys.addLong(chunkKey);
    return this;
  }

  /**
   * Removes a chunk key from the batch operation. If the chunk key does not exist, nothing happens.
   *
   * @param chunkKey the chunk key to remove
   * @return this instance for method chaining
   */
  public PersistedBatchOperation removeChunkKey(final Long chunkKey) {
    final var newKeys = chunkKeys.stream().filter(k -> !k.equals(chunkKey)).toList();

    chunkKeys.clear();

    for (final var key : newKeys) {
      chunkKeys.addLong(key);
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
    return chunkKeys.stream().min(Comparator.naturalOrder()).orElse(-1L);
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
    return chunkKeys.stream().max(Comparator.naturalOrder()).orElse(-1L);
  }

  @Override
  protected MsgpackCodec<PersistedBatchOperation> codec() {
    return PersistedBatchOperationCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedBatchOperation> ops() {
    return PersistedBatchOperationOps.INSTANCE;
  }

  public enum BatchOperationStatus {
    CREATED,
    INITIALIZED,
    SUSPENDED,
    CANCELED,
    FAILED
  }
}
