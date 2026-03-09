/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Set;
import org.junit.Test;

/**
 * Regression test for <a
 * href="https://github.com/camunda/camunda/issues/43097">camunda/camunda#43097</a>.
 *
 * <p>A suspended batch operation should not block other pending batch operations from making
 * progress. When a batch operation is suspended, the scheduler should skip it and process the next
 * pending batch operation in the queue.
 */
public final class SuspendedBatchOperationBlocksQueueTest extends AbstractBatchOperationTest {

  @Test
  public void shouldNotBlockOtherBatchOperationsWhenOneSuspended() {
    // given — create batchA and suspend it
    final var batchAKeys = Set.of(1L, 2L, 3L);
    final var batchAKey = createNewCancelProcessInstanceBatchOperation(batchAKeys);

    // suspend batchA before it gets a chance to execute
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchAKey).suspend();

    // verify batchA is actually suspended
    RecordingExporter.batchOperationLifecycleRecords()
        .withBatchOperationKey(batchAKey)
        .withIntent(BatchOperationIntent.SUSPENDED)
        .await();

    // when — create batchB which should complete independently of the suspended batchA
    final var batchBKeys = Set.of(4L, 5L, 6L);
    final var batchBKey = createNewCancelProcessInstanceBatchOperation(batchBKeys);

    // then — batchB should complete while batchA remains suspended.
    // RecordingExporter blocks (up to its configured timeout) until the COMPLETED event appears.
    // If the bug is present, batchB is stuck behind the suspended batchA and this will time out.
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchBKey)
                .withIntent(BatchOperationIntent.COMPLETED)
                .exists())
        .describedAs(
            "Batch operation B (key=%d) should have completed, "
                + "but it is blocked by the suspended batch operation A (key=%d).",
            batchBKey, batchAKey)
        .isTrue();
  }
}
