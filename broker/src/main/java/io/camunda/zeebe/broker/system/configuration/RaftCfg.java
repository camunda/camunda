/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.time.Duration;

public final class RaftCfg implements ConfigurationEntry {
  public static final boolean DEFAULT_ENABLE_PRIORITY_ELECTION = true;
  private static final FlushConfig DEFAULT_FLUSH_CONFIG = new FlushConfig(true, Duration.ZERO);

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

  public record FlushConfig(boolean enabled, Duration delayTime) {
    public FlushConfig(final boolean enabled, final Duration delayTime) {
      this.enabled = enabled;
      this.delayTime = delayTime == null ? Duration.ZERO : delayTime;
    }
  }
}
