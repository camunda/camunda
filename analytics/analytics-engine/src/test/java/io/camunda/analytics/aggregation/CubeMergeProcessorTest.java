/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.aggregation.CubeMergeProcessor.CellApplier;
import io.camunda.eventbridge.streaming.aggregate.SegmentDedup;
import io.camunda.eventbridge.streaming.internals.FlowMetrics;
import io.camunda.eventbridge.streaming.shuffle.CellDelta;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelope;
import io.camunda.eventbridge.streaming.shuffle.ShuffleOperation;
import io.camunda.eventbridge.streaming.shuffle.ShufflePayloadKind;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CubeMergeProcessorTest {

  private static final int STREAM_ID = 7;

  @Test
  void shouldCountADuplicateBatchAsDedupSkippedButNotReapplyIt() {
    // given a processor wired with a counting applier and the library lag-pack metrics
    final List<byte[]> applied = new ArrayList<>();
    final CellApplier applier =
        (sourcePartition, keyBytes, windowStart, accBytes) -> applied.add(accBytes);
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final CubeMergeProcessor processor =
        new CubeMergeProcessor(
            new SegmentDedup(),
            Map.of(STREAM_ID, applier),
            List.of(),
            (sourcePartition, segment, chunk, cell) -> {
              throw new AssertionError("no stream is unknown in this test");
            },
            FlowMetrics.of(registry, "aggregation", 1));

    // when the same (sourcePartition, segment, chunk) batch is delivered twice — a producer re-emit
    processor.process(envelope(0L, 0));
    processor.process(envelope(0L, 0));

    // then the cell applied exactly once, and the dedup skip is counted for the re-emit
    assertThat(applied).hasSize(1);
    assertThat(
            registry
                .get("eb.streaming.dedup.skipped")
                .tag("stage", "aggregation")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldNotCountADistinctSegmentAsDedupSkipped() {
    // given a processor wired with the library lag-pack metrics
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final CubeMergeProcessor processor =
        new CubeMergeProcessor(
            new SegmentDedup(),
            Map.of(STREAM_ID, (sourcePartition, keyBytes, windowStart, accBytes) -> {}),
            List.of(),
            (sourcePartition, segment, chunk, cell) -> {
              throw new AssertionError("no stream is unknown in this test");
            },
            FlowMetrics.of(registry, "aggregation", 1));

    // when two genuinely distinct segments are delivered
    processor.process(envelope(0L, 0));
    processor.process(envelope(1L, 0));

    // then neither counts as a dedup skip
    assertThat(
            registry
                .get("eb.streaming.dedup.skipped")
                .tag("stage", "aggregation")
                .counter()
                .count())
        .isZero();
  }

  private static ShuffleEnvelope envelope(final long segment, final int chunk) {
    return new ShuffleEnvelope(
        0L,
        1,
        1,
        segment,
        chunk,
        false,
        ShufflePayloadKind.AGGREGATE_DELTA,
        ShuffleOperation.MERGE,
        List.of(new CellDelta(STREAM_ID, 0L, new byte[] {1}, new byte[] {2})));
  }
}
