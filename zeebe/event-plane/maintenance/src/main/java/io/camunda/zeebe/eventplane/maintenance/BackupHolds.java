/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import io.camunda.zeebe.eventplane.backup.BackupLog;
import io.camunda.zeebe.eventplane.history.HistoryLineage;
import io.camunda.zeebe.eventplane.history.HistoryLineage.Stretch;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What the backups of a log need of it, from their backup logs: a segment that serves the path of a
 * backup's history at an index the backup needs is held, and retention never deletes it, whatever
 * its age.
 */
final class BackupHolds {

  private static final Logger LOG = LoggerFactory.getLogger(BackupHolds.class);
  private static final String BACKUP_LOG_SUFFIX = ".json";

  private final List<Hold> holds;

  private BackupHolds(final List<Hold> holds) {
    this.holds = holds;
  }

  /**
   * Reads the backup logs of a log. An object that is gone when it is read was deleted with its
   * backup after the listing, and needs nothing. Objects that aren't named like a backup log are
   * skipped.
   *
   * @throws UnreadableBackupLogException if a backup log isn't valid: what it needs is unknown
   * @throws io.camunda.zeebe.eventplane.history.MissingManifestException while a manifest that the
   *     path of a backup's history needs is missing
   * @throws io.camunda.zeebe.eventplane.history.InconsistentHistoryException if the manifests
   *     contradict each other
   */
  static BackupHolds read(final ObjectStore store, final LogId log, final HistoryLineage lineage)
      throws IOException, UnreadableBackupLogException {
    final var paths = new HashMap<UUID, List<Stretch>>();
    final var holds = new ArrayList<Hold>();
    for (final var object : Listing.objects(store, ObjectNames.backupLogsPrefix(log))) {
      if (object.key().endsWith(BACKUP_LOG_SUFFIX)) {
        final var backup = readBackupLog(store, object.key());
        if (backup.isPresent()) {
          final var path =
              paths.computeIfAbsent(
                  backup.get().history(), history -> pathOf(lineage, history, log));
          holds.add(new Hold(backup.get(), path));
        }
      }
    }
    return new BackupHolds(holds);
  }

  /** Whether a backup needs the segment, which is in the directory of the history. */
  boolean holds(final UUID history, final StoredSegment segment) {
    return holds.stream().anyMatch(hold -> hold.holds(history, segment));
  }

  private static Optional<BackupLog> readBackupLog(final ObjectStore store, final String key)
      throws IOException, UnreadableBackupLogException {
    final var content = store.get(key);
    if (content.isEmpty()) {
      LOG.info("Skipping the backup log '{}', which was deleted after it was listed", key);
      return Optional.empty();
    }
    try {
      return Optional.of(BackupLog.fromJson(content.get()));
    } catch (final IOException e) {
      throw new UnreadableBackupLogException(key, e);
    }
  }

  /**
   * The path of the backup's history through the log's partition. A history that leaves the
   * partition out has no path, and then it is one stretch without an end: all of its own segments
   * are on it, to be conservative.
   */
  private static List<Stretch> pathOf(
      final HistoryLineage lineage, final UUID history, final LogId log) {
    final var path = lineage.path(history, log.partition());
    return path.isEmpty() ? List.of(new Stretch(history, Base.NOTHING, null)) : path;
  }

  /** A backup's log, and the path of its history: what the backup needs is on that path. */
  private record Hold(BackupLog backup, List<Stretch> path) {

    boolean holds(final UUID history, final StoredSegment segment) {
      return segment.firstIndex() <= backup.lastIndex()
          && segment.lastIndex() >= backup.firstIndex()
          && Retention.isOnPath(Retention.stretchOf(path, history), segment);
    }
  }
}
