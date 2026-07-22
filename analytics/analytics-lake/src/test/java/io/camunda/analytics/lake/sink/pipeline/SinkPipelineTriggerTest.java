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
 * Covers the seal-trigger matrix: SEGMENT_FULL cascading, TIME_DUE file boundaries, and the "never
 * seal an empty segment" rule.
 */
class SinkPipelineTriggerTest {

  private static final Duration AWAIT = Duration.ofSeconds(5);
  private static final long NEVER_MS = 1_000_000_000L;

  @Test
  void shouldCascadeSegmentFullSealsWithoutOpeningAFileBoundary() {
    // given: a config where only SEGMENT_FULL can ever fire (huge interval/size targets)
    final TableSchema schema = TestPipelines.schema("t-segment-full");
    final SinkConfig config = new SinkConfig(3, 4, NEVER_MS, NEVER_MS, 0, schema.table());
    final Segment[] segments =
        TestPipelines.newSegments(schema, config.ringSegments(), config.segmentRows());
    final FakeBackpressureGate gate = new FakeBackpressureGate();
    final FakeDescriptorSink sink = new FakeDescriptorSink();
    final FakeBatchEncoderFactory factory = new FakeBatchEncoderFactory();
    final SinkPipeline pipeline =
        new SinkPipeline(
            config,
            segments,
            gate,
            TestPipelines.identitySorter(),
            factory,
            sink,
            List.of(),
            new FakeClock(0),
            new SimpleMeterRegistry());
    pipeline.start();

    // when: 9 rows are fed — exactly three SEGMENT_FULL cascades of 3 rows each
    for (long value = 0; value < 9; value++) {
      assertThat(TestPipelines.tryAppendRow(pipeline.ring(), value)).isTrue();
      pipeline.onPollTick(value, 1000L + value);
    }
    TestWaits.awaitTrue(() -> factory.allAppendedValuesInOrder().size() == 9, AWAIT);

    // then: rows landed in the (still open) window's encoder, but no file boundary ever closed it
    assertThat(sink.accepted).isEmpty();
    assertThat(gate.pauseCount.get()).isZero();

    // when: shutdown drains the still-open window (no SHUTDOWN seal needed — filling is empty)
    pipeline.close();

    // then: exactly one descriptor, covering every row fed
    assertThat(sink.accepted).hasSize(1);
    assertThat(sink.accepted.get(0).firstOffset()).isEqualTo(0L);
    assertThat(sink.accepted.get(0).lastOffset()).isEqualTo(8L);
    assertThat(factory.allAppendedValuesInOrder())
        .containsExactly(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
  }

  @Test
  void shouldSealOnTimeDueAndEmitExactlyOneDescriptorCoveringRowsSincePreviousDescriptor() {
    // given: SEGMENT_FULL can never fire (segmentRows huge); only TIME_DUE can seal
    final TableSchema schema = TestPipelines.schema("t-time-due");
    final SinkConfig config = new SinkConfig(100, 2, 100L, NEVER_MS, 0, schema.table());
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

    // when: 5 rows fed without the clock moving — no TIME_DUE yet
    for (long value = 0; value < 5; value++) {
      TestPipelines.tryAppendRow(pipeline.ring(), value);
      pipeline.onPollTick(value, 500L);
    }
    assertThat(sink.accepted).isEmpty();

    // when: the clock crosses the flush interval and a tick re-checks the trigger
    clock.advance(150L);
    pipeline.onPollTick(4L, 500L);
    TestWaits.awaitTrue(() -> sink.accepted.size() == 1, AWAIT);

    // then: one descriptor covering exactly the rows fed so far
    assertThat(sink.accepted.get(0).firstOffset()).isEqualTo(0L);
    assertThat(sink.accepted.get(0).lastOffset()).isEqualTo(4L);

    // when: 3 more rows are fed and a second TIME_DUE fires
    for (long value = 5; value < 8; value++) {
      TestPipelines.tryAppendRow(pipeline.ring(), value);
      pipeline.onPollTick(value, 600L);
    }
    clock.advance(150L);
    pipeline.onPollTick(7L, 600L);
    TestWaits.awaitTrue(() -> sink.accepted.size() == 2, AWAIT);

    // then: the second descriptor covers only the rows since the first descriptor
    assertThat(sink.accepted.get(1).firstOffset()).isEqualTo(5L);
    assertThat(sink.accepted.get(1).lastOffset()).isEqualTo(7L);

    pipeline.close();
  }

  @Test
  void shouldNotSealOrEmitADescriptorWhenTimeDueFiresOnAnEmptyFillingSegment() {
    // given: no rows ever fed
    final TableSchema schema = TestPipelines.schema("t-empty-time-due");
    final SinkConfig config = new SinkConfig(10, 2, 50L, NEVER_MS, 0, schema.table());
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

    // when: the clock crosses the flush interval, and a tick runs with an empty filling segment
    clock.advance(100L);
    pipeline.onPollTick(0L, 500L);

    // then: nothing was sealed, nothing was emitted
    assertThat(pipeline.ring().sealedCount()).isZero();
    assertThat(sink.accepted).isEmpty();
    assertThat(factory.created).isEmpty();

    pipeline.close();
    assertThat(sink.accepted).isEmpty();
  }
}
