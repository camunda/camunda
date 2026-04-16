/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;

public class RdbmsReplication {

  /** Enables asynchronous replication support in the RDBMS exporter. */
  private boolean enabled = false;

  /** Minimum number of synchronous replicas that must acknowledge writes. */
  private int minSyncReplicas = 0;

  /**
   * Maximum time an entry can remain unconfirmed by replication before it is confirmed anyway. This
   * bounds the log retention caused by replication lag.
   */
  private Duration maxLag = Duration.ofMinutes(15);

  /**
   * When {@code true} (default), the exporter stops flushing records to the database as soon as the
   * database-reported replication lag exceeds {@link #maxLag}, and resumes once the lag drops below
   * it. When {@code false}, the exporter keeps flushing and force-confirms pending positions when
   * the lag is exceeded (the pre-existing behaviour).
   */
  private boolean pauseOnMaxLagExceeded = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public int getMinSyncReplicas() {
    return minSyncReplicas;
  }

  public void setMinSyncReplicas(final int minSyncReplicas) {
    this.minSyncReplicas = minSyncReplicas;
  }

  public Duration getMaxLag() {
    return maxLag;
  }

  public void setMaxLag(final Duration maxLag) {
    this.maxLag = maxLag;
  }

  public boolean isPauseOnMaxLagExceeded() {
    return pauseOnMaxLagExceeded;
  }

  public void setPauseOnMaxLagExceeded(final boolean pauseOnMaxLagExceeded) {
    this.pauseOnMaxLagExceeded = pauseOnMaxLagExceeded;
  }
}
