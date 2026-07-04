/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.spark;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.apache.spark.api.java.function.FlatMapGroupsWithStateFunction;
import org.apache.spark.sql.streaming.GroupState;

/**
 * Stage A — the base projection (Model A). This is Spark's equivalent of "a Model A card plus a
 * timer": arbitrary stateful processing over a keyed stream.
 *
 * <p>REFERENCE EXAMPLE — not built, not wired into anything.
 *
 * <p>Spark hands us, per micro-batch and per key:
 *
 * <ul>
 *   <li>the {@code instanceKey} we grouped by,
 *   <li>an {@link Iterator} of all {@link SourceEvent}s for that key in this batch,
 *   <li>a {@link GroupState} handle to the durable per-key {@link InstanceState}.
 * </ul>
 *
 * <p><strong>What Spark owns:</strong> the state store (RocksDB or in-memory), its checkpointing and
 * replay, delivering events grouped by key, firing the event-time timeout once the watermark passes
 * the registered timestamp, and — with {@code OutputMode.Append()} + {@code EventTimeTimeout} —
 * garbage-collecting nothing on its own (eviction is explicit, see {@link GroupState#remove()}).
 *
 * <p><strong>What we hand-write (everything domain-specific):</strong> the state shape, every
 * transition rule, the derive step (building {@link CompletionFact}), the decision to evict, and the
 * SLA timer registration. Contrast this with a Kafka Streams {@code Transformer}/{@code
 * Processor} + punctuator, or a Flink {@code KeyedProcessFunction} + timer — same three
 * responsibilities (state, transitions, timers), different framework surface.
 */
public class BaseProjectionState
    implements FlatMapGroupsWithStateFunction<
        Long, SourceEvent, InstanceState, CompletionFact> {

  private static final long ONE_MINUTE_MS = 60_000L;

  /** SLA: an instance that has not reached a terminal state within 5 minutes is a breach. */
  private static final long SLA_MS = 5 * ONE_MINUTE_MS;

  @Override
  public Iterator<CompletionFact> call(
      final Long instanceKey,
      final Iterator<SourceEvent> events,
      final GroupState<InstanceState> state) {

    // --- Timeout path -------------------------------------------------------------------------
    // Spark set this flag because the event-time watermark advanced past the timeout timestamp we
    // registered below. The instance never reached a terminal state within its SLA. Emit a breach
    // fact and evict. On a timeout call, `events` is empty by contract.
    if (state.hasTimedOut()) {
      final InstanceState s = state.get();
      final CompletionFact breach =
          deriveFact(instanceKey, s, /* durationMs= */ SLA_MS, /* slaBreach= */ true);
      state.remove(); // explicit eviction — Spark does not drop the key for us
      return Collections.singletonList(breach).iterator();
    }

    // --- Normal path --------------------------------------------------------------------------
    InstanceState s = state.exists() ? state.get() : null;
    final List<CompletionFact> out = new ArrayList<>();
    boolean evicted = false;

    // NOTE: within a single micro-batch Spark does not guarantee these events are ordered by event
    // time. For this reference we apply them in arrival order and assume a terminal event, if
    // present, is the last meaningful one. A production build would sort by timestampMs (or fold
    // ACTIVATED before terminal explicitly) to be robust to out-of-order delivery inside a batch.
    while (events.hasNext()) {
      final SourceEvent e = events.next();

      switch (e.getType()) {
        case ACTIVATED -> {
          if (s == null) {
            s = new InstanceState();
            s.setStartMs(e.getTimestampMs());
          }
          // processId/tenantId are stamped on every event; capture them onto the state so the
          // derived fact has them even if the terminal event omits them.
          s.setStartMs(s.getStartMs() == 0 ? e.getTimestampMs() : s.getStartMs());
        }
        case VARIABLE -> {
          if (s == null) {
            s = new InstanceState(); // tolerate a variable arriving before ACTIVATED in-batch
          }
          if (e.getVarName() != null) {
            s.getVars().put(e.getVarName(), e.getVarValue());
          }
        }
        case INCIDENT -> {
          if (s == null) {
            s = new InstanceState();
          }
          s.setHadIncident(true);
        }
        case COMPLETED, TERMINATED -> {
          if (s == null) {
            // A terminal event with no prior state — nothing sensible to derive; skip.
            break;
          }
          s.setEndMs(e.getTimestampMs());
          s.setStatus(
              e.getType() == SourceEvent.Type.COMPLETED
                  ? InstanceState.Status.COMPLETED
                  : InstanceState.Status.TERMINATED);
          final long durationMs = Math.max(0, s.getEndMs() - s.getStartMs());
          out.add(deriveFactFromEvent(e, s, durationMs));
          state.remove(); // terminal reached → derive + evict
          evicted = true;
        }
      }
      if (evicted) {
        break; // ignore any trailing events for an already-terminated instance
      }
    }

    if (!evicted && s != null) {
      state.update(s);
      // (Re)register the SLA timer relative to the start time. With EventTimeTimeout the timeout
      // fires when the watermark crosses this timestamp — no wall-clock involved.
      state.setTimeoutTimestamp(s.getStartMs() + SLA_MS);
    }

    return out.iterator();
  }

  /** Derive a fact from a real terminal event (carries the origin coordinate + processId/tenant). */
  private static CompletionFact deriveFactFromEvent(
      final SourceEvent terminal, final InstanceState s, final long durationMs) {
    final CompletionFact f = baseFact(s, durationMs, /* slaBreach= */ false);
    f.setProcessId(terminal.getProcessId());
    f.setTenantId(terminal.getTenantId());
    f.setSourcePartition(terminal.getSourcePartition());
    f.setSourceOffset(terminal.getSourceOffset());
    return f;
  }

  /** Derive a fact from state alone (SLA breach path — no terminal event exists). */
  private static CompletionFact deriveFact(
      final Long instanceKey,
      final InstanceState s,
      final long durationMs,
      final boolean slaBreach) {
    final CompletionFact f = baseFact(s, durationMs, slaBreach);
    // processId/tenant/origin are best-effort here; a real build would persist them on the state.
    f.setProcessId("unknown-" + instanceKey);
    f.setTenantId("<unknown>");
    return f;
  }

  private static CompletionFact baseFact(
      final InstanceState s, final long durationMs, final boolean slaBreach) {
    final long startWindowMs = floorToMinute(s.getStartMs());
    final CompletionFact f = new CompletionFact();
    f.setStartWindowMs(startWindowMs);
    f.setStartWindow(new Timestamp(startWindowMs));
    f.setDurationMs(durationMs);
    f.setHadIncident(s.isHadIncident());
    f.setSlaBreach(slaBreach);
    return f;
  }

  private static long floorToMinute(final long ms) {
    return ms - Math.floorMod(ms, ONE_MINUTE_MS);
  }
}
