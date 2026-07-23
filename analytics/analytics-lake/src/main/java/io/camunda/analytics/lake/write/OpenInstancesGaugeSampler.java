/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.write;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Buffers {@code open_instances_gauge} samples in memory and periodically flushes them through a
 * {@link GaugeBatchSink} — the small-files control described in the module's gauge-table design:
 * writing one Parquet file per 30-second sample tick would proliferate hundreds of tiny files a
 * day, so this class accumulates several ticks' worth of samples and flushes them as one file/
 * commit every {@code flushIntervalMs} instead.
 *
 * <h2>Not thread-safe by design</h2>
 *
 * <p>Every method here is meant to be called from exactly one thread — {@code LakePocApp}'s single
 * poll-loop thread, the same thread that already drives {@code
 * io.camunda.analytics.lake.state.StateSnapshotDumper}'s periodic tick this sampler piggybacks on.
 * No internal synchronization is needed or provided.
 *
 * <h2>Gauge semantics (accepted gaps)</h2>
 *
 * <p>Samples are wall-clock observations, not source-log-derived facts: {@link #tick} never blocks
 * or slows the fold path (it is a plain in-memory buffer append), and any buffered-but-not-yet-
 * flushed samples are lost if the process crashes before the next flush or a clean {@link #close}.
 * That loss is accepted — a fresh warehouse (or one recovering from a crash) simply starts its
 * gauge history at its own next sample, with no replay or backfill from the source log expected or
 * possible (there is no source offset a gauge row could be replayed from in the first place).
 */
public final class OpenInstancesGaugeSampler {

  private final GaugeBatchSink sink;
  private final long flushIntervalMs;
  private final List<GaugeSample> buffered = new ArrayList<>();
  private long lastFlushAtMs;

  /**
   * @param sink where a flush's accumulated samples are durably written
   * @param flushIntervalMs minimum wall-clock time between flushes (see {@code
   *     LakeConfig#gaugeFlushIntervalMs}'s own javadoc)
   * @param startAtMs the wall-clock time this sampler is constructed — seeds the flush clock so the
   *     first flush is due {@code flushIntervalMs} after startup, not immediately
   */
  public OpenInstancesGaugeSampler(
      final GaugeBatchSink sink, final long flushIntervalMs, final long startAtMs) {
    this.sink = sink;
    this.flushIntervalMs = flushIntervalMs;
    lastFlushAtMs = startAtMs;
  }

  /**
   * Buffers one sample tick's per-process open-instance counts, then flushes the whole buffer if
   * {@code flushIntervalMs} has elapsed since the last flush.
   *
   * <p>A process absent from {@code openCountsByProcessId}, or present with a count {@code <= 0},
   * contributes no row — this table carries one row per (tick, process with more than zero open
   * instances), never a zero-count row (see {@code IcebergLakeWriter}'s {@code
   * OPEN_INSTANCES_GAUGE_SCHEMA} javadoc for why the derivable "all processes" total is never
   * stored either).
   *
   * @param nowMs wall-clock epoch millis this tick was taken at — stamped onto every {@link
   *     GaugeSample} produced from this call
   * @param openCountsByProcessId per-process open-instance counts for this tick
   */
  public void tick(final long nowMs, final Map<String, Long> openCountsByProcessId) {
    openCountsByProcessId.forEach(
        (processId, openInstances) -> {
          if (openInstances > 0) {
            buffered.add(new GaugeSample(nowMs, processId, openInstances));
          }
        });
    if (nowMs - lastFlushAtMs >= flushIntervalMs) {
      flush();
      lastFlushAtMs = nowMs;
    }
  }

  /** Flushes whatever is currently buffered, if anything; a no-op on an empty buffer. */
  public void flush() {
    if (buffered.isEmpty()) {
      return;
    }
    sink.writeBatch(List.copyOf(buffered));
    buffered.clear();
  }

  /**
   * Flushes any remaining buffered tail. Callers must invoke this during shutdown, before the
   * underlying {@link GaugeBatchSink}'s own resources (the shared DuckDB connection, the Iceberg
   * catalog) close — see {@code LakePocApp.Handle#close}'s own drain ordering.
   */
  public void close() {
    flush();
  }
}
