/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves the effective timeline of a partition (spec sections 8.1 and 13). */
public final class LogArchiverTimeline {

  /** Written by restore into the partition's data directory. */
  public static final String TIMELINE_FILE_NAME = "log-archive-timeline";

  private LogArchiverTimeline() {}

  /**
   * Returns the content of the timeline file in the partition directory if it exists and is not
   * blank, otherwise the configured timeline.
   *
   * @throws UncheckedIOException if the timeline file exists but cannot be read
   */
  public static String resolve(final Path partitionDirectory, final String configuredTimeline) {
    final var file = partitionDirectory.resolve(TIMELINE_FILE_NAME);
    if (!Files.exists(file)) {
      return configuredTimeline;
    }
    try {
      final var timeline = Files.readString(file, StandardCharsets.UTF_8).strip();
      return timeline.isEmpty() ? configuredTimeline : timeline;
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read the log archive timeline file " + file, e);
    }
  }
}
