/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;

public class PrimaryStorage {

  private static final String PREFIX = "camunda.data.primary-storage";

  private static final Set<String> LEGACY_DIRECTORY_PROPERTIES =
      Set.of("zeebe.broker.data.directory");
  private static final Set<String> LEGACY_RUNTIME_DIRECTORY_PROPERTIES =
      Set.of("zeebe.broker.data.runtimeDirectory");

  /** Specify the directory in which data is stored. */
  private String directory = "data";

  /**
   * Specify the directory in which runtime is stored. By default, runtime is stored in `directory`
   * for data. If `runtime-directory` is configured, then the configured directory will be used. It
   * will have a subdirectory for each partition to store its runtime. There is no need to store
   * runtime in a persistent storage. This configuration allows to split runtime to another disk to
   * optimize for performance and disk usage. Note: If runtime is another disk than the data
   * directory, files need to be copied to data directory while taking snapshot. This may impact
   * disk i/o or performance during snapshotting.
   */
  private String runtimeDirectory;

  private Disk disk = new Disk();
  private LogStream logStream = new LogStream();

  public String getDirectory() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".directory",
        directory,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_DIRECTORY_PROPERTIES);
  }

  public void setDirectory(final String directory) {
    this.directory = directory;
  }

  public String getRuntimeDirectory() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".runtime-directory",
        runtimeDirectory,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_RUNTIME_DIRECTORY_PROPERTIES);
  }

  public void setRuntimeDirectory(final String runtimeDirectory) {
    this.runtimeDirectory = runtimeDirectory;
  }

  public Disk getDisk() {
    return disk;
  }

  public void setDisk(final Disk disk) {
    this.disk = disk;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public void setLogStream(final LogStream logStream) {
    this.logStream = logStream;
  }
}
