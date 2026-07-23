/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.write;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link OpenInstancesGaugeSampler}'s batching logic against a fake {@link GaugeBatchSink}
 * that just collects the batches it receives — no Iceberg/DuckDB machinery involved, only the
 * accumulate-then-flush-on-interval behavior itself (see {@link OpenInstancesGaugeWriterTest} for
 * the real-writer, real-commit coverage).
 */
final class OpenInstancesGaugeSamplerTest {

  private static final long FLUSH_INTERVAL_MS = 300_000L; // 5 minutes, matches the default knob

  @Test
  void shouldNotFlushBeforeIntervalElapses() {
    // given a sampler that just started
    final List<List<GaugeSample>> flushedBatches = new ArrayList<>();
    final OpenInstancesGaugeSampler sampler =
        new OpenInstancesGaugeSampler(flushedBatches::add, FLUSH_INTERVAL_MS, 0L);

    // when several ticks land before the flush interval elapses
    sampler.tick(0L, Map.of("order-intake", 3L));
    sampler.tick(150_000L, Map.of("order-intake", 4L));
    sampler.tick(299_999L, Map.of("order-intake", 5L));

    // then nothing has been flushed yet
    assertThat(flushedBatches).isEmpty();
  }

  @Test
  void shouldFlushEveryAccumulatedSampleOnceIntervalElapses() {
    // given three ticks' worth of samples buffered across two processes
    final List<List<GaugeSample>> flushedBatches = new ArrayList<>();
    final OpenInstancesGaugeSampler sampler =
        new OpenInstancesGaugeSampler(flushedBatches::add, FLUSH_INTERVAL_MS, 0L);
    sampler.tick(0L, Map.of("order-intake", 3L));
    sampler.tick(150_000L, Map.of("order-intake", 5L, "dispute-handling", 2L));

    // when a tick lands once the flush interval has elapsed
    sampler.tick(300_000L, Map.of("order-intake", 6L));

    // then exactly one batch was flushed, containing every buffered sample across all three ticks
    assertThat(flushedBatches).hasSize(1);
    final List<GaugeSample> batch = flushedBatches.get(0);
    assertThat(batch)
        .containsExactlyInAnyOrder(
            new GaugeSample(0L, "order-intake", 3L),
            new GaugeSample(150_000L, "order-intake", 5L),
            new GaugeSample(150_000L, "dispute-handling", 2L),
            new GaugeSample(300_000L, "order-intake", 6L));
  }

  @Test
  void shouldStartAFreshBufferAfterAFlush() {
    // given a sampler that already flushed once
    final List<List<GaugeSample>> flushedBatches = new ArrayList<>();
    final OpenInstancesGaugeSampler sampler =
        new OpenInstancesGaugeSampler(flushedBatches::add, FLUSH_INTERVAL_MS, 0L);
    sampler.tick(0L, Map.of("order-intake", 3L));
    sampler.tick(300_000L, Map.of("order-intake", 4L)); // triggers the first flush

    // when another tick lands, still short of the next flush interval
    sampler.tick(450_000L, Map.of("order-intake", 5L));

    // then only the first flush happened -- the post-flush tick is buffered, not yet flushed
    assertThat(flushedBatches).hasSize(1);
  }

  @Test
  void shouldOmitProcessesWithNoOpenInstances() {
    // given a tick where one process has open instances and another has dropped to zero
    final List<List<GaugeSample>> flushedBatches = new ArrayList<>();
    final OpenInstancesGaugeSampler sampler =
        new OpenInstancesGaugeSampler(flushedBatches::add, FLUSH_INTERVAL_MS, 0L);
    sampler.tick(0L, Map.of("order-intake", 3L, "dispute-handling", 0L));

    // when the interval elapses and the buffer flushes
    sampler.tick(300_000L, Map.of());

    // then only the process with a positive count produced a row -- no zero-count row, and no
    // synthesized "all processes" total row either
    assertThat(flushedBatches).hasSize(1);
    assertThat(flushedBatches.get(0)).containsExactly(new GaugeSample(0L, "order-intake", 3L));
  }

  @Test
  void shouldFlushBufferedTailOnClose() {
    // given samples buffered but not yet due for a periodic flush
    final List<List<GaugeSample>> flushedBatches = new ArrayList<>();
    final OpenInstancesGaugeSampler sampler =
        new OpenInstancesGaugeSampler(flushedBatches::add, FLUSH_INTERVAL_MS, 0L);
    sampler.tick(0L, Map.of("order-intake", 3L));
    sampler.tick(60_000L, Map.of("order-intake", 4L));

    // when the sampler is closed (shutdown path)
    sampler.close();

    // then the buffered tail was flushed as one final batch
    assertThat(flushedBatches).hasSize(1);
    assertThat(flushedBatches.get(0))
        .containsExactlyInAnyOrder(
            new GaugeSample(0L, "order-intake", 3L), new GaugeSample(60_000L, "order-intake", 4L));
  }

  @Test
  void shouldNotFlushAgainOnCloseWhenBufferAlreadyEmpty() {
    // given a sampler with nothing buffered (e.g. every process had zero open instances)
    final List<List<GaugeSample>> flushedBatches = new ArrayList<>();
    final OpenInstancesGaugeSampler sampler =
        new OpenInstancesGaugeSampler(flushedBatches::add, FLUSH_INTERVAL_MS, 0L);

    // when closed
    sampler.close();

    // then the sink is never called with an empty batch
    assertThat(flushedBatches).isEmpty();
  }
}
