/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.sink.SealReason;
import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.TableSchema;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A genuinely full ring must pause the gate exactly once and refuse further appends; once the flush
 * thread frees a slot, the gate must resume and appends proceed. The flush thread is stalled inside
 * a blocking sorter so the ring-full condition is deterministic rather than timing-dependent.
 */
class SinkPipelineBackpressureTest {

  private static final Duration AWAIT = Duration.ofSeconds(5);

  @Test
  void shouldPauseOnceWhenRingFullAndResumeOnceAFlushFreesASlot() throws InterruptedException {
    // given: a 2-segment ring (the minimum), one row per segment, and a sorter that stalls the
    // flush thread on its first call so the first sealed segment is never released prematurely
    final TableSchema schema = TestPipelines.schema("t-backpressure");
    final SinkConfig config =
        new SinkConfig(1, 2, 1_000_000_000L, 1_000_000_000L, 0, schema.table());
    final Segment[] segments =
        TestPipelines.newSegments(schema, config.ringSegments(), config.segmentRows());
    final FakeBackpressureGate gate = new FakeBackpressureGate();
    final FakeDescriptorSink sink = new FakeDescriptorSink();
    final FakeBatchEncoderFactory factory = new FakeBatchEncoderFactory();
    final BlockingFirstCallSorter sorter = new BlockingFirstCallSorter(TestPipelines.EPOCH_DAY);
    final SinkPipeline pipeline =
        new SinkPipeline(
            config,
            segments,
            gate,
            sorter,
            factory,
            sink,
            List.of(),
            new FakeClock(0),
            new SimpleMeterRegistry());
    pipeline.start();

    // when: the first row fills and seals segment A (SEGMENT_FULL); the flush thread takes it and
    // stalls inside the sorter
    assertThat(TestPipelines.tryAppendRow(pipeline.ring(), 0L)).isTrue();
    assertThat(sorter.awaitEntered(AWAIT)).isTrue();

    // then: segment A is taken but not released (tail unchanged) — the ring still shows it sealed
    assertThat(pipeline.ring().sealedCount()).isEqualTo(1);

    // when: a second row fills segment B and its SEGMENT_FULL seal is attempted
    assertThat(TestPipelines.tryAppendRow(pipeline.ring(), 1L)).isTrue();

    // then: the ring is genuinely full (A held, B filling+full) — the seal failed, the gate paused
    // exactly once, and B is left sealed-pending (still occupying the ring's one FILLING slot)
    assertThat(pipeline.ring().sealedCount()).isEqualTo(1);
    assertThat(gate.pauseCount.get()).isEqualTo(1);
    assertThat(gate.resumeCount.get()).isZero();

    // and: a further row is refused outright (appends must stop, never buffer elsewhere)
    assertThat(TestPipelines.tryAppendRow(pipeline.ring(), 2L)).isFalse();

    // when: the flush thread is released, finishes processing A (SEGMENT_FULL -> release
    // immediately)
    sorter.release();
    TestWaits.awaitTrue(() -> gate.resumeCount.get() == 1, AWAIT);

    // then: the gate resumed exactly once, and only once
    assertThat(gate.pauseCount.get()).isEqualTo(1);
    assertThat(gate.resumeCount.get()).isEqualTo(1);

    // and: appends now proceed — B's previously-failed seal attempt succeeds on retry
    assertThat(pipeline.ring().seal(SealReason.SEGMENT_FULL)).isTrue();

    pipeline.close();
  }
}
