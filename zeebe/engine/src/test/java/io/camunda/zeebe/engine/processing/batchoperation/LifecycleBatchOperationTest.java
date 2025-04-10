/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Set;
import org.junit.Test;

public final class LifecycleBatchOperationTest extends AbstractBatchOperationTest {

  @Test
  public void shouldCancelBatchOperation() {
    // given
    final var processInstanceKeys = Set.of(1L, 2L, 3L);
    final var batchOperationKey =
        createNewProcessInstanceCancellationBatchOperation(processInstanceKeys);

    // when we cancel the batch operation
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).cancel();

    // then we have a canceled event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents())
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.CANCELED);

    // and no follow op up command to execute again
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyCommands())
        .extracting(Record::getIntent)
        .doesNotContain(BatchOperationExecutionIntent.EXECUTE);
  }

  @Test
  public void shouldPauseBatchOperationInScheduler() {
    // given
    final var processInstanceKeys = Set.of(1L, 2L, 3L);
    final var batchOperationKey =
        createNewProcessInstanceCancellationBatchOperation(processInstanceKeys);

    // when we pause the batch operation
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).pause();

    // then we have a paused event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents())
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.PAUSED);

    // and no follow op up command to execute again
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyCommands())
        .extracting(Record::getIntent)
        .doesNotContain(BatchOperationExecutionIntent.EXECUTE);
  }

  @Test
  public void shouldPauseBatchOperationInExecutor() {
    // given
    final var processInstanceKeys = Set.of(1L, 2L, 3L);
    final var batchOperationKey =
        createNewProcessInstanceCancellationBatchOperation(processInstanceKeys);

    // when we pause the batch operation
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).pause();

    // and send the execute command
    engine
        .batchOperation()
        .newExecution()
        .withBatchOperationKey(batchOperationKey)
        .executeWithoutExpectation();

    // then we have a paused event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents())
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.PAUSED);

    // and that we have no executed event
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents())
        .extracting(Record::getIntent)
        .doesNotContain(BatchOperationExecutionIntent.EXECUTED);
  }

  @Test
  public void shouldRejectPauseBatchOperationIfNotFound() {
    // given a nonexisting batch operation key
    final var batchOperationKey = 1L;

    // when we pause the batch operation which does not exist
    engine
        .batchOperation()
        .newLifecycle()
        .withBatchOperationKey(batchOperationKey)
        .pauseWithoutExpectations();

    // then we have a rejected command
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withRejectionType(RejectionType.NOT_FOUND)
                .onlyCommandRejections())
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.PAUSE);

    // and no follow op up command to execute again
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyCommands())
        .extracting(Record::getIntent)
        .doesNotContain(BatchOperationExecutionIntent.EXECUTE);
  }

  @Test
  public void shouldRejectPauseBatchOperationIfInvalidState() {
    // given a failed batch operation
    final var batchOperationKey = createNewFailedProcessInstanceCancellationBatchOperation();

    // when we pause the batch operation which does not exist
    engine
        .batchOperation()
        .newLifecycle()
        .withBatchOperationKey(batchOperationKey)
        .pauseWithoutExpectations();

    // then we have a rejected command
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withRejectionType(RejectionType.INVALID_STATE)
                .onlyCommandRejections())
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.PAUSE);

    // and no follow-up command to execute again
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyCommands())
        .extracting(Record::getIntent)
        .doesNotContain(BatchOperationExecutionIntent.EXECUTE);
  }

  @Test
  public void shouldResumeBatchOperation() {
    // given
    final var processInstanceKeys = Set.of(1L, 2L, 3L);
    final var batchOperationKey =
        createNewProcessInstanceCancellationBatchOperation(processInstanceKeys);

    // and we pause the batch operation
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).pause();

    // when we resume it again
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).resume();

    // then the batch should be active
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents())
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.PAUSED, BatchOperationIntent.RESUMED);

    // and at least the completed
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents())
        .extracting(Record::getIntent)
        .containsSequence(
            BatchOperationExecutionIntent.EXECUTED, BatchOperationExecutionIntent.COMPLETED);
  }

  @Test
  public void shouldRejectResumeBatchOperationIfNotFound() {
    // when we resume it again
    final var batchOperationKey = 1L;
    engine
        .batchOperation()
        .newLifecycle()
        .withBatchOperationKey(batchOperationKey)
        .resumeWithoutExpectation();

    // then we have a rejected command
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withRejectionType(RejectionType.NOT_FOUND)
                .onlyCommandRejections())
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.RESUME);

    // and no follow-up command to execute again
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyCommands())
        .extracting(Record::getIntent)
        .doesNotContain(BatchOperationExecutionIntent.EXECUTE);
  }

  @Test
  public void shouldRejectResumeBatchOperationIfInvalidState() {
    // given a batch operation wich is not paused
    final var processInstanceKeys = Set.of(1L, 2L, 3L);
    final var batchOperationKey =
        createNewProcessInstanceCancellationBatchOperation(processInstanceKeys);

    // when we resume the batch operation which is not paused
    engine
        .batchOperation()
        .newLifecycle()
        .withBatchOperationKey(batchOperationKey)
        .resumeWithoutExpectation();

    // then we have a rejected command
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withRejectionType(RejectionType.INVALID_STATE)
                .onlyCommandRejections())
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.RESUME);
  }
}
