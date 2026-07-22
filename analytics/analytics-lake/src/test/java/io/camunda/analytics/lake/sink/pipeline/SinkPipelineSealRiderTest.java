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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link io.camunda.analytics.lake.sink.SealRider#onSealed} must run once per sealed segment,
 * strictly before any encoder append for that same segment.
 */
class SinkPipelineSealRiderTest {

  private static final Duration AWAIT = Duration.ofSeconds(5);
  private static final long NEVER_MS = 1_000_000_000L;

  @Test
  void shouldCallSealRiderOncePerSegmentBeforeAnyEncoderAppendForIt() {
    // given: a shared log so the relative order of rider vs. encoder calls is observable
    final List<String> log = new ArrayList<>();
    final TableSchema schema = TestPipelines.schema("t-rider-order");
    final SinkConfig config = new SinkConfig(2, 3, NEVER_MS, NEVER_MS, 0, schema.table());
    final Segment[] segments =
        TestPipelines.newSegments(schema, config.ringSegments(), config.segmentRows());
    final FakeDescriptorSink sink = new FakeDescriptorSink();
    final FakeBatchEncoderFactory factory = new FakeBatchEncoderFactory(log);
    final FakeSealRider rider = new FakeSealRider(log);
    final SinkPipeline pipeline =
        new SinkPipeline(
            config,
            segments,
            new FakeBackpressureGate(),
            TestPipelines.identitySorter(),
            factory,
            sink,
            List.of(rider),
            new FakeClock(0),
            new SimpleMeterRegistry());
    pipeline.start();

    // when: two segments are each filled and sealed (SEGMENT_FULL)
    for (long value = 0; value < 4; value++) {
      TestPipelines.tryAppendRow(pipeline.ring(), value);
    }
    TestWaits.awaitTrue(() -> rider.callCount.get() == 2, AWAIT);

    // then: the rider was called exactly once per sealed segment, with the right sizes
    assertThat(rider.callCount.get()).isEqualTo(2);
    assertThat(rider.sealedSizes).containsExactly(2, 2);

    // and: each segment's rider call precedes its encoder append — same-day rows reuse the one
    // open file, so only the first segment's processing opens it
    assertThat(log)
        .containsExactly(
            "rider(size=2)",
            "open(day=" + TestPipelines.EPOCH_DAY + ")",
            "append(file=1,rows=2)",
            "rider(size=2)",
            "append(file=1,rows=2)");

    pipeline.close();
  }
}
