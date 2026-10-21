/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import io.camunda.zeebe.eventplane.history.HistoryLineage;
import io.camunda.zeebe.eventplane.history.HistoryLineage.Stretch;
import io.camunda.zeebe.eventplane.history.HistoryManifest;
import io.camunda.zeebe.eventplane.segment.Footer;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.NoSuchObjectException;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.Ulid;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deletes the segments that are older than the retention period, after it writes a retention marker
 * that tells readers where what is kept starts. It keeps what a restore from the bucket needs: the
 * newest snapshot of the path, whatever its age, and every segment from the oldest kept snapshot
 * on. It also keeps what backups need, which the marker may pass: a segment that serves the path of
 * a backup's history at an index that the backup's log names, whatever its age.
 */
final class Retention {

  private static final Logger LOG = LoggerFactory.getLogger(Retention.class);
  private static final String MARKER_SUFFIX = ".json";

  private final ObjectStore store;
  private final StoreLayout layout;
  private final Duration retention;
  private final InstantSource clock;
  private final SnapshotRetention snapshots;

  Retention(
      final ObjectStore store,
      final StoreLayout layout,
      final Duration retention,
      final InstantSource clock) {
    this.store = store;
    this.layout = layout;
    this.retention = retention;
    this.clock = clock;
    snapshots = new SnapshotRetention(store);
  }

  /**
   * Applies retention to a log. The log's segments are those of the histories on the path to the
   * tenant's current history, which serve the log's journal, and the others, which don't: a history
   * that isn't on the path, and what a history on the path wrote after its successor took over.
   *
   * <p>It deletes nothing, and returns {@link MaintenanceReport#NONE}, if a backup's log can't be
   * read, as what it needs is unknown.
   *
   * @throws io.camunda.zeebe.eventplane.history.MissingManifestException while a manifest that the
   *     path of the current history, or of a backup's, needs is missing
   * @throws io.camunda.zeebe.eventplane.history.InconsistentHistoryException if the manifests
   *     contradict each other
   */
  MaintenanceReport run(
      final LogId log, final HistoryManifest current, final HistoryLineage lineage)
      throws IOException {
    final var path = lineage.path(current.historyId(), log.partition());
    if (path.isEmpty()) {
      // Without the log in the current history, every segment would be off the path, and every old
      // one deleted, as when the current history's part for this partition has yet to be written.
      LOG.warn(
          "Not applying retention to {}, as the current history {} doesn't have it",
          log,
          current.historyId());
      return MaintenanceReport.NONE;
    }

    final BackupHolds backups;
    try {
      // before anything is deleted: a backup's log that can't be read could protect any segment
      backups = BackupHolds.read(store, log, lineage);
    } catch (final UnreadableBackupLogException e) {
      LOG.warn("Not applying retention to {}, as a backup log can't be read", log, e);
      return MaintenanceReport.NONE;
    }

    final var threshold = clock.instant().minus(retention);
    final var onPath = new ArrayList<StoredSegment>();
    final var offPath = new ArrayList<StoredSegment>();
    final var held = new HashSet<String>();
    for (final var history : layout.histories(log)) {
      final var stretch = stretchOf(path, history);
      for (final var segment : Listing.segments(store, log, history)) {
        (isOnPath(stretch, segment) ? onPath : offPath).add(segment);
        if (backups.holds(history, segment)) {
          held.add(segment.key());
        }
      }
    }

    // snapshots first: a snapshot that outlives the segments after it couldn't be restored
    final var kept = snapshots.run(log, path, threshold);
    var report = new MaintenanceReport(0, 0, 0, kept.deleted());
    report = report.plus(trimPath(log, current, onPath, held, threshold, kept.bound()));
    report = report.plus(trimOffPath(log, offPath, held, threshold));
    trimMarkers(log);
    return report;
  }

  /** Of the segments off the path, deletes those that are old, except the held ones. */
  private MaintenanceReport trimOffPath(
      final LogId log,
      final List<StoredSegment> offPath,
      final Set<String> held,
      final Instant threshold)
      throws IOException {
    int deleted = 0;
    int spared = 0;
    for (final var segment : offPath) {
      if (segment.isUploadedBefore(threshold)) {
        if (held.contains(segment.key())) {
          spared++;
        } else {
          store.delete(segment.key());
          deleted++;
        }
      }
    }
    logSpared(log, spared, "off the path");
    return new MaintenanceReport(0, deleted, 0);
  }

