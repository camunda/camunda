/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceMigrationPlan;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceModificationPlan;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.BatchOperationProcessInstanceMigrationPlanValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.BatchOperationProcessInstanceModificationPlanValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.Comparator;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class PersistedBatchOperation extends UnpackedObject implements DbValue {

  private final LongProperty keyProp = new LongProperty("key");
  private final EnumProperty<BatchOperationType> batchOperationTypeProp =
      new EnumProperty<>("batchOperationType", BatchOperationType.class);
  private final EnumProperty<BatchOperationStatus> statusProp =
      new EnumProperty<>("status", BatchOperationStatus.class);
  private final BinaryProperty entityFilterProp = new BinaryProperty("entityFilter");
  private final ObjectProperty<BatchOperationProcessInstanceMigrationPlan> migrationPlanProp =
      new ObjectProperty<>("migrationPlan", new BatchOperationProcessInstanceMigrationPlan());
  private final ObjectProperty<BatchOperationProcessInstanceModificationPlan> modificationPlanProp =
      new ObjectProperty<>("modificationPlan", new BatchOperationProcessInstanceModificationPlan());
  private final ArrayProperty<LongValue> chunkKeysProp =
      new ArrayProperty<>("chunkKeys", LongValue::new);
  private final ArrayProperty<IntegerValue> partitionsProp =
      new ArrayProperty<>("partitions", IntegerValue::new);
  private final ArrayProperty<IntegerValue> completedPartitionsProp =
      new ArrayProperty<>("completedPartitions", IntegerValue::new);

  public PersistedBatchOperation() {
    super(9);
    declareProperty(keyProp)
        .declareProperty(batchOperationTypeProp)
        .declareProperty(statusProp)
        .declareProperty(entityFilterProp)
        .declareProperty(migrationPlanProp)
        .declareProperty(modificationPlanProp)
        .declareProperty(chunkKeysProp)
        .declareProperty(partitionsProp)
        .declareProperty(completedPartitionsProp);
  }

  public PersistedBatchOperation wrap(final BatchOperationCreationRecord record) {
    setKey(record.getBatchOperationKey());
    setBatchOperationType(record.getBatchOperationType());
    setEntityFilter(record.getEntityFilterBuffer());
    setMigrationPlan(record.getMigrationPlan());
    setModificationPlan(record.getModificationPlan());
    setPartitions(record.getPartitionIds());
    return this;
  }

  public boolean canCancel() {
    return getStatus() == BatchOperationStatus.CREATED
        || getStatus() == BatchOperationStatus.STARTED
        || getStatus() == BatchOperationStatus.PAUSED;
  }

  public boolean canPause() {
    return getStatus() == BatchOperationStatus.CREATED
        || getStatus() == BatchOperationStatus.STARTED;
  }

  public boolean canResume() {
    return isPaused();
  }

  public boolean isPaused() {
    return getStatus() == BatchOperationStatus.PAUSED;
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

  public PersistedBatchOperation addCompletedPartition(final int partitionId) {
    completedPartitionsProp.add().setValue(partitionId);
    return this;
  }

  public List<Integer> getCompletedPartitions() {
    return completedPartitionsProp.stream().map(IntegerValue::getValue).toList();
  }

  public long nextChunkKey() {
    return getMaxChunkKey() + 1;
  }

  public PersistedBatchOperation addChunkKey(final Long chunkKey) {
    chunkKeysProp.add().setValue(chunkKey);
    return this;
  }

  public PersistedBatchOperation removeChunkKey(final Long chunkKey) {
    final var newKeys =
        chunkKeysProp.stream().map(LongValue::getValue).filter(k -> !k.equals(chunkKey)).toList();

    chunkKeysProp.reset();

    for (final var key : newKeys) {
      chunkKeysProp.add().setValue(key);
    }

    return this;
  }

  public long getMinChunkKey() {
    return chunkKeysProp.stream()
        .min(Comparator.comparing(LongValue::getValue))
        .map(LongValue::getValue)
        .orElse(-1L);
  }

  public long getMaxChunkKey() {
    return chunkKeysProp.stream()
        .max(Comparator.comparing(LongValue::getValue))
        .map(LongValue::getValue)
        .orElse(-1L);
  }

  public enum BatchOperationStatus {
    CREATED,
    STARTED,
    PAUSED,
    CANCELED,
    FAILED
  }
}
