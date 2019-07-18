/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog;

import java.io.File;

/**
 * Represents the storage configuration of a partition. It keeps the path of the local data
 * directory for
 *
 * <ul>
 *   <li>logstream segments
 *   <li>log block index
 *   <li>log block index snapshots
 *   <li>stream processor state
 * </ul>
 */
public class StorageConfiguration {

  private final File logDirectory;
  private final File statesDirectory;
  private int partitionId;
  private long logSegmentSize;

  public StorageConfiguration(final File partitionLogDir, final File statesDir) {
    this.logDirectory = partitionLogDir;
    this.statesDirectory = statesDir;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public StorageConfiguration setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public File getLogDirectory() {
    return logDirectory;
  }

  public long getLogSegmentSize() {
    return logSegmentSize;
  }

  public StorageConfiguration setLogSegmentSize(final long value) {
    logSegmentSize = value;
    return this;
  }

  public File getStatesDirectory() {
    return statesDirectory;
  }
}
