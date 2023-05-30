/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static io.camunda.zeebe.broker.system.configuration.ClusterCfg.DEFAULT_ELECTION_TIMEOUT;

import java.time.Duration;

public final class ExperimentalRaftCfg implements ConfigurationEntry {

  public static final Duration DEFAULT_SNAPSHOT_REQUEST_TIMEOUT = Duration.ofMillis(2500);
  // Requests should time out faster than the election timeout to ensure that a single missed
  // heartbeat does not cause immediate re-election.
  private static final Duration DEFAULT_REQUEST_TIMEOUT = DEFAULT_ELECTION_TIMEOUT;
  private static final Duration DEFAULT_MAX_QUORUM_RESPONSE_TIMEOUT = Duration.ofSeconds(0);
  private static final int DEFAULT_MIN_STEP_DOWN_FAILURE_COUNT = 3;
  private static final int DEFAULT_PREFER_SNAPSHOT_REPLICATION_THRESHOLD = 100;
  private static final boolean DEFAULT_PREALLOCATE_SEGMENT_FILES = true;
  private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
  private Duration snapshotRequestTimeout = DEFAULT_SNAPSHOT_REQUEST_TIMEOUT;
  private Duration maxQuorumResponseTimeout = DEFAULT_MAX_QUORUM_RESPONSE_TIMEOUT;
  private int minStepDownFailureCount = DEFAULT_MIN_STEP_DOWN_FAILURE_COUNT;
  private int preferSnapshotReplicationThreshold = DEFAULT_PREFER_SNAPSHOT_REPLICATION_THRESHOLD;

  private boolean preallocateSegmentFiles = DEFAULT_PREALLOCATE_SEGMENT_FILES;

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public Duration getSnapshotRequestTimeout() {
    return snapshotRequestTimeout;
  }

  public void setSnapshotRequestTimeout(final Duration snapshotRequestTimeout) {
    this.snapshotRequestTimeout = snapshotRequestTimeout;
  }

  public Duration getMaxQuorumResponseTimeout() {
    return maxQuorumResponseTimeout;
  }

  public void setMaxQuorumResponseTimeout(final Duration maxQuorumResponseTimeout) {
    this.maxQuorumResponseTimeout = maxQuorumResponseTimeout;
  }

  public int getMinStepDownFailureCount() {
    return minStepDownFailureCount;
  }

  public void setMinStepDownFailureCount(final int minStepDownFailureCount) {
    this.minStepDownFailureCount = minStepDownFailureCount;
  }

  public int getPreferSnapshotReplicationThreshold() {
    return preferSnapshotReplicationThreshold;
  }

  public void setPreferSnapshotReplicationThreshold(final int preferSnapshotReplicationThreshold) {
    this.preferSnapshotReplicationThreshold = preferSnapshotReplicationThreshold;
  }

  public boolean isPreallocateSegmentFiles() {
    return preallocateSegmentFiles;
  }

  public void setPreallocateSegmentFiles(final boolean preallocateSegmentFiles) {
    this.preallocateSegmentFiles = preallocateSegmentFiles;
  }
}