  /**
   * Of the segments on the path, deletes those below the cutoff that are old, except the held ones:
   * the cutoff is the first index of the earliest segment that is young or holds an entry at or
   * after the snapshot bound, which the oldest kept snapshot needs. With no young segment there is
   * no cutoff, and nothing is deleted, so that a log that stopped is not lost. Writes the retention
   * marker first, also when backups hold all the segments below the cutoff, so that consumers don't
   * see them.
   */
  private MaintenanceReport trimPath(
      final LogId log,
      final HistoryManifest current,
      final List<StoredSegment> onPath,
      final Set<String> held,
      final Instant threshold,
      final long snapshotBound)
      throws IOException {
    if (onPath.stream().allMatch(segment -> segment.isUploadedBefore(threshold))) {
      return MaintenanceReport.NONE;
    }
    final var atCutoff =
        onPath.stream()
            .filter(
                segment ->
                    !segment.isUploadedBefore(threshold) || segment.lastIndex() >= snapshotBound)
            .min(Comparator.comparingLong(StoredSegment::firstIndex));
    if (atCutoff.isEmpty()) {
      return MaintenanceReport.NONE;
    }
    final long cutoff = atCutoff.get().firstIndex();
    final var below =
        onPath.stream()
            .filter(segment -> segment.lastIndex() < cutoff && segment.isUploadedBefore(threshold))
            .toList();
    if (below.isEmpty()) {
      return MaintenanceReport.NONE;
    }
    final var expired = below.stream().filter(segment -> !held.contains(segment.key())).toList();
    logSpared(log, below.size() - expired.size(), "below the cutoff");

    int markers = 0;
    // The marker stays as it is while it is at the cutoff already, as segments that backups hold
    // are below the cutoff for good, and a new marker for each run would only churn.
    if (!isMarkedAt(log, current.historyId(), cutoff)) {
      try {
        writeMarker(log, current.historyId(), atCutoff.get());
      } catch (final NoSuchObjectException e) {
        LOG.info(
            "Not applying retention to {} yet, as the segment '{}' at the cutoff is gone",
            log,
            atCutoff.get().key(),
            e);
        return MaintenanceReport.NONE;
      }
      markers = 1;
    }
    for (final var segment : expired) {
      store.delete(segment.key());
    }
    return new MaintenanceReport(0, expired.size(), markers);
  }

  /** Writes a retention marker for the cutoff, the first index of the segment. */
  private void writeMarker(final LogId log, final UUID history, final StoredSegment atCutoff)
      throws IOException {
    final long cutoff = atCutoff.firstIndex();
    final var markerKey = ObjectNames.retentionMarker(log, Ulid.next());
    store.put(markerKey, marker(history, cutoff, firstPosition(atCutoff)));
    LOG.info("Wrote the retention marker '{}' for the cutoff at index {}", markerKey, cutoff);
  }

  /** Whether the newest retention marker of the log is of this history and cutoff already. */
  private boolean isMarkedAt(final LogId log, final UUID history, final long cutoff)
      throws IOException {
    final var keys = markerKeys(log);
    if (keys.isEmpty()) {
      return false;
    }
    return store
        .get(keys.last())
        .map(content -> new String(content, StandardCharsets.UTF_8))
        .filter(marker -> marker.startsWith(markerStart(history, cutoff)))
        .isPresent();
  }

  /** Logs how many old segments of the log were kept, as backups need them. */
  private static void logSpared(final LogId log, final int spared, final String where) {
    if (spared > 0) {
      LOG.info("Kept {} old segments of {} {}, as backups need them", spared, log, where);
    }
  }

  /**
   * Deletes all retention markers of the log but the newest, which is the one that applies. A
   * listing may not show the marker just written yet; then the one before it stays until the next
   * run.
   */
  private void trimMarkers(final LogId log) throws IOException {
    final var keys = markerKeys(log);
    for (final var key : keys.headSet(keys.isEmpty() ? "" : keys.last())) {
      store.delete(key);
    }
  }

  /** The keys of the log's retention markers, in key order. */
  private SortedSet<String> markerKeys(final LogId log) throws IOException {
    final var prefix = ObjectNames.retentionPrefix(log);
    final var keys = new TreeSet<String>();
    for (final var object : Listing.objects(store, prefix)) {
      final var name = object.key().substring(prefix.length());
      if (name.endsWith(MARKER_SUFFIX)
          && Ulid.isValid(name.substring(0, name.length() - MARKER_SUFFIX.length()))) {
        keys.add(object.key());
      }
    }
    return keys;
  }

  /** The position of the segment's first record, or -1 if it has none: its footer says. */
  private long firstPosition(final StoredSegment segment) throws IOException {
    final long start = Math.max(0, segment.size() - Footer.USUAL_TAIL_LENGTH);
    final var tail = store.getRange(segment.key(), start, Footer.USUAL_TAIL_LENGTH);
    return Footer.read(ByteBuffer.wrap(tail)).firstPosition();
  }

  private byte[] marker(final UUID history, final long firstIndex, final long firstPosition) {
    return (markerStart(history, firstIndex)
            + "\"firstPosition\":%d,\"createdAt\":\"%s\"}"
                .formatted(firstPosition, clock.instant()))
        .getBytes(StandardCharsets.UTF_8);
  }

  /** What a marker of the history and the first index starts with. */
  private static String markerStart(final UUID history, final long firstIndex) {
    return "{\"version\":1,\"history\":\"%s\",\"firstIndex\":%d,".formatted(history, firstIndex);
  }

  /** The history's stretch of the path, or null if the history isn't on it. */
  static @Nullable Stretch stretchOf(final List<Stretch> path, final UUID history) {
    return path.stream()
        .filter(stretch -> stretch.history().equals(history))
        .findFirst()
        .orElse(null);
  }

  /**
   * Whether the segment serves the path: its history is on it, and the segment starts at or before
   * the entry where the next history took over, if one did.
   */
  static boolean isOnPath(final @Nullable Stretch stretch, final StoredSegment segment) {
    if (stretch == null) {
      return false;
    }
    final var end = stretch.end();
    return end == null || segment.firstIndex() <= end.index();
  }
}
