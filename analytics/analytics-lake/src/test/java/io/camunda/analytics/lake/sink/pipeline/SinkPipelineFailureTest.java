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
 * An encoder failure must abort every open file, mark the pipeline terminally failed, emit no
 * descriptor for the broken window, and never wedge shutdown.
 */
class SinkPipelineFailureTest {

  private static final Duration AWAIT = Duration.ofSeconds(5);
  private static final long NEVER_MS = 1_000_000_000L;

  @Test
  void shouldAbortAllAndMarkFailedWithoutEmittingADescriptorWhenAnEncoderThrows() {
    // given: the very first encoder ever opened is configured to throw on append
    final TableSchema schema = TestPipelines.schema("t-failure");
    final SinkConfig config = new SinkConfig(2, 3, NEVER_MS, NEVER_MS, 0, schema.table());
    final Segment[] segments =
        TestPipelines.newSegments(schema, config.ringSegments(), config.segmentRows());
    final FakeDescriptorSink sink = new FakeDescriptorSink();
    final FakeBatchEncoderFactory factory = new FakeBatchEncoderFactory();
    factory.throwOnAppendForNextEncoders.set(true);
    final SinkPipeline pipeline =
        new SinkPipeline(
            config,
            segments,
            new FakeBackpressureGate(),
            TestPipelines.identitySorter(),
            factory,
            sink,
            List.of(),
            new FakeClock(0),
            new SimpleMeterRegistry());
    pipeline.start();

    // when: a SEGMENT_FULL seal drives the flush thread into the encoder that throws
    TestPipelines.tryAppendRow(pipeline.ring(), 0L);
    TestPipelines.tryAppendRow(pipeline.ring(), 1L);
    pipeline.onPollTick(1L, 42L);

    // then: the pipeline is terminally failed, with the thrown exception recorded
    TestWaits.awaitTrue(pipeline::isFailed, AWAIT);
    assertThat(pipeline.failureCause())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("synthetic encoder append failure");

    // and: no descriptor was ever emitted for the broken window
    assertThat(sink.accepted).isEmpty();

    // and: the encoder that threw was aborted, never finished
    assertThat(factory.created).hasSize(1);
    assertThat(factory.created.get(0).aborted).isTrue();
    assertThat(factory.created.get(0).finished).isFalse();

    // and: further ticks are a harmless no-op — the translator is expected to stop on isFailed()
    pipeline.onPollTick(2L, 43L);

    // and: close() does not wedge even though the flush thread already terminated on its own
    pipeline.close();
    assertThat(sink.accepted).isEmpty();
  }
}
