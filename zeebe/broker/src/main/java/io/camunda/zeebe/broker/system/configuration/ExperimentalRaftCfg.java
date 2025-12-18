/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static io.camunda.zeebe.broker.system.configuration.ClusterCfg.DEFAULT_ELECTION_TIMEOUT;

import io.camunda.zeebe.journal.file.SegmentAllocator;
import java.time.Duration;
import org.springframework.util.unit.DataSize;

public final class ExperimentalRaftCfg implements ConfigurationEntry {

  public static final Duration DEFAULT_SNAPSHOT_REQUEST_TIMEOUT = Duration.ofMillis(2500);
  public static final DataSize DEFAULT_SNAPSHOT_CHUNK_SIZE = DataSize.ofGigabytes(1);
  private static final Duration DEFAULT_CONFIGURATION_CHANGE_TIMEOUT = Duration.ofSeconds(10);
  // Requests should time out faster than the election timeout to ensure that a single missed
  // heartbeat does not cause immediate re-election.
  private static final Duration DEFAULT_REQUEST_TIMEOUT = DEFAULT_ELECTION_TIMEOUT;
  private static final Duration DEFAULT_MAX_QUORUM_RESPONSE_TIMEOUT = Duration.ofSeconds(0);
  private static final int DEFAULT_MIN_STEP_DOWN_FAILURE_COUNT = 3;
  private static final int DEFAULT_PREFER_SNAPSHOT_REPLICATION_THRESHOLD = 100;
  private static final boolean DEFAULT_PREALLOCATE_SEGMENT_FILES = true;
  private static final PreAllocationStrategy DEFAULT_PREALLOCATE_SEGMENT_STRATEGY =
      PreAllocationStrategy.FILL;
  private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
  private Duration snapshotRequestTimeout = DEFAULT_SNAPSHOT_REQUEST_TIMEOUT;
  private DataSize snapshotChunkSize = DEFAULT_SNAPSHOT_CHUNK_SIZE;
  private Duration configurationChangeTimeout = DEFAULT_CONFIGURATION_CHANGE_TIMEOUT;
  private Duration maxQuorumResponseTimeout = DEFAULT_MAX_QUORUM_RESPONSE_TIMEOUT;
  private int minStepDownFailureCount = DEFAULT_MIN_STEP_DOWN_FAILURE_COUNT;
  private int preferSnapshotReplicationThreshold = DEFAULT_PREFER_SNAPSHOT_REPLICATION_THRESHOLD;
  private boolean preallocateSegmentFiles = DEFAULT_PREALLOCATE_SEGMENT_FILES;

  private PreAllocationStrategy segmentPreallocationStrategy = DEFAULT_PREALLOCATE_SEGMENT_STRATEGY;

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

  public DataSize getSnapshotChunkSize() {
    return snapshotChunkSize;
  }

  public void setSnapshotChunkSize(final DataSize snapshotChunkSize) {
    this.snapshotChunkSize = snapshotChunkSize;
  }

  public Duration getConfigurationChangeTimeout() {
    return configurationChangeTimeout;
  }

  public void setConfigurationChangeTimeout(final Duration configurationChangeTimeout) {
    this.configurationChangeTimeout = configurationChangeTimeout;
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

  public PreAllocationStrategy getSegmentPreallocationStrategy() {
    if (!preallocateSegmentFiles) {
      return PreAllocationStrategy.NOOP;
    }
    return segmentPreallocationStrategy;
  }

  public void setSegmentPreallocationStrategy(final PreAllocationStrategy preAllocationStrategy) {
    segmentPreallocationStrategy = preAllocationStrategy;
  }

  /**
   * Defines the strategy to use to preallocate segment files when "preallocateSegmentFiles" is set
   * to true. Possible options are:
   *
   * <ul>
   *   <li>NOOP: does not preallocate files, same as setting `preallocateSegmentFiles=false`
   *   <li>FILL: fills the new segments with zeroes to ensure the disk space is reserved and the
   *       file is initialized with zeroes
   *   <li>POSIX: reserves the space required on disk using `fallocate` posix system call. Depending
   *       on the filesystem, this may not ensure that enough disk space is available. This strategy
   *       reduces the write throughput to disk which can be particularly useful when using network
   *       file systems. Running `fallocate` requires a POSIX filesystem and JNI calls which might
   *       not be available. If you want to make sure that `fallocate` is used, configure this
   *       strategies, otherwise use the below ones.
   *   <li>POSIX_OR_NOOP: use POSIX strategy or NOOP if it's not possible.
   *   <li>POSIX_OR_FILL: use POSIX strategy or FILL if it's not possible.
   * </ul>
   */
  public enum PreAllocationStrategy {
    NOOP(SegmentAllocator.noop()),
    FILL(SegmentAllocator.fill()),
    POSIX(SegmentAllocator.posix(null)),
    POSIX_OR_NOOP(SegmentAllocator.posix(SegmentAllocator.noop())),
    POSIX_OR_FILL(SegmentAllocator.posix(SegmentAllocator.fill()));

    private final SegmentAllocator segmentAllocator;

    PreAllocationStrategy(final SegmentAllocator segmentAllocator) {
      this.segmentAllocator = segmentAllocator;
    }

    public SegmentAllocator segmentAllocator() {
      return segmentAllocator;
    }
  }
}
