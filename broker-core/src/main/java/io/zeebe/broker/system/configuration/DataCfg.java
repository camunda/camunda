/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import io.zeebe.util.Environment;
import java.util.Arrays;
import java.util.List;

public class DataCfg implements ConfigurationEntry {
  public static final String DEFAULT_DIRECTORY = "data";

  // Hint: do not use Collections.singletonList as this does not support replaceAll
  private List<String> directories = Arrays.asList(DEFAULT_DIRECTORY);

  private String logSegmentSize = "512M";

  private String snapshotPeriod = "15m";

  private String snapshotReplicationPeriod = "5m";

  private String raftSegmentSize;

  private int maxSnapshots = 3;

  @Override
  public void init(BrokerCfg globalConfig, String brokerBase, Environment environment) {
    applyEnvironment(environment);
    directories.replaceAll(d -> ConfigurationUtil.toAbsolutePath(d, brokerBase));
  }

  private void applyEnvironment(final Environment environment) {
    environment.getList(EnvironmentConstants.ENV_DIRECTORIES).ifPresent(v -> directories = v);
  }

  public List<String> getDirectories() {
    return directories;
  }

  public void setDirectories(List<String> directories) {
    this.directories = directories;
  }

  public String getLogSegmentSize() {
    return logSegmentSize;
  }

  public void setLogSegmentSize(String logSegmentSize) {
    this.logSegmentSize = logSegmentSize;
  }

  public String getSnapshotPeriod() {
    return snapshotPeriod;
  }

  public void setSnapshotPeriod(final String snapshotPeriod) {
    this.snapshotPeriod = snapshotPeriod;
  }

  public String getSnapshotReplicationPeriod() {
    return snapshotReplicationPeriod;
  }

  public void setSnapshotReplicationPeriod(String snapshotReplicationPeriod) {
    this.snapshotReplicationPeriod = snapshotReplicationPeriod;
  }

  public int getMaxSnapshots() {
    return maxSnapshots;
  }

  public void setMaxSnapshots(final int maxSnapshots) {
    this.maxSnapshots = maxSnapshots;
  }

  public String getRaftSegmentSize() {
    return raftSegmentSize;
  }

  public void setRaftSegmentSize(String raftSegmentSize) {
    this.raftSegmentSize = raftSegmentSize;
  }

  @Override
  public String toString() {
    return "DataCfg{"
        + "directories="
        + directories
        + ", logSegmentSize='"
        + logSegmentSize
        + '\''
        + ", snapshotPeriod='"
        + snapshotPeriod
        + '\''
        + ", snapshotReplicationPeriod='"
        + snapshotReplicationPeriod
        + '\''
        + ", maxSnapshots='"
        + maxSnapshots
        + '\''
        + '}';
  }
}
