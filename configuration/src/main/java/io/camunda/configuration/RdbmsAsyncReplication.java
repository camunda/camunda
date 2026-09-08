/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration.RegionConfiguration;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration.ReplicationType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class RdbmsAsyncReplication {

  private boolean enabled = ReplicationConfiguration.DEFAULT_ENABLED;
  private ReplicationType type;
  private Duration pollingInterval = ReplicationConfiguration.DEFAULT_POLLING_INTERVAL;

  /**
   * The number of replicas required to confirm a position - the simple alternative to {@link
   * #regions}. Left unset (rather than defaulted here) so the mapping layer can tell whether an
   * operator configured it explicitly; the actual default ({@link
   * ReplicationConfiguration#DEFAULT_MIN_SYNC_REPLICAS}) is applied there, only when {@link
   * #regions} is also empty. Mutually exclusive with {@link #regions} - set only one.
   */
  private Integer minSyncReplicas;

  private Duration maxLag = ReplicationConfiguration.DEFAULT_MAX_LAG;
  private boolean pauseOnMaxLagExceeded =
      ReplicationConfiguration.DEFAULT_PAUSE_ON_MAX_LAG_EXCEEDED;
  private Duration delay;
  private Duration queueDebounceTime = ReplicationConfiguration.DEFAULT_QUEUE_DEBOUNCE_TIME;
  private int queueCapacity = ReplicationConfiguration.DEFAULT_QUEUE_CAPACITY;

  /**
   * Per-region quorum requirements, for topologies where a flat {@link #minSyncReplicas} can't
   * express per-region redundancy (e.g. "at least 2 of 3 nodes healthy in each of 3 regions") - see
   * {@code zeebe/exporters/rdbms-exporter/docs/adr/0001-region-aware-replication-quorum.md}. Every
   * entry is mandatory. Empty by default; mutually exclusive with {@link #minSyncReplicas} - set
   * only one.
   */
  private List<Region> regions = new ArrayList<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

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

  public Duration getQueueDebounceTime() {
    return queueDebounceTime;
  }

  public void setQueueDebounceTime(final Duration queueDebounceTime) {
    this.queueDebounceTime = queueDebounceTime;
  }

  public int getQueueCapacity() {
    return queueCapacity;
  }

  public void setQueueCapacity(final int queueCapacity) {
    this.queueCapacity = queueCapacity;
  }

  public Duration getPollingInterval() {
    return pollingInterval;
  }

  public void setPollingInterval(final Duration pollingInterval) {
    this.pollingInterval = pollingInterval;
  }

  public Integer getMinSyncReplicas() {
    return minSyncReplicas;
  }

  public void setMinSyncReplicas(final Integer minSyncReplicas) {
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

  public List<Region> getRegions() {
    return regions;
  }

  public void setRegions(final List<Region> regions) {
    this.regions = regions;
  }

  /** Mirrors {@link RegionConfiguration}. */
  public static class Region {

    private String name;
    private String pattern;
    private int minReplicas;

    public String getName() {
      return name;
    }

    public void setName(final String name) {
      this.name = name;
    }

    public String getPattern() {
      return pattern;
    }

    public void setPattern(final String pattern) {
      this.pattern = pattern;
    }

    public int getMinReplicas() {
      return minReplicas;
    }

    public void setMinReplicas(final int minReplicas) {
      this.minReplicas = minReplicas;
    }
  }
}
