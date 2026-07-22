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
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Orderly shutdown must drain the filling segment and emit a final descriptor; an empty pipeline
 * must close cleanly without ever touching the descriptor sink.
 */
class SinkPipelineShutdownTest {

  private static final long NEVER_MS = 1_000_000_000L;

  @Test
  void shouldSealShutdownFlushAndEmitAFinalDescriptorOnClose() {
    // given: a pipeline where only close() can ever trigger a boundary
    final TableSchema schema = TestPipelines.schema("t-shutdown-drain");
    final SinkConfig config = new SinkConfig(100, 2, NEVER_MS, NEVER_MS, 7, schema.table());
    final Segment[] segments =
        TestPipelines.newSegments(schema, config.ringSegments(), config.segmentRows());
    final FakeDescriptorSink sink = new FakeDescriptorSink();
    final FakeBatchEncoderFactory factory = new FakeBatchEncoderFactory();
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

    // when: rows sit in the filling segment (never full, never time/size triggered) and close()
    // is called
    for (long value = 0; value < 6; value++) {
      TestPipelines.tryAppendRow(pipeline.ring(), value);
      pipeline.onPollTick(value, 42L);
    }
    pipeline.close();

    // then: the flush thread joined (close() returned) and a single final descriptor was emitted
    assertThat(sink.accepted).hasSize(1);
    assertThat(sink.accepted.get(0).firstOffset()).isEqualTo(0L);
    assertThat(sink.accepted.get(0).lastOffset()).isEqualTo(5L);
    assertThat(sink.accepted.get(0).sourcePartition()).isEqualTo(7);
    assertThat(factory.allAppendedValuesInOrder()).containsExactly(0L, 1L, 2L, 3L, 4L, 5L);
  }

  @Test
  void shouldCloseCleanlyAndEmitNothingOnAPipelineThatNeverReceivedARow() {
    // given: a pipeline that is started but never fed any row
    final TableSchema schema = TestPipelines.schema("t-empty-close");
    final SinkConfig config = new SinkConfig(10, 2, NEVER_MS, NEVER_MS, 0, schema.table());
    final Segment[] segments =
        TestPipelines.newSegments(schema, config.ringSegments(), config.segmentRows());
    final FakeDescriptorSink sink = new FakeDescriptorSink();
    final FakeBatchEncoderFactory factory = new FakeBatchEncoderFactory();
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

    // when
    pipeline.close();

    // then: clean, no descriptor, no file ever opened
    assertThat(sink.accepted).isEmpty();
    assertThat(factory.created).isEmpty();

    // and: idempotent
    pipeline.close();
    assertThat(sink.accepted).isEmpty();
  }
}
