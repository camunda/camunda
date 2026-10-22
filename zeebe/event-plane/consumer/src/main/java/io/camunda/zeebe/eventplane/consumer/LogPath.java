/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.camunda.zeebe.eventplane.history.HistoryLineage;
import io.camunda.zeebe.eventplane.history.HistoryLineage.Stretch;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.history.MissingManifestException;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A log's current history and its path, as the manifests in the store say: the stretches of journal
 * that each history on it appended, oldest first. A history's segments serve the path up to the end
 * of its stretch, the current history's all of it.
 */
final class LogPath {

  private final LogId log;
  private final HistoryLineage lineage;
  private final UUID current;
  private final List<Stretch> stretches;

  private LogPath(
      final LogId log,
      final HistoryLineage lineage,
      final UUID current,
      final List<Stretch> stretches) {
    this.log = log;
    this.lineage = lineage;
    this.current = current;
    this.stretches = stretches;
  }

  /**
   * The log's path in a lineage of its tenant, or null while there is none: no manifest yet, the
   * current history leaves the partition out, or a manifest on the path hasn't arrived.
   */
  static @Nullable LogPath of(final HistoryLineage lineage, final LogId log) {
    final var current = lineage.current(log.cluster(), log.tenant());
    if (current.isEmpty()) {
      return null;
    }
    final List<Stretch> stretches;
    try {
      stretches = lineage.path(current.get().historyId(), log.partition());
    } catch (final MissingManifestException e) {
      return null;
    }
    return stretches.isEmpty()
        ? null
        : new LogPath(log, lineage, current.get().historyId(), stretches);
  }

  UUID current() {
    return current;
  }

  int size() {
    return stretches.size();
  }

  UUID history(final int stretch) {
    return stretches.get(stretch).history();
  }

  /** The directory of the segments of a stretch's history. */
  String directory(final int stretch) {
    return ObjectNames.segmentsPrefix(log, history(stretch));
  }

  /** The last index that a stretch's history's segments serve on the path. */
  long limit(final int stretch) {
    final var end = stretches.get(stretch).end();
    return end == null ? Long.MAX_VALUE : end.index();
  }

  /** The position of the record at a stretch's limit; only for a stretch that ends. */
  long limitPosition(final int stretch) {
    return Objects.requireNonNull(stretches.get(stretch).end()).position();
  }

  /** The stretch that appended the entry at an index of 1 or more. */
  int stretchOf(final long index) {
    for (int i = 0; i < stretches.size(); i++) {
      if (index <= limit(i)) {
        return i;
      }
    }
    return stretches.size() - 1;
  }

  /**
   * The last entry that another history of the tenant shares with the current one, or null while a
   * manifest that it needs is missing.
   */
  @Nullable Base shared(final UUID history) {
    try {
      return lineage.lastShared(history, current, log.partition());
    } catch (final MissingManifestException e) {
      return null;
    }
  }
}
