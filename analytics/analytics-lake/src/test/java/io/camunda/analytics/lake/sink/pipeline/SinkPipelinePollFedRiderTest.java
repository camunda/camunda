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
 * End-to-end (within this package) proof of the poll-fed rider alignment mechanism {@link
 * SinkPipeline}/{@link FlushLoop} implement via {@code SealRider#onPollBoundary}/{@code
 * #rollbackPollBoundary}/{@code #hasPendingPollFedData} — see those methods' own javadoc for the
 * full contract. Uses {@link FakePollFedRider} rather than the real {@code
 * io.camunda.analytics.lake.metrics.PollFedRider} so this test stays in the sink package and needs
 * no declaration/algebra machinery.
 */
class SinkPipelinePollFedRiderTest {

  private static final Duration AWAIT = Duration.ofSeconds(5);
  private static final long NEVER_MS = 1_000_000_000L;

  @Test
  void shouldFreezeOnlyFoldsBeforeATimeDueBoundaryAndDrainAfterFoldsInTheNextWindow() {
    // given: TIME_DUE is the only trigger that can ever fire
    final TableSchema schema = TestPipelines.schema("t-pollfed-time-due");
    final SinkConfig config = new SinkConfig(100, 2, 100L, NEVER_MS, 0, schema.table());
    final Segment[] segments =
        TestPipelines.newSegments(schema, config.ringSegments(), config.segmentRows());
    final FakeBackpressureGate gate = new FakeBackpressureGate();
    final FakeDescriptorSink sink = new FakeDescriptorSink();
    final FakeBatchEncoderFactory factory = new FakeBatchEncoderFactory();
    final FakeClock clock = new FakeClock(0L);
    final FakePollFedRider rider = new FakePollFedRider();
    final SinkPipeline pipeline =
        new SinkPipeline(
            config,
            segments,
            gate,
            TestPipelines.identitySorter(),
            factory,
            sink,
            List.of(rider),
            clock,
            new SimpleMeterRegistry());
    pipeline.start();

    // when: a raw row is fed (onPollTick's boundary check requires a non-empty filling segment)
    // together with 3 poll-fed folds, all strictly before the boundary tick
    TestPipelines.tryAppendRow(pipeline.ring(), 0L);
    rider.fold();
    rider.fold();
    rider.fold();
    pipeline.onPollTick(0L, 500L);
    assertThat(sink.accepted).isEmpty();

    // and: 2 more poll-fed folds happen strictly AFTER the boundary tick that seals the window
    clock.advance(150L);
    pipeline.onPollTick(0L, 500L); // TIME_DUE fires here -- rider swaps at this exact moment
    rider.fold();
    rider.fold();

    // then: the first window's drain sees exactly the 3 pre-boundary folds, never the 2 after
    TestWaits.awaitTrue(() -> sink.accepted.size() == 1, AWAIT);
    assertThat(rider.drainedSoFar()).containsExactly(3);

    // when: a second raw row plus a second TIME_DUE boundary closes the second window
    TestPipelines.tryAppendRow(pipeline.ring(), 1L);
    pipeline.onPollTick(1L, 600L);
    clock.advance(150L);
    pipeline.onPollTick(1L, 600L);
    TestWaits.awaitTrue(() -> sink.accepted.size() == 2, AWAIT);

    // then: the second window's drain sees exactly the 2 after-boundary folds from before, with
    // nothing from the first window leaking into it
    assertThat(rider.drainedSoFar()).containsExactly(3, 2);

    pipeline.close();
  }

  @Test
  void shouldRollBackTheSwapWhenARealRingFullSealAttemptFails() throws InterruptedException {
    // given: a 2-segment ring (the minimum) stalled full via SinkPipelineBackpressureTest's own
    // technique, so a genuine TIME_DUE seal attempt fails deterministically rather than racing
    final TableSchema schema = TestPipelines.schema("t-pollfed-rollback");
    final SinkConfig config = new SinkConfig(1, 2, 10L, NEVER_MS, 0, schema.table());
    final Segment[] segments =
        TestPipelines.newSegments(schema, config.ringSegments(), config.segmentRows());
    final FakeBackpressureGate gate = new FakeBackpressureGate();
    final FakeDescriptorSink sink = new FakeDescriptorSink();
    final FakeBatchEncoderFactory factory = new FakeBatchEncoderFactory();
    final BlockingFirstCallSorter sorter = new BlockingFirstCallSorter(TestPipelines.EPOCH_DAY);
    final FakeClock clock = new FakeClock(0L);
    final FakePollFedRider rider = new FakePollFedRider();
    final SinkPipeline pipeline =
        new SinkPipeline(
            config,
            segments,
            gate,
            sorter,
            factory,
            sink,
            List.of(rider),
            clock,
            new SimpleMeterRegistry());
    pipeline.start();

    // fill+seal segment A (SEGMENT_FULL); the flush thread takes it and stalls inside the sorter
    assertThat(TestPipelines.tryAppendRow(pipeline.ring(), 0L)).isTrue();
    assertThat(sorter.awaitEntered(AWAIT)).isTrue();

    // one poll-fed fold, then fill segment B too -- its own SEGMENT_FULL seal attempt fails (the
    // ring is now genuinely full), leaving B as the still-full filling segment
    rider.fold();
    assertThat(TestPipelines.tryAppendRow(pipeline.ring(), 1L)).isTrue();
    assertThat(pipeline.ring().sealedCount()).isEqualTo(1);

    // when: the clock crosses flushIntervalMs and a tick attempts TIME_DUE -- it also fails (ring
    // still full), so trySeal()'s own rollback must undo the speculative swap
    clock.advance(50L);
    pipeline.onPollTick(1L, 500L);

    // then: nothing was drained for that failed attempt, and a further fold lands back in the
    // SAME (restored) active window, not a lost/orphaned one
    assertThat(rider.drainedSoFar()).isEmpty();
    rider.fold();

    // when: the flush thread is released, frees a slot, and a later retick's TIME_DUE succeeds
    sorter.release();
    TestWaits.awaitTrue(() -> gate.resumeCount.get() == 1, AWAIT);
    clock.advance(50L);
    pipeline.onPollTick(1L, 500L);
    TestWaits.awaitTrue(() -> sink.accepted.size() == 1, AWAIT);

    // then: the real boundary's window drains BOTH folds together -- proof the rollback restored
    // the active window rather than losing or duplicating the first fold
    assertThat(rider.drainedSoFar()).containsExactly(2);

    pipeline.close();
  }

  @Test
  void shouldDrainPollFedDataAtShutdownEvenWhenTheRawWindowNeverGotARow() {
    // given: TIME_DUE could fire, but the raw ring never receives a single row this whole test --
    // onPollTick's own "never seal an empty segment" rule means no boundary check ever runs for
    // it, so only poll-fed data exists to flush at shutdown
    final TableSchema schema = TestPipelines.schema("t-pollfed-shutdown-only");
    final SinkConfig config = new SinkConfig(100, 2, 50L, NEVER_MS, 0, schema.table());
    final Segment[] segments =
        TestPipelines.newSegments(schema, config.ringSegments(), config.segmentRows());
    final FakeBackpressureGate gate = new FakeBackpressureGate();
    final FakeDescriptorSink sink = new FakeDescriptorSink();
    final FakeBatchEncoderFactory factory = new FakeBatchEncoderFactory();
    final FakeClock clock = new FakeClock(0L);
    final FakePollFedRider rider = new FakePollFedRider();
    final SinkPipeline pipeline =
        new SinkPipeline(
            config,
            segments,
            gate,
            TestPipelines.identitySorter(),
            factory,
            sink,
            List.of(rider),
            clock,
            new SimpleMeterRegistry());
    pipeline.start();

    // when: only poll-fed folds happen -- no raw row ever, so ordinary onPollTick ticks are all
    // no-ops for boundary purposes
    rider.fold();
    rider.fold();
    rider.fold();
    clock.advance(100L);
    pipeline.onPollTick(-1L, 500L); // never seals anything: ring.filling().size() == 0

    // when: the pipeline shuts down
    pipeline.close();

    // then: the poll-fed data was still drained (FlushLoop#drainShutdown's own
    // hasPendingPollFedData fallback) -- one descriptor is still committed to carry the rider's
    // derived files (a real PollFedRider would produce a non-empty derivedFiles map here; this
    // fake produces none, so only the empty raw file list is observable), even though the raw
    // window itself never received a single row
    assertThat(rider.drainedSoFar()).containsExactly(3);
    assertThat(sink.accepted).hasSize(1);
    assertThat(sink.accepted.get(0).files()).isEmpty();
  }
}
