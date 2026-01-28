/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BackupRangesTest {

  @Test
  void shouldReturnEmptyCollectionForEmptyMarkers() {
    // given
    final var markers = List.<BackupRangeMarker>of();

    // when
    final var ranges = BackupRanges.fromMarkers(markers);

    // then
    assertThat(ranges).isEmpty();
  }

  @Test
  void shouldCreateCompleteRangeForStartAndEndMarkers() {
    // given
    final var markers =
        List.<BackupRangeMarker>of(new BackupRangeMarker.Start(1), new BackupRangeMarker.End(2));

    // when
    final var ranges = BackupRanges.fromMarkers(markers);

    // then
    assertThat(ranges).containsExactly(new BackupRange.Complete(1, 2));
  }

  @Test
  void shouldCreateIncompleteRangeWithDeletions() {
    // given
    final var markers =
        List.<BackupRangeMarker>of(
            new BackupRangeMarker.Start(1),
            new BackupRangeMarker.Deletion(2),
            new BackupRangeMarker.End(3));

    // when
    final var ranges = BackupRanges.fromMarkers(markers);

    // then
    assertThat(ranges).containsExactly(new BackupRange.Incomplete(1, 3, Set.of(2L)));
  }

  @Test
  void shouldIgnoreUnmatchedEndMarkers() {
    // given
    final var markers = List.<BackupRangeMarker>of(new BackupRangeMarker.End(1));

    // when
    final var ranges = BackupRanges.fromMarkers(markers);

    // then
    assertThat(ranges).isEmpty();
  }

  @Test
  void shouldIgnoreUnmatchedStartMarkers() {
    // given
    final var markers = List.<BackupRangeMarker>of(new BackupRangeMarker.Start(1));

    // when
    final var ranges = BackupRanges.fromMarkers(markers);

    // then
    assertThat(ranges).isEmpty();
  }

  @Test
  void shouldCreateMultipleRanges() {
    // given
    final var markers =
        List.<BackupRangeMarker>of(
            new BackupRangeMarker.Start(1),
            new BackupRangeMarker.End(2),
            new BackupRangeMarker.Start(3),
            new BackupRangeMarker.Deletion(4),
            new BackupRangeMarker.End(5));

    // when
    final var ranges = BackupRanges.fromMarkers(markers);

    // then
    assertThat(ranges)
        .containsExactly(
            new BackupRange.Complete(1, 2), new BackupRange.Incomplete(3, 5, Set.of(4L)));
  }

  @Test
  void shouldSortMarkersByCheckpointIdAndType() {
    // given
    final var markers =
        List.<BackupRangeMarker>of(
            new BackupRangeMarker.End(3),
            new BackupRangeMarker.Deletion(2),
            new BackupRangeMarker.Start(1));

    // when
    final var ranges = BackupRanges.fromMarkers(markers);

    // then
    assertThat(ranges).containsExactly(new BackupRange.Incomplete(1, 3, Set.of(2L)));
  }

  @Test
  void shouldCreateRangeWithMultipleDeletions() {
    // given
    final var markers =
        List.<BackupRangeMarker>of(
            new BackupRangeMarker.Start(1),
            new BackupRangeMarker.Deletion(2),
            new BackupRangeMarker.Deletion(3),
            new BackupRangeMarker.End(4));

    // when
    final var ranges = BackupRanges.fromMarkers(markers);

    // then
    assertThat(ranges).containsExactly(new BackupRange.Incomplete(1, 4, Set.of(2L, 3L)));
  }

  @Test
  void shouldUseLastStartWhenMultipleStartsArePresent() {
    // given
    final var markers =
        List.<BackupRangeMarker>of(
            new BackupRangeMarker.Start(1),
            new BackupRangeMarker.Start(2),
            new BackupRangeMarker.End(3));

    // when
    final var ranges = BackupRanges.fromMarkers(markers);

    // then
    assertThat(ranges).containsExactly(new BackupRange.Complete(2, 3));
  }

  @Test
  void shouldUseLastEndWhenMultipleEndsArePresent() {
    // given
    final var markers =
        List.<BackupRangeMarker>of(
            new BackupRangeMarker.Start(1),
            new BackupRangeMarker.End(2),
            new BackupRangeMarker.End(3));

    // when
    final var ranges = BackupRanges.fromMarkers(markers);

    // then
    assertThat(ranges).containsExactly(new BackupRange.Complete(1, 3));
  }

  @Test
  void shouldIgnoreDeletionsBeforeAnyStart() {
    // given
    final var markers =
        List.<BackupRangeMarker>of(
            new BackupRangeMarker.Deletion(1),
            new BackupRangeMarker.Start(2),
            new BackupRangeMarker.End(3));

    // when
    final var ranges = BackupRanges.fromMarkers(markers);

    // then
    assertThat(ranges).containsExactly(new BackupRange.Complete(2, 3));
  }

  @Test
  void shouldDiscardDeletionsAfterEnd() {
    // given
    final var markers =
        List.<BackupRangeMarker>of(
            new BackupRangeMarker.Start(1),
            new BackupRangeMarker.End(2),
            new BackupRangeMarker.Deletion(3));

    // when
    final var ranges = BackupRanges.fromMarkers(markers);

    // then
    assertThat(ranges).containsExactly(new BackupRange.Complete(1, 2));
  }

  @Test
  void shouldHandleMarkersWithSameCheckpointId() {
    // given
    final var markers =
        List.<BackupRangeMarker>of(
            new BackupRangeMarker.Deletion(1),
            new BackupRangeMarker.End(1),
            new BackupRangeMarker.Start(1));

    // when
    final var ranges = BackupRanges.fromMarkers(markers);

    // then
    assertThat(ranges).containsExactly(new BackupRange.Incomplete(1, 1, Set.of(1L)));
  }

  @Test
  void shouldHandleComplexScenario() {
    // given
    final var markers =
        List.<BackupRangeMarker>of(
            new BackupRangeMarker.Deletion(1),
            new BackupRangeMarker.Start(2),
            new BackupRangeMarker.Deletion(3),
            new BackupRangeMarker.Start(4),
            new BackupRangeMarker.End(5),
            new BackupRangeMarker.End(6),
            new BackupRangeMarker.Deletion(7),
            new BackupRangeMarker.Start(8),
            new BackupRangeMarker.End(9));

    // when
    final var ranges = BackupRanges.fromMarkers(markers);

    // then
    assertThat(ranges)
        .containsExactly(new BackupRange.Complete(4, 6), new BackupRange.Complete(8, 9));
  }
}
