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
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
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

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withEngineConfig(
              config -> config.setBatchOperationSchedulerInterval(Duration.ofDays(1)));

  @Test
  public void shouldCreateOnAllPartitions() {
    // given
    // nothing

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
  public void shouldCompleteOnAllPartitions() {
    // given
    final long batchOperationKey =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(new UnsafeBuffer("{\"hasIncident\": false}".getBytes()))
            .create()
            .getValue()
            .getBatchOperationKey();

    // when
    for (int i = 1; i <= PARTITION_COUNT; i++) {
      engine
          .batchOperation()
          .newExecution()
          .onPartition(i)
          .withBatchOperationKey(batchOperationKey)
          .execute();
    }

    assertThat(
            RecordingExporter.batchOperationPartitionLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withPartitionId(1)
                .limitByCount(
                    record -> record.getIntent().equals(BatchOperationIntent.PARTITION_COMPLETED),
                    3)
                .collect(Collectors.toList()))
        .extracting(Record::getIntent)
        .containsExactlyInAnyOrder(
            BatchOperationIntent.COMPLETE_PARTITION,
            BatchOperationIntent.COMPLETE_PARTITION,
            BatchOperationIntent.COMPLETE_PARTITION,
            BatchOperationIntent.PARTITION_COMPLETED,
            BatchOperationIntent.PARTITION_COMPLETED,
            BatchOperationIntent.PARTITION_COMPLETED);

    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withPartitionId(1)
                .limit(record -> record.getIntent().equals(BatchOperationIntent.COMPLETED))
                .collect(Collectors.toList()))
        .extracting(Record::getIntent)
        .contains(BatchOperationIntent.COMPLETED);
    assertThat(engine.getProcessingState(1).getBatchOperationState().get(batchOperationKey))
        .isEmpty();

    for (int i = 2; i <= PARTITION_COUNT; i++) {
      assertThat(
              RecordingExporter.batchOperationPartitionLifecycleRecords()
                  .withBatchOperationKey(batchOperationKey)
                  .withPartitionId(i)
                  .limit(
                      record -> record.getIntent().equals(BatchOperationIntent.PARTITION_COMPLETED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .contains(BatchOperationIntent.PARTITION_COMPLETED);
      assertThat(engine.getProcessingState(i).getBatchOperationState().get(batchOperationKey))
          .isEmpty();
    }
  }

  @Test
  public void shouldTreatFailedAsPartiallyCompleted() {
    // given
    final long batchOperationKey = createDistributedBatchOperation();

    // when
    // fail partition 3 and complete the others
    engine
        .batchOperation()
        .newPartitionLifecycle()
        .withBatchOperationKey(batchOperationKey)
        .onPartition(PARTITION_COUNT)
        .fail();
    for (int i = 1; i < PARTITION_COUNT; i++) {
      engine
          .batchOperation()
          .newExecution()
          .onPartition(i)
          .withBatchOperationKey(batchOperationKey)
          .execute();
    }

    // then
    assertThat(
            RecordingExporter.batchOperationPartitionLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withPartitionId(1)
                .limitByCount(
                    record ->
                        record.getIntent().equals(BatchOperationIntent.PARTITION_FAILED)
                            || record.getIntent().equals(BatchOperationIntent.PARTITION_COMPLETED),
                    3)
                .collect(Collectors.toList()))
        .extracting(Record::getIntent)
        .containsExactlyInAnyOrder(
            BatchOperationIntent.FAIL_PARTITION,
            BatchOperationIntent.COMPLETE_PARTITION,
            BatchOperationIntent.COMPLETE_PARTITION,
            BatchOperationIntent.PARTITION_FAILED,
            BatchOperationIntent.PARTITION_COMPLETED,
            BatchOperationIntent.PARTITION_COMPLETED);

    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withPartitionId(1)
                .limit(record -> record.getIntent().equals(BatchOperationIntent.COMPLETED))
                .collect(Collectors.toList()))
        .extracting(Record::getIntent)
        .contains(BatchOperationIntent.COMPLETED);

    // partitions 1 and 2 are completed
    for (int i = 1; i < PARTITION_COUNT; i++) {
      assertThat(
              RecordingExporter.batchOperationPartitionLifecycleRecords()
                  .withBatchOperationKey(batchOperationKey)
                  .withPartitionId(i)
                  .limit(
                      record -> record.getIntent().equals(BatchOperationIntent.PARTITION_COMPLETED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .contains(BatchOperationIntent.PARTITION_COMPLETED);
      assertThat(engine.getProcessingState(i).getBatchOperationState().get(batchOperationKey))
          .isEmpty();
    }

    // partitions 3 is failed
    assertThat(
            RecordingExporter.batchOperationPartitionLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withPartitionId(3)
                .limit(record -> record.getIntent().equals(BatchOperationIntent.PARTITION_FAILED))
                .collect(Collectors.toList()))
        .extracting(Record::getIntent)
        .contains(BatchOperationIntent.PARTITION_FAILED);
    assertThat(engine.getProcessingState(3).getBatchOperationState().get(batchOperationKey))
        .isEmpty();
  }

  @Test
  public void shouldBeFailedWhenAllPartitionsAreFailed() {
    // given
    final long batchOperationKey = createDistributedBatchOperation();

    // When
    // fail all partitions
    for (int i = 1; i <= PARTITION_COUNT; i++) {
      engine
          .batchOperation()
          .newPartitionLifecycle()
          .withBatchOperationKey(batchOperationKey)
          .onPartition(i)
          .fail();
    }

    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withPartitionId(1)
                .limit(record -> record.getIntent().equals(BatchOperationIntent.FAILED))
                .collect(Collectors.toList()))
        .as("Batch operation is reported as FAILED on lead partition")
        .extracting(Record::getIntent)
        .contains(BatchOperationIntent.FAILED);
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
    suspendDistributedBatchOperation(batchOperationKey, true);

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
          .containsSequence(BatchOperationIntent.RESUME, BatchOperationIntent.RESUMED);
    }
  }

  @Test
  public void shouldResumeOnRemainingPartitionsIfOneFailed() {
    // given
    final long batchOperationKey = createDistributedBatchOperation();

    // fail one partition
    engine
        .batchOperation()
        .newPartitionLifecycle()
        .withBatchOperationKey(batchOperationKey)
        .onPartition(PARTITION_COUNT)
        .fail();

    suspendDistributedBatchOperation(batchOperationKey, false);

    // when
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).resume();

    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .limitByCount(
                    record ->
                        record.getIntent().equals(BatchOperationIntent.RESUME)
                            || record.getIntent().equals(BatchOperationIntent.RESUMED),
                    5)
                .collect(Collectors.toList()))
        .extracting(Record::getIntent)
        .contains(
            BatchOperationIntent.RESUME,
            BatchOperationIntent.RESUME,
            BatchOperationIntent.RESUME,
            BatchOperationIntent.RESUMED,
            BatchOperationIntent.RESUMED);

    // partition 1 and 2 execute
    for (int i = 1; i <= PARTITION_COUNT - 1; i++) {
      engine
          .batchOperation()
          .newExecution()
          .onPartition(i)
          .withBatchOperationKey(batchOperationKey)
          .execute();
    }

    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(record -> record.getIntent().equals(BatchOperationIntent.COMPLETED))
                .collect(Collectors.toList()))
        .allSatisfy(
            record -> assertThat(record.getBatchOperationReference()).isEqualTo(batchOperationKey));

    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limitByCount(
                    record -> record.getIntent().equals(BatchOperationExecutionIntent.EXECUTED), 2)
                .collect(Collectors.toList()))
        .allSatisfy(
            record -> assertThat(record.getBatchOperationReference()).isEqualTo(batchOperationKey));

    assertThat(
            RecordingExporter.batchOperationPartitionLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limitByCount(
                    record ->
                        record.getIntent().equals(BatchOperationIntent.PARTITION_COMPLETED)
                            || record.getIntent().equals(BatchOperationIntent.PARTITION_FAILED),
                    PARTITION_COUNT + 1)
                .collect(Collectors.toList()))
        .allSatisfy(
            record -> assertThat(record.getBatchOperationReference()).isEqualTo(batchOperationKey));
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

  private void suspendDistributedBatchOperation(final long batchOperationKey, final boolean reset) {
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).suspend();

    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limit(record -> record.getIntent().equals(CommandDistributionIntent.FINISHED)))
        .extracting(Record::getIntent)
        .endsWith(CommandDistributionIntent.FINISHED);

    if (reset) {
      RecordingExporter.reset();
    }
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
