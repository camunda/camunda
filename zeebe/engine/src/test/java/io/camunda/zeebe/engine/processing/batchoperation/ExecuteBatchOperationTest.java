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
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Set;
import org.junit.Test;

public final class ExecuteBatchOperationTest extends AbstractBatchOperationTest {

  @Test
  public void shouldExecuteBatchOperationForProcessInstanceCancellation() {
    // given
    final var processInstanceKeys = Set.of(1L, 2L, 3L);
    final var batchOperationKey =
        createNewProcessInstanceCancellationBatchOperation(processInstanceKeys);

    // then we have executed and completed event
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents())
        .extracting(Record::getIntent)
        .containsSequence(
            BatchOperationExecutionIntent.EXECUTED, BatchOperationExecutionIntent.COMPLETED);

    // and a follow op up command to execute again
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyCommands())
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationExecutionIntent.EXECUTE);

    // and we have several process cancellation commands
    processInstanceKeys.forEach(
        key -> {
          assertThat(RecordingExporter.processInstanceRecords().withProcessInstanceKey(key))
              .extracting(Record::getIntent)
              .containsSequence(ProcessInstanceIntent.CANCEL);
        });
  }

  @Test
  public void shouldDoNothingIfThereIsNoBatchOperation() {
    // given
    final var batchOperationKey = 1L;

    // when
    engine
        .batchOperation()
        .newExecution()
        .withBatchOperationKey(batchOperationKey)
        .executeWithoutExpectation();

    // then
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey))
        .extracting(Record::getIntent)
        .doesNotContain(
            BatchOperationExecutionIntent.EXECUTED, BatchOperationExecutionIntent.COMPLETED);
  }

  @Test
  public void shouldCancelBatchOperationForProcessInstanceCancellation() {
    // given
    final var processInstanceKeys = Set.of(1L, 2L, 3L);
    final var batchOperationKey =
        createNewProcessInstanceCancellationBatchOperation(processInstanceKeys);

    // when we cancel the batch operation
    engine.batchOperation().newExecution().withBatchOperationKey(batchOperationKey).cancel();

    // then we have a canceled event
    assertThat(
        RecordingExporter.batchOperationExecutionRecords()
            .withBatchOperationKey(batchOperationKey)
            .onlyEvents())
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationExecutionIntent.CANCELED);

    // and no follow op up command to execute again
    assertThat(
        RecordingExporter.batchOperationExecutionRecords()
            .withBatchOperationKey(batchOperationKey)
            .onlyCommands())
        .extracting(Record::getIntent)
        .doesNotContain(BatchOperationExecutionIntent.EXECUTE);
  }
}
