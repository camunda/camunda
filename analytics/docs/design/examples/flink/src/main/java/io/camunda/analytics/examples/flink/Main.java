/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.examples.flink;

import io.camunda.analytics.examples.flink.SourceEvent.EventType;
import java.time.Duration;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.CheckpointConfig.ExternalizedCheckpointCleanup;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * Entry point. Configures the runtime (checkpointing, state backend, exactly-once), builds a small
 * bounded source for illustration, attaches the watermark strategy, wires the graph via {@link
 * AnalyticsJob}, and runs it on the embedded MiniCluster.
 *
 * <p>Everything the runtime owns for correctness — checkpoint cadence, the RocksDB state backend,
 * and exactly-once mode — is configured here, in one place, and then applies to the whole graph.
 * That co-location is itself the point: in Flink these are runtime knobs, not things you thread
 * through your operators.
 */
public final class Main {

  private Main() {}

  public static void main(final String[] args) throws Exception {
    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

    // ========================================================================================
    // CROSS-CUTTING: checkpointing + exactly-once (framework-owned correctness)
    // ========================================================================================

    // Snapshot all keyed state (Stage A InstanceState + SLA timers, Stage B/C window accumulators)
    // every 60s using aligned checkpoint barriers.
    env.enableCheckpointing(Duration.ofSeconds(60).toMillis(), CheckpointingMode.EXACTLY_ONCE);

    // RocksDB keyed-state backend: out-of-core, incremental snapshots. This is what makes the
    // base-projection store scale past heap and recover cheaply — we don't manage it.
    env.setStateBackend(new EmbeddedRocksDBStateBackend(/* enableIncrementalCheckpointing= */ true));

    // Keep the last checkpoint on cancellation so the job can be resumed exactly where it stopped.
    env.getCheckpointConfig()
        .setExternalizedCheckpointCleanup(ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
    // Don't let checkpoints pile up if a snapshot runs long.
    env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
    env.getCheckpointConfig().setMinPauseBetweenCheckpoints(Duration.ofSeconds(10).toMillis());

    // ========================================================================================
    // SOURCE + WATERMARKS (event time)
    // ========================================================================================

    // In a real deployment this is a KafkaSource / a source over the engine event log, with the
    // source's committed offsets stored *inside* the checkpoint — that offset-in-checkpoint coupling
    // is the read side of exactly-once. Here we use a tiny in-memory sample so the graph is runnable.
    final DataStream<SourceEvent> source =
        env.fromElements(sampleEvents())
            .assignTimestampsAndWatermarks(
                WatermarkStrategy.<SourceEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                    // timestampMs is the event time that drives windows AND the SLA timers.
                    .withTimestampAssigner((event, recordTs) -> event.timestampMs()))
            .name("engine-event-source");

    // Forward-only gate: only count facts at/after this source offset (dataset activation point).
    final long activationOffset = 0L;

    AnalyticsJob.build(source, activationOffset);

    env.execute("process-instance-analytics (flink reference)");
  }

  /** A minimal, deterministic event sequence for a couple of instances. Illustration only. */
  private static SourceEvent[] sampleEvents() {
    final long t0 = Duration.ofHours(400_000).toMillis(); // arbitrary fixed epoch base
    final String p = "order-process";
    final String tenant = "<default>";

    return new SourceEvent[] {
      // instance 100: activate, set a var, complete after 90s -> a normal completion fact
      new SourceEvent(EventType.ACTIVATED, 100, p, tenant, t0, "start", null, null, 0, 10),
      new SourceEvent(EventType.VARIABLE, 100, p, tenant, t0 + 1_000, null, "amount", "42", 0, 11),
      new SourceEvent(EventType.COMPLETED, 100, p, tenant, t0 + 90_000, "end", null, null, 0, 12),

      // instance 101: activate, hit an incident, complete after 150s -> completion fact w/ incident
      new SourceEvent(EventType.ACTIVATED, 101, p, tenant, t0 + 5_000, "start", null, null, 0, 20),
      new SourceEvent(EventType.INCIDENT, 101, p, tenant, t0 + 30_000, "task-a", null, null, 0, 21),
      new SourceEvent(EventType.COMPLETED, 101, p, tenant, t0 + 155_000, "end", null, null, 0, 22),

      // instance 102: activate and never finish -> SLA breach fact fires at start + 5 min
      new SourceEvent(EventType.ACTIVATED, 102, p, tenant, t0 + 8_000, "start", null, null, 0, 30),

      // a late "heartbeat" that advances the watermark past instance 102's SLA deadline so the
      // event-time timer fires in this bounded run
      new SourceEvent(
          EventType.ACTIVATED, 999, p, tenant, t0 + Duration.ofMinutes(10).toMillis(),
          "start", null, null, 0, 40),
    };
  }
}
