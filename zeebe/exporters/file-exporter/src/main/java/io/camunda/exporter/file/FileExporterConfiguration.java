/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.file;

import io.camunda.zeebe.exporter.api.context.StrictConfiguration;
import java.time.Duration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@StrictConfiguration
public class FileExporterConfiguration {

  private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 128L * 1024 * 1024;
  private static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofSeconds(1);

  private @Nullable String directory;
  private long maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE_BYTES;
  private Duration flushInterval = DEFAULT_FLUSH_INTERVAL;

  public @Nullable String getDirectory() {
    return directory;
  }

  public FileExporterConfiguration setDirectory(final @Nullable String directory) {
    this.directory = directory;
    return this;
  }

  public long getMaxFileSizeBytes() {
    return maxFileSizeBytes;
  }

  public FileExporterConfiguration setMaxFileSizeBytes(final long maxFileSizeBytes) {
    this.maxFileSizeBytes = maxFileSizeBytes;
    return this;
  }

  public Duration getFlushInterval() {
    return flushInterval;
  }

  public FileExporterConfiguration setFlushInterval(final Duration flushInterval) {
    this.flushInterval = flushInterval;
    return this;
  }

  @Override
  public String toString() {
    return "FileExporterConfiguration{"
        + "directory='"
        + directory
        + '\''
        + ", maxFileSizeBytes="
        + maxFileSizeBytes
        + ", flushInterval="
        + flushInterval
        + '}';
  }
}
