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
    final var batchOperationKey = createNewCancelProcessInstanceBatchOperation(processInstanceKeys);

    // when we cancel the batch operation
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).cancel();

    // then we have a canceled event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.CANCELED))
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
  public void shouldSuspendBatchOperationInScheduler() {
    // given
    final var processInstanceKeys = Set.of(1L, 2L, 3L);
    final var batchOperationKey = createNewCancelProcessInstanceBatchOperation(processInstanceKeys);

    // when we suspend the batch operation
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).suspend();

    // then we have a suspended event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.SUSPENDED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.SUSPENDED);

    // and no follow op up command to execute again
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyCommands())
        .extracting(Record::getIntent)
        .doesNotContain(BatchOperationExecutionIntent.EXECUTE);
  }

  @Test
  public void shouldSuspendBatchOperationInExecutor() {
    // given
    final var processInstanceKeys = Set.of(1L, 2L, 3L);
    final var batchOperationKey = createNewCancelProcessInstanceBatchOperation(processInstanceKeys);

    // when we suspend the batch operation
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).suspend();

    // and send the execute command
    engine
        .batchOperation()
        .newExecution()
        .withBatchOperationKey(batchOperationKey)
        .executeWithoutExpectation();

    // then we have a suspended event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.SUSPENDED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.SUSPENDED);

    // and that we have no executed event
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents())
        .extracting(Record::getIntent)
        .doesNotContain(BatchOperationExecutionIntent.EXECUTED);
  }

  @Test
  public void shouldRejectSuspendBatchOperationIfNotFound() {
    // given a non-existing batch operation key
    final var batchOperationKey = 1L;

    // when we suspend the batch operation which does not exist
    engine
        .batchOperation()
        .newLifecycle()
        .withBatchOperationKey(batchOperationKey)
        .suspendWithoutExpectations();

    // then we have a rejected command
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withRejectionType(RejectionType.NOT_FOUND)
                .onlyCommandRejections()
                .limit(r -> r.getIntent() == BatchOperationIntent.SUSPEND))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.SUSPEND);

    // and no follow op up command to execute again
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
    final var batchOperationKey = createNewCancelProcessInstanceBatchOperation(processInstanceKeys);

    // and we suspend the batch operation
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).suspend();

    // when we resume it again
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).resume();

    // then the batch should be active
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents())
        .extracting(Record::getIntent)
        .containsSequence(
            BatchOperationIntent.SUSPENDED,
            BatchOperationIntent.RESUMED,
            BatchOperationIntent.COMPLETED);

    // and at least one EXECUTED
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationExecutionIntent.EXECUTED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationExecutionIntent.EXECUTED);
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
                .onlyCommandRejections()
                .limit(r -> r.getIntent() == BatchOperationIntent.RESUME))
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
    // given a batch operation wich is not suspended
    final var processInstanceKeys = Set.of(1L, 2L, 3L);
    final var batchOperationKey = createNewCancelProcessInstanceBatchOperation(processInstanceKeys);

    // when we resume the batch operation which is not suspended
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
                .onlyCommandRejections()
                .limit(r -> r.getIntent() == BatchOperationIntent.RESUME))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.RESUME);
  }

  @Test
  public void shouldRejectCancelBatchOperationWithNoPermission() {
    // given
    final var processInstanceKeys = Set.of(1L, 2L, 3L);
    final var batchOperationKey = createNewCancelProcessInstanceBatchOperation(processInstanceKeys);

    // and we add a new user with no permissions
    final var user = createUser();

    // when we cancel the batch operation
    engine
        .batchOperation()
        .newLifecycle()
        .withBatchOperationKey(batchOperationKey)
        .expectRejection()
        .cancel(user.getUsername());

    // then we have a rejected cancel command
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .onlyCommandRejections()
                .withBatchOperationKey(batchOperationKey)
                .limit(r -> r.getIntent() == BatchOperationIntent.CANCEL))
        .allSatisfy(
            r -> {
              assertThat(r.getIntent()).isEqualTo(BatchOperationIntent.CANCEL);
              assertThat(r.getRejectionType()).isEqualTo(RejectionType.FORBIDDEN);
            });
  }

  @Test
  public void shouldRejectSuspendBatchOperationWithNoPermission() {
    // given
    final var processInstanceKeys = Set.of(1L, 2L, 3L);
    final var batchOperationKey = createNewCancelProcessInstanceBatchOperation(processInstanceKeys);

    // and we add a new user with no permissions
    final var user = createUser();

    // when we cancel the batch operation
    engine
        .batchOperation()
        .newLifecycle()
        .withBatchOperationKey(batchOperationKey)
        .expectRejection()
        .suspend(user.getUsername());

    // then we have a rejected cancel command
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .onlyCommandRejections()
                .withBatchOperationKey(batchOperationKey)
                .limit(r -> r.getIntent() == BatchOperationIntent.SUSPEND))
        .allSatisfy(
            r -> {
              assertThat(r.getIntent()).isEqualTo(BatchOperationIntent.SUSPEND);
              assertThat(r.getRejectionType()).isEqualTo(RejectionType.FORBIDDEN);
            });
  }

  @Test
  public void shouldRejectResumeBatchOperationWithNoPermission() {
    // given
    final var processInstanceKeys = Set.of(1L, 2L, 3L);
    final var batchOperationKey = createNewCancelProcessInstanceBatchOperation(processInstanceKeys);

    // and we suspend the batch operation
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).suspend();

    // and we add a new user with no permissions
    final var user = createUser();

    // when we resume the batch operation
    engine
        .batchOperation()
        .newLifecycle()
        .withBatchOperationKey(batchOperationKey)
        .expectRejection()
        .resume(user.getUsername());

    // then we have a rejected cancel command
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .onlyCommandRejections()
                .withBatchOperationKey(batchOperationKey)
                .limit(r -> r.getIntent() == BatchOperationIntent.RESUME))
        .allSatisfy(
            r -> {
              assertThat(r.getIntent()).isEqualTo(BatchOperationIntent.RESUME);
              assertThat(r.getRejectionType()).isEqualTo(RejectionType.FORBIDDEN);
            });
  }
}
