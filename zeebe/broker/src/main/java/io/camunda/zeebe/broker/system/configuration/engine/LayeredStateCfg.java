/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.db.layered.zdb.LayeredZeebeDbConfig;
import java.time.Duration;
import org.springframework.util.unit.DataSize;

/**
 * Configures the experimental layered state store: an in-memory, log-first buffer in front of
 * RocksDB that turns per-batch state commits into in-memory promotions and drains to RocksDB only
 * in periodic persist rounds (see {@code io.camunda.zeebe.db.layered}).
 *
 * <p><b>Experimental — unsafe to enable in production.</b> When disabled (the default), the broker
 * behaves exactly as before. When enabled, the runtime RocksDB state trails the log by up to the
 * persist interval, so crash recovery replays a correspondingly larger window, and secondary
 * readers (e.g. the query API) observe state at persist-round freshness. The timer due-date,
 * message-TTL and job-timeout checkers run asynchronously on read views of the buffered state,
 * refreshed right before each checker execution (there is no periodic freeze cadence — the
 * pre-execution barrier is the only freshness anyone consumes, and freezing earlier would forfeit
 * the free overwrite absorption of the active overlay); the remaining engine scheduled tasks run on
 * the stream processor's thread behind a persist barrier so their state scans keep observing every
 * committed batch.
 */
public final class LayeredStateCfg implements ConfigurationEntry {

  // the store library owns the defaults; this entry only overlays what the broker config sets
  private static final LayeredZeebeDbConfig DEFAULTS = LayeredZeebeDbConfig.defaults();

  private boolean enabled = true;
  private Duration persistInterval = DEFAULTS.persistInterval();
  private DataSize maxBytesPerStore = DataSize.ofBytes(DEFAULTS.maxBytesPerStore());
  private DataSize maxBufferedBytes = DataSize.ofBytes(DEFAULTS.maxBufferedBytes());
  private double ladderStartFraction = DEFAULTS.ladderStartFraction();
  private double ladderFlatOutFraction = DEFAULTS.ladderFlatOutFraction();
  private boolean absorbDeletes = DEFAULTS.absorbDeletes();
  private int pipelineSegmentLimit = DEFAULTS.pipelineSegmentLimit();
  private DataSize persistMinSliceBytes = DataSize.ofBytes(DEFAULTS.persistMinSliceBytes());
  private double persistPacingTargetFraction = DEFAULTS.persistPacingTargetFraction();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public Duration getPersistInterval() {
    return persistInterval;
  }

  public void setPersistInterval(final Duration persistInterval) {
    this.persistInterval = persistInterval;
  }

  public DataSize getMaxBytesPerStore() {
    return maxBytesPerStore;
  }

  public void setMaxBytesPerStore(final DataSize maxBytesPerStore) {
    this.maxBytesPerStore = maxBytesPerStore;
  }

  /**
   * Total buffered-bytes budget across all layered stores of the engine domain: when positive, the
   * buffered (not yet persisted) writes are drained on a graduated pressure ladder over this budget
   * (see {@link #getLadderStartFraction()} and {@link #getLadderFlatOutFraction()}) — bounding
   * memory and the recovery replay window by size, independently of the persist interval. Zero (the
   * default) disables the ladder; rounds then run at the persist interval or on per-store
   * over-capacity only.
   */
  public DataSize getMaxBufferedBytes() {
    return maxBufferedBytes;
  }

  public void setMaxBufferedBytes(final DataSize maxBufferedBytes) {
    this.maxBufferedBytes = maxBufferedBytes;
  }

  /**
   * The buffer-pressure ladder's start rung as a fraction of {@link #getMaxBufferedBytes()}: at
   * this fill level a paced persist round starts early (or an in-flight one is expedited), so the
   * buffer drains before pressure builds further.
   */
  public double getLadderStartFraction() {
    return ladderStartFraction;
  }

