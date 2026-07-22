/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.TableSchema;
import org.junit.jupiter.api.Test;

/**
 * Synchronous, single-threaded coverage of the failure-recovery hardening in {@link
 * FileWindow#finishAll()}: when a window spans several open files and one of them fails to finish,
 * an encoder that already finished successfully must never be re-touched by the subsequent {@link
 * FileWindow#abortAll()} — only the encoder that never finished should be aborted.
 */
class FileWindowFailureRecoveryTest {

  @Test
  void shouldLeaveAnAlreadyFinishedEncoderUntouchedWhenASiblingFailsToFinish() {
    // given: two distinct family days open in the same window
    final TableSchema schema = TestPipelines.schema("t-file-window-failure");
    final FakeBatchEncoderFactory factory = new FakeBatchEncoderFactory();
    final FileWindow window = new FileWindow(factory, schema);

    final Segment day100 = TestPipelines.segmentWithValues(schema, 1L, 2L);
    window.append(new IdentitySortedRun(day100, 100L));
    final Segment day200 = TestPipelines.segmentWithValues(schema, 3L, 4L);
    window.append(new IdentitySortedRun(day200, 200L));

    assertThat(factory.created).hasSize(2);
    final FakeBatchEncoderFactory.FakeBatchEncoder firstOpened = factory.created.get(0);
    final FakeBatchEncoderFactory.FakeBatchEncoder secondOpened = factory.created.get(1);
    assertThat(firstOpened.epochDay).isEqualTo(100L);
    assertThat(secondOpened.epochDay).isEqualTo(200L);

    // when: the second file (day 200) fails to finish
    secondOpened.throwOnFinish.set(true);

    // then: finishAll() propagates the failure
    assertThatThrownBy(window::finishAll).isInstanceOf(RuntimeException.class);

    // and: the first file already finished successfully, untouched by anything that follows
    assertThat(firstOpened.finished).isTrue();
    assertThat(firstOpened.aborted).isFalse();
    // and: the second file never finished
    assertThat(secondOpened.finished).isFalse();

    // when: the failure-recovery path runs abortAll()
    window.abortAll();

    // then: the already-finished file was never aborted; the never-finished one was
    assertThat(firstOpened.aborted).isFalse();
    assertThat(secondOpened.aborted).isTrue();
  }
}
