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
  private boolean absorbDeletes = DEFAULTS.absorbDeletes();
  private int pipelineSegmentLimit = DEFAULTS.pipelineSegmentLimit();

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
   * Total buffered-bytes budget across all layered stores of the engine domain: when positive, a
   * persist round starts as soon as the buffered (not yet persisted) writes reach it — bounding
   * memory and the recovery replay window by size, independently of the persist interval. Zero (the
   * default) disables the size trigger; rounds then run at the persist interval or on per-store
   * over-capacity only.
   */
  public DataSize getMaxBufferedBytes() {
    return maxBufferedBytes;
  }

  public void setMaxBufferedBytes(final DataSize maxBufferedBytes) {
    this.maxBufferedBytes = maxBufferedBytes;
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

  /** The store-library configuration equivalent of this entry, validated on construction. */
  public LayeredZeebeDbConfig toDbConfig() {
    return new LayeredZeebeDbConfig(
        maxBytesPerStore.toBytes(),
        maxBufferedBytes.toBytes(),
        absorbDeletes,
        pipelineSegmentLimit,
        persistInterval);
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
        + ", absorbDeletes="
        + absorbDeletes
        + ", pipelineSegmentLimit="
        + pipelineSegmentLimit
        + '}';
  }
}
