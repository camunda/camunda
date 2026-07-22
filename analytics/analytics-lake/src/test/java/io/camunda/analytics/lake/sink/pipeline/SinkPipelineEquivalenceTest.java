/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.sink.Descriptor;
import io.camunda.analytics.lake.sink.TableSchema;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/**
 * The package's core contract: flush timing (segment size, flush interval) is a pure performance
 * knob — feeding the identical row sequence through two differently-chunked pipelines must yield an
 * identical union of appended rows and identical total offset coverage, however differently the
 * physical files/descriptors are cut.
 */
class SinkPipelineEquivalenceTest {

  private static final Duration AWAIT = Duration.ofSeconds(5);
  private static final int ROW_COUNT = 40;
  private static final long NEVER_MS = 1_000_000_000L;

  @Test
  void shouldProduceIdenticalAppendedRowsAndOffsetCoverageRegardlessOfChunking() {
    // given: pipeline A relies purely on SEGMENT_FULL cascades (TIME_DUE/SIZE_CAP never fire).
    // Ring capacity is generously oversized on both pipelines on purpose: this test's point is
    // chunking-invariance, not backpressure (that has its own dedicated tests), so the flush
    // thread is never required to race the feed loop to avoid a false deadlock.
    final TableSchema schemaA = TestPipelines.schema("t-equiv-a");
    final SinkConfig configA = new SinkConfig(6, 20, NEVER_MS, NEVER_MS, 0, schemaA.table());
    final FakeBatchEncoderFactory factoryA = new FakeBatchEncoderFactory();
    final FakeDescriptorSink sinkA = new FakeDescriptorSink();
    final SinkPipeline pipelineA =
        new SinkPipeline(
            configA,
            TestPipelines.newSegments(schemaA, configA.ringSegments(), configA.segmentRows()),
            new FakeBackpressureGate(),
            TestPipelines.identitySorter(),
            factoryA,
            sinkA,
            List.of(),
            new FakeClock(0L),
            new SimpleMeterRegistry());

    // and: pipeline B uses a different segment size and a finite flush interval, so file
    // boundaries fall in completely different places
    final TableSchema schemaB = TestPipelines.schema("t-equiv-b");
    final SinkConfig configB = new SinkConfig(7, 20, 500L, NEVER_MS, 0, schemaB.table());
    final FakeBatchEncoderFactory factoryB = new FakeBatchEncoderFactory();
    final FakeDescriptorSink sinkB = new FakeDescriptorSink();
    final FakeClock clockB = new FakeClock(0L);
    final SinkPipeline pipelineB =
        new SinkPipeline(
            configB,
            TestPipelines.newSegments(schemaB, configB.ringSegments(), configB.segmentRows()),
            new FakeBackpressureGate(),
            TestPipelines.identitySorter(),
            factoryB,
            sinkB,
            List.of(),
            clockB,
            new SimpleMeterRegistry());

    pipelineA.start();
    pipelineB.start();

    // when: the identical row sequence is fed through both, chunked completely differently
    for (long value = 0; value < ROW_COUNT; value++) {
      TestPipelines.appendRowRetrying(pipelineA.ring(), value, AWAIT);
      pipelineA.onPollTick(value, 1000L + value);

      TestPipelines.appendRowRetrying(pipelineB.ring(), value, AWAIT);
      pipelineB.onPollTick(value, 1000L + value);
      if ((value + 1) % 5 == 0) {
        // periodically force pipeline B's TIME_DUE check to re-evaluate on an advanced clock —
        // a no-op if the filling segment happens to be empty right at that instant
        clockB.advance(600L);
        pipelineB.onPollTick(value, 1000L + value);
      }
    }
    pipelineA.close();
    pipelineB.close();

    TestWaits.awaitTrue(() -> !sinkA.accepted.isEmpty() && !sinkB.accepted.isEmpty(), AWAIT);

    // then: both reconstruct the exact original row sequence, in order
    final List<Long> expected = LongStream.range(0, ROW_COUNT).boxed().toList();
    assertThat(factoryA.allAppendedValuesInOrder()).containsExactlyElementsOf(expected);
    assertThat(factoryB.allAppendedValuesInOrder()).containsExactlyElementsOf(expected);

    // and: each pipeline's descriptors cover the full offset range with no gaps or overlaps
    assertContiguousFullCoverage(sinkA.accepted, ROW_COUNT);
    assertContiguousFullCoverage(sinkB.accepted, ROW_COUNT);

    // and: the two pipelines really did chunk differently (this isn't a trivial single-file case
    // on both sides) — A never triggers a file boundary until shutdown, B does so repeatedly
    assertThat(sinkA.accepted).hasSize(1);
    assertThat(sinkB.accepted.size()).isGreaterThan(1);
  }

  private static void assertContiguousFullCoverage(
      final List<Descriptor> descriptors, final int rowCount) {
    final List<Descriptor> ordered =
        descriptors.stream().sorted(Comparator.comparingLong(Descriptor::firstOffset)).toList();
    assertThat(ordered.get(0).firstOffset()).isEqualTo(0L);
    assertThat(ordered.get(ordered.size() - 1).lastOffset()).isEqualTo(rowCount - 1L);
    for (int i = 1; i < ordered.size(); i++) {
      assertThat(ordered.get(i).firstOffset()).isEqualTo(ordered.get(i - 1).lastOffset() + 1);
    }
  }
}
