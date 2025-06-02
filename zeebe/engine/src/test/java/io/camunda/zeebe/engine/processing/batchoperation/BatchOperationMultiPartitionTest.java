/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.stream.Collectors;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;

public final class BatchOperationMultiPartitionTest {
  private static final int PARTITION_COUNT = 3;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Test
  public void shouldCreateOnAllPartitions() {
    // given

    // when
    final long batchOperationKey =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(new UnsafeBuffer("{\"hasIncident\": false}".getBytes()))
            .create()
            .getValue()
            .getBatchOperationKey();

    assertThatCommandIsDistributedCorrectly(
        ValueType.BATCH_OPERATION_CREATION,
        BatchOperationIntent.CREATE,
        BatchOperationIntent.CREATED);

    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.batchOperationCreationRecords()
                  .withBatchOperationKey(batchOperationKey)
                  .withPartitionId(partitionId)
                  .limit(record -> record.getIntent().equals(BatchOperationIntent.CREATED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsExactly(BatchOperationIntent.CREATE, BatchOperationIntent.CREATED);
    }
  }

  @Test
  public void shouldCancelOnAllPartitions() {
    // given
    final long batchOperationKey = createDistributedBatchOperation();

    // when

    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).cancel();

    assertThatCommandIsDistributedCorrectly(
        ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
        BatchOperationIntent.CANCEL,
        BatchOperationIntent.CANCELED);

    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.batchOperationLifecycleRecords()
                  .withBatchOperationKey(batchOperationKey)
                  .withPartitionId(partitionId)
                  .limit(record -> record.getIntent().equals(BatchOperationIntent.CANCELED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsExactly(BatchOperationIntent.CANCEL, BatchOperationIntent.CANCELED);
    }
  }

  @Test
  public void shouldSuspendOnAllPartitions() {
    // given
    final long batchOperationKey = createDistributedBatchOperation();

    // when
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).suspend();

    assertThatCommandIsDistributedCorrectly(
        ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
        BatchOperationIntent.SUSPEND,
        BatchOperationIntent.SUSPENDED);

    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.batchOperationLifecycleRecords()
                  .withBatchOperationKey(batchOperationKey)
                  .withPartitionId(partitionId)
                  .limit(record -> record.getIntent().equals(BatchOperationIntent.SUSPENDED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsExactly(BatchOperationIntent.SUSPEND, BatchOperationIntent.SUSPENDED);
    }
  }

  @Test
  public void shouldResumeOnAllPartitions() {
    // given
    final long batchOperationKey = createDistributedBatchOperation();
    suspendDistributedBatchOperation(batchOperationKey);

    // when
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).resume();

    assertThatCommandIsDistributedCorrectly(
        ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
        BatchOperationIntent.RESUME,
        BatchOperationIntent.RESUMED);

    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.batchOperationLifecycleRecords()
                  .withBatchOperationKey(batchOperationKey)
                  .withPartitionId(partitionId)
                  .limit(record -> record.getIntent().equals(BatchOperationIntent.RESUMED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsExactly(BatchOperationIntent.RESUME, BatchOperationIntent.RESUMED);
    }
  }

  private long createDistributedBatchOperation() {
    final long batchOperationKey =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(new UnsafeBuffer("{\"hasIncident\": false}".getBytes()))
            .create()
            .getValue()
            .getBatchOperationKey();

    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limit(record -> record.getIntent().equals(CommandDistributionIntent.FINISHED)))
        .extracting(Record::getIntent)
        .endsWith(CommandDistributionIntent.FINISHED);

    RecordingExporter.reset();

    return batchOperationKey;
  }

  private void suspendDistributedBatchOperation(final long batchOperationKey) {
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).suspend();

    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limit(record -> record.getIntent().equals(CommandDistributionIntent.FINISHED)))
        .extracting(Record::getIntent)
        .endsWith(CommandDistributionIntent.FINISHED);

    RecordingExporter.reset();
  }

  private void assertThatCommandIsDistributedCorrectly(
      final ValueType valueType, final Intent commandIntent, final Intent eventIntent) {
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limit(record -> record.getIntent().equals(CommandDistributionIntent.FINISHED))
                .filter(
                    record ->
                        record.getValueType() == valueType
                            || (record.getValueType() == ValueType.COMMAND_DISTRIBUTION
                                && ((CommandDistributionRecordValue) record.getValue()).getIntent()
                                    == commandIntent)))
        .extracting(Record::getIntent, Record::getRecordType, this::extractPartitionId)
        .startsWith(
            tuple(commandIntent, RecordType.COMMAND, 1),
            tuple(eventIntent, RecordType.EVENT, 1),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));

    // is in correct queue
    assertThat(
            RecordingExporter.commandDistributionRecords()
                .withIntent(CommandDistributionIntent.ENQUEUED)
                .limit(r -> r.getIntent() == CommandDistributionIntent.ENQUEUED))
        .extracting(r -> r.getValue().getQueueId())
        .containsOnly(DistributionQueue.BATCH_OPERATION.getQueueId());
  }

  private Integer extractPartitionId(final Record<RecordValue> r) {
    return r.getValue() instanceof CommandDistributionRecordValue
        ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
        : r.getPartitionId();
  }
}