  public void setLadderStartFraction(final double ladderStartFraction) {
    this.ladderStartFraction = ladderStartFraction;
  }

  /**
   * The buffer-pressure ladder's flat-out rung as a fraction of {@link #getMaxBufferedBytes()}: at
   * this fill level a persist round starts immediately and drains unpaced; a single store over its
   * own {@link #getMaxBytesPerStore()} budget feeds this rung too. Must be at least {@link
   * #getLadderStartFraction()}.
   */
  public double getLadderFlatOutFraction() {
    return ladderFlatOutFraction;
  }

  public void setLadderFlatOutFraction(final double ladderFlatOutFraction) {
    this.ladderFlatOutFraction = ladderFlatOutFraction;
  }

  /**
   * Whether a delete of a never-persisted put annihilates the pair in memory so neither write ever
   * reaches RocksDB. On by default — the store's exact flushed flags make absorption
   * unconditionally sound, and short-lived put/delete churn is the dominant write pattern the
   * layered store exists to elide.
   */
  public boolean isAbsorbDeletes() {
    return absorbDeletes;
  }

  public void setAbsorbDeletes(final boolean absorbDeletes) {
    this.absorbDeletes = absorbDeletes;
  }

  /**
   * Maximum number of non-persisting frozen segments per store before they are merged down,
   * bounding the read amplification of buffered state.
   */
  public int getPipelineSegmentLimit() {
    return pipelineSegmentLimit;
  }

  public void setPipelineSegmentLimit(final int pipelineSegmentLimit) {
    this.pipelineSegmentLimit = pipelineSegmentLimit;
  }

  /**
   * Minimum entry bytes per sub-batch slice of a paced persist drain: a round's drain to RocksDB
   * commits slices of at least this size instead of one monolithic batch, spread across the persist
   * interval (see {@link #getPersistPacingTargetFraction()}).
   */
  public DataSize getPersistMinSliceBytes() {
    return persistMinSliceBytes;
  }

  public void setPersistMinSliceBytes(final DataSize persistMinSliceBytes) {
    this.persistMinSliceBytes = persistMinSliceBytes;
  }

  /**
   * The fraction of the persist interval a paced drain aims to finish within (checkpoint-spreading
   * style, in (0, 1]): after each slice the drain waits while its progress runs ahead of
   * elapsed-time over this fraction of the interval. The pacing is dropped (the drain finishes
   * flat-out) when buffered state runs over capacity mid-round.
   */
  public double getPersistPacingTargetFraction() {
    return persistPacingTargetFraction;
  }

  public void setPersistPacingTargetFraction(final double persistPacingTargetFraction) {
    this.persistPacingTargetFraction = persistPacingTargetFraction;
  }

  /** The store-library configuration equivalent of this entry, validated on construction. */
  public LayeredZeebeDbConfig toDbConfig() {
    return new LayeredZeebeDbConfig(
        maxBytesPerStore.toBytes(),
        maxBufferedBytes.toBytes(),
        ladderStartFraction,
        ladderFlatOutFraction,
        absorbDeletes,
        pipelineSegmentLimit,
        persistInterval,
        persistMinSliceBytes.toBytes(),
        persistPacingTargetFraction);
  }

  @Override
  public String toString() {
    return "LayeredStateCfg{"
        + "enabled="
        + enabled
        + ", persistInterval="
        + persistInterval
        + ", maxBytesPerStore="
        + maxBytesPerStore
        + ", maxBufferedBytes="
        + maxBufferedBytes
        + ", ladderStartFraction="
        + ladderStartFraction
        + ", ladderFlatOutFraction="
        + ladderFlatOutFraction
        + ", absorbDeletes="
        + absorbDeletes
        + ", pipelineSegmentLimit="
        + pipelineSegmentLimit
        + ", persistMinSliceBytes="
        + persistMinSliceBytes
        + ", persistPacingTargetFraction="
        + persistPacingTargetFraction
        + '}';
  }
}
