/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration.legacy;

import io.zeebe.util.ByteValueParser;
import io.zeebe.util.DurationUtil;
import io.zeebe.util.Environment;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Deprecated(since = "0.23.0-alpha1")
/* Kept in order to be able to offer a migration path for old configuration.
 * It is not yet clear whether we intent to offer a migration path for old configurations.
 * This class might be moved or removed on short notice.
 */
public final class DataCfg implements ConfigurationEntry {
  public static final String DEFAULT_DIRECTORY = "data";

  // Hint: do not use Collections.singletonList as this does not support replaceAll
  private List<String> directories = Arrays.asList(DEFAULT_DIRECTORY);

  private String logSegmentSize = "512M";

  private String snapshotPeriod = "15m";

  private int maxSnapshots = 3;

  @Override
  public void init(
      final BrokerCfg globalConfig, final String brokerBase, final Environment environment) {

    applyEnvironment(environment);
    directories.replaceAll(d -> ConfigurationUtil.toAbsolutePath(d, brokerBase));
  }

  private void applyEnvironment(final Environment environment) {
    environment.getList(EnvironmentConstants.ENV_DIRECTORIES).ifPresent(v -> directories = v);
  }

  public List<String> getDirectories() {
    return directories;
  }

  public void setDirectories(final List<String> directories) {
    this.directories = directories;
  }

  public Long getLogSegmentSizeInBytes() {
    if (logSegmentSize != null) {
      return ByteValueParser.fromString(logSegmentSize).toBytes();
    } else {
      return null;
    }
  }

  public String getLogSegmentSize() {
    return logSegmentSize;
  }

  public void setLogSegmentSize(final String logSegmentSize) {
    if (logSegmentSize != null) {
      // call parsing logic to provoke any exceptions that might occur during parsing
      ByteValueParser.fromString(logSegmentSize);
    }

    this.logSegmentSize = logSegmentSize;
  }

  public String getSnapshotPeriod() {
    return snapshotPeriod;
  }

  public void setSnapshotPeriod(final String snapshotPeriod) {
    // call parsing to provoke any exceptions that might occur during parsing
    DurationUtil.parse(snapshotPeriod);

    this.snapshotPeriod = snapshotPeriod;
  }

  public Duration getSnapshotPeriodAsDuration() {
    return DurationUtil.parse(snapshotPeriod);
  }

  public int getMaxSnapshots() {
    return maxSnapshots;
  }

  public void setMaxSnapshots(final int maxSnapshots) {
    this.maxSnapshots = maxSnapshots;
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
        + ", maxSnapshots="
        + maxSnapshots
        + '}';
  }
}
