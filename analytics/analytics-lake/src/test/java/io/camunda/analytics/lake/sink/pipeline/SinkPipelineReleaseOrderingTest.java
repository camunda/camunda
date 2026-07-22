/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.TableSchema;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The load-bearing property from {@link FlushLoop}'s class javadoc: the segment that triggers a
 * file boundary must stay un-released for as long as {@code DescriptorSink.accept} is outstanding.
 * Verified behaviorally, not by inspecting private state: while {@code accept} is latched open, the
 * ring must look and behave exactly as if that segment's slot were still occupied — a second
 * boundary attempt must hit genuine backpressure — and only once {@code accept} returns does the
 * slot free up and the next descriptor follow.
 */
class SinkPipelineReleaseOrderingTest {

  private static final Duration AWAIT = Duration.ofSeconds(5);

  @Test
  void shouldHoldTheBoundarySegmentUnreleasedUntilTheDescriptorSinkAccepts()
      throws InterruptedException {
    // given: a 2-segment ring (the minimum) and a descriptor sink armed to block its next accept
    final TableSchema schema = TestPipelines.schema("t-release-order");
    final SinkConfig config = new SinkConfig(100, 2, 100L, 1_000_000_000L, 0, schema.table());
    final Segment[] segments =
        TestPipelines.newSegments(schema, config.ringSegments(), config.segmentRows());
    final FakeBackpressureGate gate = new FakeBackpressureGate();
    final FakeDescriptorSink sink = new FakeDescriptorSink();
    final FakeBatchEncoderFactory factory = new FakeBatchEncoderFactory();
    final FakeClock clock = new FakeClock(0L);
    final SinkPipeline pipeline =
        new SinkPipeline(
            config,
            segments,
            gate,
            TestPipelines.identitySorter(),
            factory,
            sink,
            List.of(),
            clock,
            new SimpleMeterRegistry());
    pipeline.start();
    sink.armBlockingNextAccept();

    // when: a TIME_DUE boundary seals the filling segment; the flush thread reaches accept() and
    // blocks there
    TestPipelines.tryAppendRow(pipeline.ring(), 0L);
    pipeline.onPollTick(0L, 1L);
    clock.advance(200L);
    pipeline.onPollTick(0L, 1L);
    assertThat(sink.awaitAcceptEntered(AWAIT)).isTrue();

    // then: the triggering segment is still held — proven by forcing a second boundary attempt,
    // which must hit genuine ring-full backpressure (not just an unasserted internal flag)
    TestPipelines.tryAppendRow(pipeline.ring(), 1L);
    clock.advance(200L);
    pipeline.onPollTick(1L, 2L);
    TestWaits.awaitTrue(() -> gate.pauseCount.get() == 1, AWAIT);
    assertThat(sink.accepted).isEmpty();

    // when: the descriptor sink's accept() returns
    sink.releaseBlockedAccept();
    TestWaits.awaitTrue(() -> sink.accepted.size() == 1, AWAIT);

    // then: only now is the slot freed — the gate resumed, and the second boundary (which had
    // failed) can now succeed and eventually produce its own descriptor
    TestWaits.awaitTrue(() -> gate.resumeCount.get() == 1, AWAIT);
    clock.advance(200L);
    pipeline.onPollTick(1L, 2L);
    TestWaits.awaitTrue(() -> sink.accepted.size() == 2, AWAIT);

    assertThat(sink.accepted.get(0).firstOffset()).isEqualTo(0L);
    assertThat(sink.accepted.get(1).firstOffset()).isEqualTo(1L);

    pipeline.close();
  }
}
