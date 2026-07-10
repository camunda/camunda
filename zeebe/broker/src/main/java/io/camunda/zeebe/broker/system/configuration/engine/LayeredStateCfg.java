/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
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
 * readers (e.g. the query API) observe state at persist-round freshness. Engine scheduled tasks
 * (timer/job checkers) are forced onto the stream processor's thread behind a persist barrier so
 * their state scans keep observing every committed batch; enabling the experimental async checker
 * feature flags together with this flag is unsupported.
 */
public final class LayeredStateCfg implements ConfigurationEntry {

  private static final Duration DEFAULT_PERSIST_INTERVAL = Duration.ofSeconds(1);
  private static final DataSize DEFAULT_MAX_BYTES_PER_STORE = DataSize.ofMegabytes(16);

  private boolean enabled = false;
  private Duration persistInterval = DEFAULT_PERSIST_INTERVAL;
  private DataSize maxBytesPerStore = DEFAULT_MAX_BYTES_PER_STORE;

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

  @Override
  public String toString() {
    return "LayeredStateCfg{"
        + "enabled="
        + enabled
        + ", persistInterval="
        + persistInterval
        + ", maxBytesPerStore="
        + maxBytesPerStore
        + '}';
  }
}
