/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.kit;

import io.camunda.it.rdbms.exporter.RecordFixtures;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue.BatchOperationItemValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationItemValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Records of one partition with explicit keys, built from the exporter test fixtures. */
public final class KitRecords {

  private static final RecordFixtures FIXTURES = new RecordFixtures();

  /** Counter values unique per run, so repeated runs against one database never collide. */
  private static final AtomicLong COUNTER =
      new AtomicLong(System.currentTimeMillis() % 1_000_000 * 1_000);

  private KitRecords() {}

  /** A key as the engine generates it: partition in the top bits, then a per-partition counter. */
  public static long key(final int partitionId, final long counter) {
    return Protocol.encodePartitionId(partitionId, counter);
  }

  /**
   * The record as it is, with a new position: for records captured from a real engine, whose
   * positions a kit exporter may already have passed.
   */
  @SuppressWarnings("unchecked")
  public static Record<RecordValue> withNextPosition(final Record<?> record) {
    return ImmutableRecord.builder()
        .from((Record<RecordValue>) record)
        .withPosition(FIXTURES.nextPosition())
        .build();
  }

  /** A new key of the partition, unique per run. */
  public static long nextKey(final int partitionId) {
    return key(partitionId, COUNTER.incrementAndGet());
  }

  /** A new name with the given prefix, unique per run. */
  public static String nextName(final String prefix) {
    return prefix + COUNTER.incrementAndGet();
  }

  /**
   * A new key of the partition above every other key of it, i.e. directly before the keys of the
   * next partition in every key index; unique per run.
   */
  public static long nextTopKey(final int partitionId) {
    return key(partitionId, topCounter(COUNTER.incrementAndGet() % 9_000));
  }

  /** The highest counter values of a partition, for keys above every other key of it. */
  private static long topCounter(final long offset) {
    return (1L << Protocol.KEY_BITS) - 10_000 + offset;
  }

  public static Record<RecordValue> processStarted(final int p, final long processInstanceKey) {
    return processInstance(FIXTURES.getProcessInstanceStartedRecord(), p, processInstanceKey);
  }

  public static Record<RecordValue> processCompleted(final int p, final long processInstanceKey) {
    return processInstance(
        FIXTURES.getProcessInstanceCompletedRecord(processInstanceKey, -1L), p, processInstanceKey);
  }

  public static Record<RecordValue> elementActivating(
      final int p, final long elementKey, final long processInstanceKey) {
    return element(
        FIXTURES.getElementActivatingRecord(processInstanceKey), p, elementKey, processInstanceKey);
  }

  public static Record<RecordValue> elementCompleted(
      final int p, final long elementKey, final long processInstanceKey) {
    return element(
        FIXTURES.getElementCompletedRecord(elementKey), p, elementKey, processInstanceKey);
  }

  public static Record<RecordValue> batchOperationCreated(
      final int p, final long batchOperationKey) {
    return ImmutableRecord.builder()
        .from(FIXTURES.getBatchOperationCreatedRecord())
        .withKey(batchOperationKey)
        .withPartitionId(p)
        .build();
  }

  public static Record<RecordValue> batchOperationInitialized(
      final int p, final long batchOperationKey) {
    return ImmutableRecord.builder()
        .from(FIXTURES.getBatchOperationInitializedRecord(batchOperationKey))
        .withKey(batchOperationKey)
        .withPartitionId(p)
        .build();
  }

