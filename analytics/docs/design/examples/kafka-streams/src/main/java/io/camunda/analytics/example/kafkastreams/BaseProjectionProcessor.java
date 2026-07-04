/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.kafkastreams;

import java.time.Duration;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage A — the base projection, written with the low-level Processor API.
 *
 * <p>This is the part you <b>hand-write</b>: the event fold, the derivation of the completion fact,
 * the eviction, and the SLA timer. Everything around it — feeding records in on the right key,
 * keeping the {@link KeyValueStore} durable and restoring it after a crash, calling our punctuator on
 * a schedule, and forwarding downstream — is owned by Kafka Streams.
 *
 * <p>Input key = {@code instanceKey} (the stream is keyed by instance so all events for one instance
 * land on the same task and the same store shard). Output key is also {@code instanceKey}; the
 * repartition onto {@code processId} happens later in the DSL via {@code selectKey}, so that the
 * shuffle is visible as its own topology step.
 */
public class BaseProjectionProcessor implements Processor<Long, SourceEvent, Long, CompletionFact> {

  private static final Logger LOG = LoggerFactory.getLogger(BaseProjectionProcessor.class);

  /** SLA: if an instance is still OPEN 5 minutes (event time) after it started, it breaches. */
  static final Duration SLA = Duration.ofMinutes(5);

  private static final Duration ONE_MINUTE_MS = Duration.ofMinutes(1);

  private final String storeName;

  private ProcessorContext<Long, CompletionFact> context;
  private KeyValueStore<Long, InstanceState> store;

  public BaseProjectionProcessor(final String storeName) {
    this.storeName = storeName;
  }

  @Override
  public void init(final ProcessorContext<Long, CompletionFact> context) {
    this.context = context;
    this.store = context.getStateStore(storeName);

    // --- SLA timer ---------------------------------------------------------------------------
    // Kafka Streams has NO per-key event-time timer API (unlike Flink's onTimer). The idiomatic
    // pattern is a PERIODIC PUNCTUATOR that scans the store for overdue keys. We use STREAM_TIME
    // (not WALL_CLOCK_TIME) so the SLA is driven by the timestamps of the data and is therefore
    // DETERMINISTIC across replays: the same input always produces the same breaches, regardless of
    // how fast we reprocess. WALL_CLOCK_TIME would be non-deterministic and would fire differently
    // on a replay — never use it for event-time SLAs.
    //
    // The trade-off vs a real per-key timer: the punctuator is a full store scan on an interval, so
    // breach detection is granular to the interval, not exact-to-the-millisecond. That is the
    // standard KS compromise and worth calling out to a reviewer.
    context.schedule(ONE_MINUTE_MS, PunctuationType.STREAM_TIME, this::punctuateSla);
  }

  @Override
  public void process(final Record<Long, SourceEvent> record) {
    final long instanceKey = record.key();
    final SourceEvent event = record.value();

    InstanceState state = store.get(instanceKey);
    if (state == null) {
      state = new InstanceState();
    }

    switch (event.type()) {
      case ACTIVATED -> {
        state.setStartMs(event.timestampMs());
        state.setStatus(InstanceState.Status.OPEN);
        // Fold the identity fields too, so a later SLA-breach fact (which has no terminal event) can
        // still be attributed to the right processId/tenant.
        state.setProcessId(event.processId());
        state.setTenantId(event.tenantId());
        store.put(instanceKey, state); // framework persists this to RocksDB + changelog
      }
      case VARIABLE -> {
        state.vars().put(event.varName(), event.varValue());
        store.put(instanceKey, state);
      }
      case INCIDENT -> {
        state.setHadIncident(true);
        store.put(instanceKey, state);
      }
      case COMPLETED, TERMINATED -> {
        state.setEndMs(event.timestampMs());
        state.setStatus(
            event.type() == SourceEvent.EventType.COMPLETED
                ? InstanceState.Status.COMPLETED
                : InstanceState.Status.TERMINATED);
        // Derive the fact from the fully-folded state, then evict. We forward with the terminal
        // event's source coordinates so the downstream forward-only gate is exact.
        emitCompletion(instanceKey, state, event, false, record.timestamp());
        store.delete(instanceKey); // EVICTION: bound the store; the instance's life is over
      }
    }
  }

  /**
   * STREAM_TIME punctuator: fires on stream-time progress. Scans the store for instances that are
   * still OPEN past their SLA deadline, emits a breach fact, and evicts them.
   */
  private void punctuateSla(final long streamTimeMs) {
    // Collect first, mutate after — you must not delete from the store while iterating it.
    java.util.List<KeyValue<Long, InstanceState>> breached = new java.util.ArrayList<>();
    try (final KeyValueIterator<Long, InstanceState> it = store.all()) {
      while (it.hasNext()) {
        final KeyValue<Long, InstanceState> entry = it.next();
        final InstanceState s = entry.value;
        if (s.isOpen() && s.isStarted() && streamTimeMs >= s.startMs() + SLA.toMillis()) {
          breached.add(entry);
        }
      }
    }
    for (final KeyValue<Long, InstanceState> entry : breached) {
      emitBreach(entry.key, entry.value, streamTimeMs);
      store.delete(entry.key);
    }
  }

  private void emitCompletion(
      final long instanceKey,
      final InstanceState state,
      final SourceEvent terminal,
      final boolean breach,
      final long recordTimeMs) {
    if (!state.isStarted()) {
      // We never saw ACTIVATED (e.g. late join / truncated history). Skip rather than emit a fact
      // with a bogus duration.
      LOG.debug("Dropping completion for instance {} with no observed start", instanceKey);
      return;
    }
    final CompletionFact fact =
        new CompletionFact(
            terminal.processId(),
            terminal.tenantId(),
            floorToMinute(state.startMs()),
            state.endMs() - state.startMs(),
            state.hadIncident(),
            breach,
            terminal.sourcePartition(),
            terminal.sourceOffset());
    // context.forward carries the fact to the next node. We keep the record timestamp (event time)
    // so the downstream windowed aggregate windows by the ORIGINAL event time, not now().
    context.forward(new Record<>(instanceKey, fact, recordTimeMs));
  }

  private void emitBreach(
      final long instanceKey, final InstanceState state, final long streamTimeMs) {
    // A breach fact has no terminal source event; we synthesize coordinates from the stream time and
    // mark slaBreached=true. duration = elapsed-at-breach. processId/tenant were folded in on
    // ACTIVATED, so the breach fact is fully attributed. It carries no source offset (-1) and is
    // therefore treated as always-forward by the gate — a breach is a fresh emission, not a replay of
    // a source record.
    final CompletionFact fact =
        new CompletionFact(
            state.processId(),
            state.tenantId(),
            floorToMinute(state.startMs()),
            streamTimeMs - state.startMs(),
            state.hadIncident(),
            /* slaBreached */ true,
            /* sourcePartition */ -1,
            /* sourceOffset */ -1L);
    context.forward(new Record<>(instanceKey, fact, streamTimeMs));
    LOG.info("SLA breach for instance {} at streamTime {}", instanceKey, streamTimeMs);
  }

  private static long floorToMinute(final long epochMs) {
    return epochMs - (epochMs % Duration.ofMinutes(1).toMillis());
  }

  @Override
  public void close() {
    // Nothing to release; the framework owns the store lifecycle.
  }
}
