/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.function.LongSupplier;

/**
 * Instrumentation hooks of one ownership domain's layered stores; see {@link
 * LayeredStateMetricsDoc} for the published meters. All counting hooks are called on hot paths and
 * must stay allocation-free — implementations pre-resolve their meters.
 */
public interface LayeredStoreMetrics {

  /** Counts a put/delete pair (or a lone tombstone) annihilated in memory by delete absorption. */
  void countAnnihilatedWrites(int elidedWrites);

  /** Counts a never-flushed tombstone skipped by a persist drain. */
  void countDrainSkippedTombstone();

  /** Counts a point read answered by the clean read-through cache. */
  void countCleanCacheHit();

  /** Counts a point read that fell through to the durable-store delegate. */
  void countDelegateReadThrough();

  /** Counts a delegate point read made to compute an exact flushed flag at write time. */
  void countFlushedPointRead();

  /** Counts an in-memory merge collapsing a pipeline back under its segment limit. */
  void countPipelineMerge();

  /**
   * Counts an over-limit pipeline merge skipped because the store's last merge annihilated too few
   * entries to pay off (the pipeline overshoots to a hard cap instead).
   */
  void countPipelineMergeSkipped();

  /** Counts a persist round started for the given trigger. */
  void countRound(PersistTrigger trigger);

  /** Counts a failed persist round (its segments stay buffered and are retried). */
  void countRoundFailure();

  /** Records the duration of a round's persist step. */
  void observeRoundDuration(long elapsedNanos);

  /** Counts a committed slice batch of a persist round (an unpaced round commits exactly one). */
  void countPersistSlice();

  /** Counts one entry drained to the durable store, with its key/value bytes. */
  void countDrainedEntry(int keyBytes, int valueBytes);

  /** Counts a reader-view rotation onto a fresh durable-state snapshot. */
  void countViewRotation();

  /** Counts a read-only view acquisition by a concurrent reader. */
  void countViewAcquisition();

  /** Registers the anchor-lag gauge; called once when the domain's coordinator is built. */
  void registerAnchorLag(LongSupplier lag);

  /**
   * Registers the round-in-flight gauge (1 while a persist round is outstanding, 0 otherwise);
   * called once when the domain's coordinator is built.
   */
  void registerRoundInFlight(LongSupplier inFlight);

  /**
   * Registers the per-layer byte/entry and pipeline-depth gauges over the given stores. The gauges
   * are polled from the metrics scrape thread; they only touch the stores' tear-free volatile stat
   * accessors (see the {@link LayeredKeyValueStore} Threading javadoc), never owner-mutable state.
   */
  void registerStoreGauges(Collection<LayeredKeyValueStore> stores);

  /** A no-op implementation for wirings without a meter registry (e.g. tests). */
  static LayeredStoreMetrics noop() {
    return NoopLayeredStoreMetrics.INSTANCE;
  }

  /**
   * Micrometer-backed metrics tagged with the given domain, or {@link #noop()} when no registry is
   * available.
   */
  static LayeredStoreMetrics of(final MeterRegistry registry, final String domain) {
    return registry == null ? noop() : new MicrometerLayeredStoreMetrics(registry, domain);
  }

  /** The shared no-op instance behind {@link #noop()}. */
  final class NoopLayeredStoreMetrics implements LayeredStoreMetrics {

    private static final NoopLayeredStoreMetrics INSTANCE = new NoopLayeredStoreMetrics();

    private NoopLayeredStoreMetrics() {}

    @Override
    public void countAnnihilatedWrites(final int elidedWrites) {}

    @Override
    public void countDrainSkippedTombstone() {}

    @Override
    public void countCleanCacheHit() {}

    @Override
    public void countDelegateReadThrough() {}

    @Override
    public void countFlushedPointRead() {}

    @Override
    public void countPipelineMerge() {}

    @Override
    public void countPipelineMergeSkipped() {}

    @Override
    public void countRound(final PersistTrigger trigger) {}

    @Override
    public void countRoundFailure() {}

    @Override
    public void observeRoundDuration(final long elapsedNanos) {}

    @Override
    public void countPersistSlice() {}

    @Override
    public void countDrainedEntry(final int keyBytes, final int valueBytes) {}

    @Override
    public void countViewRotation() {}

    @Override
    public void countViewAcquisition() {}

    @Override
    public void registerAnchorLag(final LongSupplier lag) {}

    @Override
    public void registerRoundInFlight(final LongSupplier inFlight) {}

    @Override
    public void registerStoreGauges(final Collection<LayeredKeyValueStore> stores) {}
  }
}
