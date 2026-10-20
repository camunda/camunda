/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Resolves the effective timeline of a partition (spec sections 8.1 and 13). */
public final class LogArchiverTimeline {

  /** Written by restore into the partition's data directory. */
  public static final String TIMELINE_FILE_NAME = "log-archive-timeline";

  /**
   * Written by restore from the log archive next to the timeline file. It tells where the restored
   * timeline takes over its parent (spec section 19.3).
   */
  public static final String TAKEOVER_FILE_NAME = "log-archive-takeover";

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiverTimeline.class);

  private LogArchiverTimeline() {}

  /**
   * Returns the content of the timeline file in the partition directory if it exists and is not
   * blank, otherwise the configured timeline.
   *
   * @throws UncheckedIOException if the timeline file exists but cannot be read
   */
  public static String resolve(final Path partitionDirectory, final String configuredTimeline) {
    return resolveEffective(partitionDirectory, configuredTimeline).timelineId();
  }

  /**
   * Like {@link #resolve(Path, String)}, but also tells whether the timeline comes from the
   * timeline file, i.e. from a restore.
   *
   * @throws UncheckedIOException if the timeline file exists but cannot be read
   */
  public static Effective resolveEffective(
      final Path partitionDirectory, final String configuredTimeline) {
    final var file = partitionDirectory.resolve(TIMELINE_FILE_NAME);
    if (!Files.exists(file)) {
      return Effective.configured(configuredTimeline);
    }
    try {
      final var timeline = Files.readString(file, StandardCharsets.UTF_8).strip();
      return timeline.isEmpty()
          ? Effective.configured(configuredTimeline)
          : new Effective(timeline, true, readTakeover(partitionDirectory));
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read the log archive timeline file " + file, e);
    }
  }

  /**
   * Reads the takeover file of the partition directory. Never fails: the takeover is advisory, so a
   * missing, unreadable or invalid file yields {@code null} (the latter two with a warning) instead
   * of blocking the start of the timeline.
   */
  public static @Nullable Takeover readTakeover(final Path partitionDirectory) {
    final var file = partitionDirectory.resolve(TAKEOVER_FILE_NAME);
    if (!Files.exists(file)) {
      return null;
    }
    try {
      final var takeover = LogArchiveJson.read(Files.readAllBytes(file), Takeover.class);
      if (takeover.parentTimelineId() == null
          || takeover.parentTimelineId().isEmpty()
          || takeover.parentIndex() < 1
          || takeover.parentPosition() < 1) {
        LOG.warn("Ignoring the log archive takeover file {}: it is incomplete", file);
        return null;
      }
      return takeover;
    } catch (final IOException | RuntimeException e) {
      LOG.warn("Ignoring the log archive takeover file {}: it cannot be read", file, e);
      return null;
    }
  }

  /**
   * Where a restored timeline takes over its parent: the entry of the restore point T.
   *
   * @param parentTimelineId the timeline the restore read
   * @param parentIndex the Raft index of T's entry
   * @param parentPosition the position of T
   */
  @JsonPropertyOrder({"parentTimelineId", "parentIndex", "parentPosition"})
  public record Takeover(String parentTimelineId, long parentIndex, long parentPosition) {

    /** The deterministic content of the takeover file, without a trailing newline. */
    public byte[] toJson() {
      return LogArchiveJson.write(this);
    }
  }

  /**
   * The effective timeline of a partition.
   *
   * @param timelineId the timeline to archive into
   * @param fromTimelineFile whether it was written by a restore into the timeline file; otherwise
   *     it is the configured one
   * @param takeover where the timeline takes over its parent, from the takeover file written by a
   *     restore from the log archive; {@code null} if there is none or it is not usable
   */
  public record Effective(
      String timelineId, boolean fromTimelineFile, @Nullable Takeover takeover) {

    public Effective(final String timelineId, final boolean fromTimelineFile) {
      this(timelineId, fromTimelineFile, null);
    }

    public static Effective configured(final String timelineId) {
      return new Effective(timelineId, false, null);
    }
  }
}
