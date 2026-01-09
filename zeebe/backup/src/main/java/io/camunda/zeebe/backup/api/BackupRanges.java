/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import io.camunda.zeebe.backup.api.BackupRangeMarker.Deletion;
import io.camunda.zeebe.backup.api.BackupRangeMarker.End;
import io.camunda.zeebe.backup.api.BackupRangeMarker.Start;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.SequencedCollection;
import org.agrona.collections.LongHashSet;

public interface BackupRanges {

  /**
   * Constructs backup ranges from the given markers. Invalid ranges (e.g., missing start or end)
   * are silently dropped.
   */
  static SequencedCollection<BackupRange> fromMarkers(final Collection<BackupRangeMarker> markers) {
    final var sortedMarkers =
        markers.stream().sorted(Comparator.comparingLong(BackupRangeMarker::checkpointId)).toList();
    // Ideally we have one start and one end per range
    final var estimatedSize = markers.size() / 2;
    final var ranges = new ArrayList<BackupRange>(estimatedSize);

    Start currentRangeStart = null;
    End currentRangeEnd = null;
    final var deletionsInCurrentRange = new LongHashSet();
    for (final var marker : sortedMarkers) {
      switch (marker) {
        case final Start start -> {
          final var range =
              finalizeRange(currentRangeStart, currentRangeEnd, deletionsInCurrentRange);
          if (range != null) {
            ranges.add(range);
          }
          currentRangeStart = start;
          currentRangeEnd = null;
          deletionsInCurrentRange.clear();
        }
        case Deletion(final long checkpointId) -> deletionsInCurrentRange.add(checkpointId);
        case final End end -> currentRangeEnd = end;
      }
    }
    // We need to finalize the last range because the loop above only finalizes ranges when a new
    // start is found.
    final var range = finalizeRange(currentRangeStart, currentRangeEnd, deletionsInCurrentRange);
    if (range != null) {
      ranges.add(range);
    }

    return ranges;
  }

  private static BackupRange finalizeRange(
      final Start currentRangeStart,
      final End currentRangeEnd,
      final LongHashSet deletionsInCurrentRange) {
    if (currentRangeStart == null || currentRangeEnd == null) {
      return null;
    }
    if (deletionsInCurrentRange.isEmpty()) {
      return new BackupRange.Complete(
          currentRangeStart.checkpointId(), currentRangeEnd.checkpointId());
    } else {
      return new BackupRange.Incomplete(
          currentRangeStart.checkpointId(),
          currentRangeEnd.checkpointId(),
          deletionsInCurrentRange);
    }
  }
}
