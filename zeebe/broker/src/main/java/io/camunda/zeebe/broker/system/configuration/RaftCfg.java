/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.time.Duration;

public final class RaftCfg implements ConfigurationEntry {
  public static final boolean DEFAULT_ENABLE_PRIORITY_ELECTION = true;
  private static final FlushConfig DEFAULT_FLUSH_CONFIG =
      new FlushConfig(true, Duration.ZERO, false);

  private boolean enablePriorityElection = DEFAULT_ENABLE_PRIORITY_ELECTION;

  private FlushConfig flush = DEFAULT_FLUSH_CONFIG;

  public boolean isEnablePriorityElection() {
    return enablePriorityElection;
  }

  public void setEnablePriorityElection(final boolean enablePriorityElection) {
    this.enablePriorityElection = enablePriorityElection;
  }

  public FlushConfig getFlush() {
    return flush;
  }

  public void setFlush(final FlushConfig flush) {
    this.flush = flush;
  }

  @Override
  public String toString() {
    return "RaftCfg{"
        + "enablePriorityElection="
        + enablePriorityElection
        + ", flushConfig="
        + flush
        + '}';
  }

  /**
   * @param enabled if false, the raft log is only flushed before snapshots, trading durability for
   *     performance
   * @param delayTime if positive, flushes are delayed by at least the given period, trading
   *     durability for performance; mutually exclusive with {@code coalesced}
   * @param coalesced if true, redundant flushes are deduped and concurrent flushes are coalesced
   *     without giving up durability; mutually exclusive with {@code delayTime}
   */
  public record FlushConfig(boolean enabled, Duration delayTime, boolean coalesced) {
    public FlushConfig(final boolean enabled, final Duration delayTime, final boolean coalesced) {
      this.enabled = enabled;
      this.delayTime = delayTime == null ? Duration.ZERO : delayTime;
      this.coalesced = coalesced;
    }
  }
}
