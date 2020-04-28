/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import static io.zeebe.util.StringUtil.LIST_SANITIZER;

import io.atomix.storage.StorageLevel;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.util.unit.DataSize;

public final class DataCfg implements ConfigurationEntry {
  public static final String DEFAULT_DIRECTORY = "data";
  private static final DataSize DEFAULT_DATA_SIZE = DataSize.ofMegabytes(512);

  // Hint: do not use Collections.singletonList as this does not support replaceAll
  private List<String> directories = Arrays.asList(DEFAULT_DIRECTORY);

  private DataSize logSegmentSize = DEFAULT_DATA_SIZE;

  private Duration snapshotPeriod = Duration.ofMinutes(15);

  private int logIndexDensity = 100;

  private boolean useMmap = false;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {

    directories.replaceAll(d -> ConfigurationUtil.toAbsolutePath(d, brokerBase));
  }

  public List<String> getDirectories() {
    return directories;
  }

  public void setDirectories(final List<String> directories) {
    this.directories = LIST_SANITIZER.apply(directories);
  }

  public long getLogSegmentSizeInBytes() {
    return Optional.ofNullable(logSegmentSize).orElse(DEFAULT_DATA_SIZE).toBytes();
  }

  public DataSize getLogSegmentSize() {
    return logSegmentSize;
  }

  public void setLogSegmentSize(final DataSize logSegmentSize) {
    this.logSegmentSize = logSegmentSize;
  }

  public Duration getSnapshotPeriod() {
    return snapshotPeriod;
  }

  public void setSnapshotPeriod(final Duration snapshotPeriod) {
    this.snapshotPeriod = snapshotPeriod;
  }

  public int getLogIndexDensity() {
    return logIndexDensity;
  }

  public void setLogIndexDensity(final int logIndexDensity) {
    this.logIndexDensity = logIndexDensity;
  }

  public boolean useMmap() {
    return useMmap;
  }

  public void setUseMmap(final boolean useMmap) {
    this.useMmap = useMmap;
  }

  public StorageLevel getAtomixStorageLevel() {
    return useMmap() ? StorageLevel.MAPPED : StorageLevel.DISK;
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
        + ", logIndexDensity="
        + logIndexDensity
        + ", useMmap="
        + useMmap
        + '}';
  }
}
