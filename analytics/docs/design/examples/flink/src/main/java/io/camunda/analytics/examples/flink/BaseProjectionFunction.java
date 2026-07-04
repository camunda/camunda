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
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

/**
 * Stage A — the base projection ("Model A"), keyed by {@code instanceKey}.
 *
 * <p>This is the piece that maps most directly onto our hand-rolled design, so it is worth reading
 * closely. It is a {@link KeyedProcessFunction}: for a given instance key, Flink guarantees that
 * every event is delivered to the <em>same</em> parallel subtask, in order per key, with exclusive
 * access to that key's state. That is precisely the "one owner per key" guarantee we build by hand
 * with per-partition tasks and a sharded base-projection store.
 *
 * <p>What Flink owns here:
 *
 * <ul>
 *   <li><b>The base-projection store.</b> {@link ValueState}&lt;{@link InstanceState}&gt; is a
 *       keyed, RocksDB-backed, checkpointed store. We do not manage RocksDB column families,
 *       snapshots, or recovery — the state backend does.
 *   <li><b>Eviction.</b> {@code state.clear()} removes the instance from the store. (An alternative
 *       time-based eviction via {@code StateTtlConfig} is shown, commented, in {@link #open}.)
 *   <li><b>Event-time timers.</b> {@code registerEventTimeTimer} / {@code onTimer} give us the SLA
 *       deadline for free, fired by watermark progress — no manual timer wheel.
 * </ul>
 *
 * <p>What we still hand-write: the state machine itself (how ACTIVATED/VARIABLE/INCIDENT/terminal
 * events mutate {@link InstanceState}) and the derivation of the {@link CompletionFact}. Flink owns
 * the plumbing; the domain projection is ours.
 */
public final class BaseProjectionFunction
    extends KeyedProcessFunction<Long, SourceEvent, CompletionFact> {

  /** SLA deadline measured from the instance start. If still open at start+SLA, we emit a breach. */
  static final Duration SLA = Duration.ofMinutes(5);

  /** Window stride for {@code startWindowMs} — floor the start to a 1-minute bucket. */
  private static final long WINDOW_STRIDE_MS = Duration.ofMinutes(1).toMillis();

  /**
   * Handle to the keyed base-projection store. One logical {@link InstanceState} per instanceKey;
   * Flink scopes every access in {@link #processElement}/{@link #onTimer} to the current key
   * automatically.
   */
  private transient ValueState<InstanceState> state;

  @Override
  public void open(final Configuration parameters) {
    final ValueStateDescriptor<InstanceState> descriptor =
        new ValueStateDescriptor<>("instance-state", InstanceState.class);

    // --- Alternative eviction strategy (commented): time-to-live -----------------------------
    // Instead of (or in addition to) the explicit state.clear() on completion, Flink can expire
    // idle keys for you. This is the framework-native equivalent of a "reaper" for instances that
    // never reach a terminal event and never breach (e.g. lost/rewound sources):
    //
    //   final StateTtlConfig ttl =
    //       StateTtlConfig.newBuilder(Time.hours(24))
    //           .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
    //           .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
    //           .cleanupInRocksdbCompactFilter(1000)
    //           .build();
    //   descriptor.enableTimeToLive(ttl);
    // -----------------------------------------------------------------------------------------

    state = getRuntimeContext().getState(descriptor);
  }

  @Override
  public void processElement(
      final SourceEvent event,
      final KeyedProcessFunction<Long, SourceEvent, CompletionFact>.Context ctx,
      final Collector<CompletionFact> out)
      throws Exception {

    InstanceState instance = state.value();

    switch (event.type()) {
      case ACTIVATED -> {
        // First activation opens the instance and arms the SLA timer. Later duplicate
        // ACTIVATED events (e.g. a source replay) are idempotent: we keep the earliest start.
        if (instance == null) {
          instance = new InstanceState();
          instance.processId(event.processId());
          instance.tenantId(event.tenantId());
          instance.startMs(event.timestampMs());
          instance.status(InstanceState.Status.OPEN);

          // Register the event-time SLA deadline. Flink fires onTimer when the watermark passes
          // this timestamp — i.e. on *event time*, not wall clock. The timer is keyed state too,
          // so it is checkpointed and survives failover.
          ctx.timerService().registerEventTimeTimer(slaDeadline(instance));
        }
      }

      case VARIABLE -> {
        if (instance != null && instance.isOpen() && event.varName() != null) {
          instance.putVar(event.varName(), event.varValue());
        }
      }

      case INCIDENT -> {
        if (instance != null && instance.isOpen()) {
          instance.hadIncident(true);
        }
      }

      case COMPLETED, TERMINATED -> {
        if (instance != null && instance.isOpen()) {
          instance.endMs(event.timestampMs());
          instance.status(
              event.type() == EventType.COMPLETED
                  ? InstanceState.Status.COMPLETED
                  : InstanceState.Status.TERMINATED);

          // Derive the fact and emit it downstream.
          out.collect(deriveFact(instance, event, /* slaBreach= */ false));

          // The instance is done: cancel its SLA timer and evict it from the store.
          ctx.timerService().deleteEventTimeTimer(slaDeadline(instance));
          state.clear();
          return; // nothing to write back — state is cleared
        }
      }

      default -> {
        // ignore unknown types
      }
    }

    // Read-modify-write back into the keyed store (skipped on the terminal path above).
    if (instance != null) {
      state.update(instance);
    }
  }

  @Override
  public void onTimer(
      final long timestamp,
      final KeyedProcessFunction<Long, SourceEvent, CompletionFact>.OnTimerContext ctx,
      final Collector<CompletionFact> out)
      throws Exception {

    final InstanceState instance = state.value();

    // The timer fires on watermark progress. If the instance is still open when its SLA deadline
    // passes in event time, emit a breach fact and evict. If it already completed, the timer was
    // deleted on the terminal path, so we normally never get here for a closed instance — the
    // guard keeps us safe against races on restore.
    if (instance != null && instance.isOpen()) {
      instance.endMs(timestamp); // elapsed == SLA at the deadline
      out.collect(deriveFact(instance, /* terminalEvent= */ null, /* slaBreach= */ true));
      state.clear();
    }
  }

  // --- helpers --------------------------------------------------------------------------------

  private long slaDeadline(final InstanceState instance) {
    return instance.startMs() + SLA.toMillis();
  }

  /**
   * Build the derived fact from the accumulated projection. {@code terminalEvent} carries the
   * source coordinate for a natural completion; for an SLA breach there is no triggering event, so
   * the coordinate falls back to zeros (breach facts are filtered out of Stage B anyway). Identity
   * fields (processId/tenantId) come from the projection, so both paths are well-formed.
   */
  private CompletionFact deriveFact(
      final InstanceState instance, final SourceEvent terminalEvent, final boolean slaBreach) {

    final long startWindowMs = Math.floorDiv(instance.startMs(), WINDOW_STRIDE_MS) * WINDOW_STRIDE_MS;
    final long durationMs = Math.max(0, instance.endMs() - instance.startMs());

    final int sourcePartition = terminalEvent != null ? terminalEvent.sourcePartition() : 0;
    final long sourceOffset = terminalEvent != null ? terminalEvent.sourceOffset() : 0L;

    return new CompletionFact(
        instance.processId(),
        instance.tenantId(),
        startWindowMs,
        durationMs,
        instance.hadIncident(),
        slaBreach,
        sourcePartition,
        sourceOffset);
  }
}