  /** A child process instance started under a root instance. */
  public static Record<RecordValue> childProcessStarted(
      final int p, final long processInstanceKey, final long rootProcessInstanceKey) {
    final Record<RecordValue> record = processStarted(p, processInstanceKey);
    return ImmutableRecord.builder()
        .from(record)
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ProcessInstanceRecordValue) record.getValue())
                .withRootProcessInstanceKey(rootProcessInstanceKey)
                .withParentProcessInstanceKey(rootProcessInstanceKey)
                .build())
        .build();
  }

  /** A variable created in a process instance of the given process definition. */
  public static Record<RecordValue> variableCreated(
      final int p,
      final long variableKey,
      final long processInstanceKey,
      final long processDefinitionKey,
      final String name) {
    final Record<RecordValue> record = FIXTURES.getVariableCreatedRecord();
    return ImmutableRecord.builder()
        .from(record)
        .withKey(variableKey)
        .withPartitionId(p)
        .withValue(
            ImmutableVariableRecordValue.builder()
                .from((VariableRecordValue) record.getValue())
                .withName(name)
                .withProcessDefinitionKey(processDefinitionKey)
                .withProcessInstanceKey(processInstanceKey)
                .withRootProcessInstanceKey(processInstanceKey)
                .withScopeKey(processInstanceKey)
                .build())
        .build();
  }

  /** A chunk of pending items of a batch operation, created on a partition (three items). */
  public static Record<RecordValue> batchOperationChunkCreated(
      final int p, final long batchOperationKey) {
    return ImmutableRecord.builder()
        .from(FIXTURES.getBatchOperationChunkRecord(batchOperationKey))
        .withPartitionId(p)
        .build();
  }

  /** A chunk of the batch operation with the given number of items (new keys of partition p). */
  public static Record<RecordValue> batchOperationChunkCreated(
      final int p, final long batchOperationKey, final int items) {
    final var record = FIXTURES.getBatchOperationChunkRecord(batchOperationKey);
    final List<BatchOperationItemValue> values = new ArrayList<>();
    for (int i = 0; i < items; i++) {
      final long item = nextKey(p);
      values.add(
          ImmutableBatchOperationItemValue.builder()
              .withItemKey(item)
              .withProcessInstanceKey(item)
              .build());
    }
    return ImmutableRecord.builder()
        .from(record)
        .withPartitionId(p)
        .withValue(
            ImmutableBatchOperationChunkRecordValue.builder()
                .from((ImmutableBatchOperationChunkRecordValue) record.getValue())
                .withItems(values)
                .build())
        .build();
  }

  /** A batch operation canceled on a partition (the cancel is distributed to all partitions). */
  public static Record<RecordValue> batchOperationCanceled(
      final int p, final long batchOperationKey) {
    return ImmutableRecord.builder()
        .from(FIXTURES.getBatchOperationCreatedRecord())
        .withKey(nextKey(p))
        .withPartitionId(p)
        .withValueType(ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT)
        .withIntent(BatchOperationIntent.CANCELED)
        .withValue(
            ImmutableBatchOperationLifecycleManagementRecordValue.builder()
                .withBatchOperationKey(batchOperationKey)
                .build())
        .build();
  }

  /** A process instance canceled as an item of a batch operation. */
  public static Record<RecordValue> canceledByBatchOperation(
      final int p, final long processInstanceKey, final long batchOperationKey) {
    return ImmutableRecord.builder()
        .from(FIXTURES.getCanceledProcessRecord(processInstanceKey, batchOperationKey))
        .withPartitionId(p)
        .build();
  }

  private static Record<RecordValue> processInstance(
      final Record<RecordValue> record, final int p, final long processInstanceKey) {
    return ImmutableRecord.builder()
        .from(record)
        .withKey(processInstanceKey)
        .withPartitionId(p)
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ProcessInstanceRecordValue) record.getValue())
                .withProcessInstanceKey(processInstanceKey)
                .withRootProcessInstanceKey(processInstanceKey)
                .withParentProcessInstanceKey(-1L)
                .withParentElementInstanceKey(-1L)
                .withTags(Set.of())
                .build())
        .build();
  }

  private static Record<RecordValue> element(
      final Record<RecordValue> record,
      final int p,
      final long elementKey,
      final long processInstanceKey) {
    return ImmutableRecord.builder()
        .from(record)
        .withKey(elementKey)
        .withPartitionId(p)
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ProcessInstanceRecordValue) record.getValue())
                .withProcessInstanceKey(processInstanceKey)
                .withRootProcessInstanceKey(processInstanceKey)
                .withParentProcessInstanceKey(-1L)
                .withParentElementInstanceKey(-1L)
                .withTags(Set.of())
                .build())
        .build();
  }
}
