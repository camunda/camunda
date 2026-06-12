/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration.ReplicationType;
import java.time.Duration;

public class RdbmsAsyncReplication {

  private ReplicationType type = ReplicationConfiguration.DEFAULT_TYPE;
  private Duration pollingInterval = ReplicationConfiguration.DEFAULT_POLLING_INTERVAL;
  private int minSyncReplicas = ReplicationConfiguration.DEFAULT_MIN_SYNC_REPLICAS;
  private Duration maxLag = ReplicationConfiguration.DEFAULT_MAX_LAG;
  private boolean pauseOnMaxLagExceeded =
      ReplicationConfiguration.DEFAULT_PAUSE_ON_MAX_LAG_EXCEEDED;
  private Duration delay = ReplicationConfiguration.DEFAULT_DELAY;

  public ReplicationType getType() {
    return type;
  }

  public void setType(final ReplicationType type) {
    this.type = type;
  }

  public Duration getDelay() {
    return delay;
  }

  public void setDelay(final Duration delay) {
    this.delay = delay;
  }

  public Duration getPollingInterval() {
    return pollingInterval;
  }

  public void setPollingInterval(final Duration pollingInterval) {
    this.pollingInterval = pollingInterval;
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
