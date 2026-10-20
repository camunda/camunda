/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.eventplane.history.HistoryLineage.Stretch;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class HistoryLineageTest {

  private static final UUID A = UUID.fromString("00000000-0000-4000-8000-00000000000a");
  private static final UUID B = UUID.fromString("00000000-0000-4000-8000-00000000000b");
  private static final UUID C = UUID.fromString("00000000-0000-4000-8000-00000000000c");
  private static final Instant NOON = Instant.parse("2026-10-19T12:00:00Z");

  @Test
  void shouldTakeTheHistoryMadeLastAsCurrentWhateverItTakesOver() {
    // given B restored from A, on a broker whose clock is behind the one that made A
    final var a = history(A, 100, Map.of(1, Base.NOTHING));
    final var b = history(B, 0, Map.of(1, new Base(A, 900, 400)));

    // when
    final var current = new HistoryLineage(List.of(a, b)).current("cluster-1", "default");

    // then A stays current, and the Event Plane has to raise an alert
    assertThat(current).contains(a);
  }

  @Test
  void shouldTakeTheLargerIdAsTextAsCurrentOfHistoriesMadeAtTheSameTime() {
    // given ids that UUID.compareTo, comparing signed numbers, orders the other way round
    final var low = history(UUID.fromString("7fffffff-ffff-4fff-8fff-ffffffffffff"), 0);
    final var high = history(UUID.fromString("80000000-0000-4000-8000-000000000000"), 0);

    // when
    final var current = new HistoryLineage(List.of(high, low)).current("cluster-1", "default");

    // then
    assertThat(current).contains(high);
  }

  @Test
  void shouldTakeOnlyTheTenantsOwnHistoriesAsCurrent() {
    // given
    final var lineage = new HistoryLineage(List.of(history(A, 0)));

    // when
    final var current = lineage.current("cluster-2", "default");

    // then
    assertThat(current).isEmpty();
  }

  @Test
  void shouldShareOnlyWhatBothTookOverAfterARestoreFromAnOlderBackup() {
    // given B and C restored from backups of A, C's taken later than B's
    final var lineage =
        new HistoryLineage(
            List.of(
                history(A, 0, Map.of(1, Base.NOTHING)),
                history(B, 1, Map.of(1, new Base(A, 900, 400))),
                history(C, 2, Map.of(1, new Base(A, 1300, 600)))));

    // when
    final var bAndC = lineage.lastShared(B, C, 1);
    final var aAndC = lineage.lastShared(A, C, 1);

    // then a reader moving from B to C gets A's records 401 to 600 again
    assertThat(bAndC).isEqualTo(new Base(A, 900, 400)).isEqualTo(lineage.lastShared(C, B, 1));
    assertThat(aAndC).isEqualTo(new Base(A, 1300, 600));
  }

  @Test
  void shouldSharePartOfTheHistoryInBetweenAfterARestoreFromALaterBackup() {
    // given C restored from a backup of B, which was restored from one of A
    final var lineage =
        new HistoryLineage(
            List.of(
                history(A, 0, Map.of(1, Base.NOTHING)),
                history(B, 1, Map.of(1, new Base(A, 900, 400))),
                history(C, 2, Map.of(1, new Base(B, 1300, 600)))));

    // when
    final var bAndC = lineage.lastShared(B, C, 1);
    final var aAndC = lineage.lastShared(A, C, 1);

    // then
    assertThat(bAndC).isEqualTo(new Base(B, 1300, 600));
    assertThat(aAndC).isEqualTo(new Base(A, 900, 400));
  }

  @Test
  void shouldShareNothingInAPartitionThatAHistoryLeavesOutOrPurges() {
    // given B restored from a backup without partition 2, and C a purge of B
    final var lineage =
        new HistoryLineage(
            List.of(
                history(A, 0, Map.of(1, Base.NOTHING, 2, Base.NOTHING)),
                history(B, 1, Map.of(1, new Base(A, 900, 400))),
                history(C, 2, Map.of(1, Base.NOTHING))));

    // when
    final var leftOut = lineage.lastShared(A, B, 2);
    final var purged = lineage.lastShared(B, C, 1);

    // then
    assertThat(leftOut).isEqualTo(Base.NOTHING);
    assertThat(purged).isEqualTo(Base.NOTHING);
  }

  @Test
  void shouldRequireACheckpointInItsSourcesOwnEntries() {
    // given C taking over B at 700, before B's own entries start after 900
    final var lineage =
        new HistoryLineage(
            List.of(
                history(A, 0, Map.of(1, Base.NOTHING)),
                history(B, 1, Map.of(1, new Base(A, 900, 400))),
                history(C, 2, Map.of(1, new Base(B, 700, 300)))));

    // when, then
    assertThatIllegalArgumentException()
        .isThrownBy(() -> lineage.lastShared(B, C, 1))
        .isInstanceOfSatisfying(
            InconsistentHistoryException.class, e -> assertThat(e.history()).isEqualTo(B))
        .withMessageContaining("to take over " + B + " after that history's own entries")
        .withMessageContaining("but got index 700 and position 300");
  }

  @Test
  void shouldRequireTwoCheckpointsAtOneIndexToHoldTheSamePosition() {
    // given
    final var lineage =
        new HistoryLineage(
            List.of(
                history(A, 0, Map.of(1, Base.NOTHING)),
                history(B, 1, Map.of(1, new Base(A, 900, 400))),
                history(C, 2, Map.of(1, new Base(A, 900, 401)))));

    // when, then
    assertThatIllegalArgumentException()
        .isThrownBy(() -> lineage.lastShared(B, C, 1))
        .isInstanceOfSatisfying(
            InconsistentHistoryException.class, e -> assertThat(e.history()).isEqualTo(A))
        .withMessage(
            "Expected two checkpoints at index 900 of history %s to hold one position, but got 400 and 401"
                .formatted(A));
  }

  @Test
  void shouldWaitForAMissingManifestThatMayStillArrive() {
    // given B, whose source A's manifest isn't there yet
    final var lineage =
        new HistoryLineage(List.of(history(B, 1, Map.of(1, new Base(A, 900, 400))), history(C, 2)));

    // when, then
    assertThatIllegalStateException()
        .isThrownBy(() -> lineage.lastShared(B, C, 1))
        .isInstanceOfSatisfying(
            MissingManifestException.class, e -> assertThat(e.history()).isEqualTo(A))
        .withMessage("Expected a manifest of history %s, but got none yet".formatted(A));
    assertThatIllegalStateException()
        .isThrownBy(() -> new HistoryLineage(List.of(history(C, 2))).lastShared(B, C, 1))
        .isInstanceOfSatisfying(
            MissingManifestException.class, e -> assertThat(e.history()).isEqualTo(B));
  }

  @Test
  void shouldRejectManifestsThatContradictEachOther() {
    // a cycle: one of its checkpoints lies before its source's own entries
    final var cycle =
        new HistoryLineage(
            List.of(
                history(A, 0, Map.of(1, new Base(B, 900, 400))),
                history(B, 1, Map.of(1, new Base(A, 1300, 600))),
                history(C, 2, Map.of(1, Base.NOTHING))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> cycle.lastShared(A, C, 1))
        .withMessageContaining("after that history's own entries");

    // a source of another cluster
    final var otherTenant =
        new HistoryLineage(
            List.of(
                new HistoryManifest(1, "cluster-2", "default", A, NOON, Map.of(1, Base.NOTHING)),
                history(B, 1, Map.of(1, new Base(A, 900, 400))),
                history(C, 2, Map.of(1, Base.NOTHING))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> otherTenant.lastShared(B, C, 1))
        .withMessageContaining("to belong to the physical tenant of " + B);

    // histories of different tenants
    assertThatIllegalArgumentException()
        .isThrownBy(() -> otherTenant.lastShared(A, C, 1))
        .withMessageContaining("to belong to one physical tenant");

    // two manifests of one history that differ
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new HistoryLineage(List.of(history(A, 0), history(A, 1))))
        .isInstanceOfSatisfying(
            InconsistentHistoryException.class, e -> assertThat(e.history()).isEqualTo(A))
        .withMessageContaining(
            "Expected one manifest of history " + A + ", but got two that differ");
  }

  @Test
  void shouldRejectTheSharedPartOfAHistoryWithItself() {
    // given
    final var lineage = new HistoryLineage(List.of(history(A, 0)));

    // when, then
    assertThatIllegalArgumentException()
        .isThrownBy(() -> lineage.lastShared(A, A, 1))
        .withMessage("Expected two different histories, but got %s twice".formatted(A));
  }

  @Test
  void shouldCountTheSameManifestFromSeveralStoresOnce() {
    // given
    final var a = history(A, 0, Map.of(1, Base.NOTHING));

    // when
    final var lineage = new HistoryLineage(List.of(a, a));

    // then
    assertThat(lineage.current("cluster-1", "default")).contains(a);
    assertThat(lineage.manifests()).containsExactly(a);
  }

  @Test
  void shouldGiveAccessToItsManifestsOldestFirst() {
    // given
    final var a = history(A, 0);
    final var b = history(B, 2);
    final var c = history(C, 1);
    final var lineage = new HistoryLineage(List.of(b, c, a));

    // when, then
    assertThat(lineage.manifests()).containsExactly(a, c, b);
    assertThat(lineage.manifest(B)).contains(b);
    assertThat(lineage.manifest(UUID.randomUUID())).isEmpty();
    assertThatThrownBy(() -> lineage.manifests().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldWalkAPathOldestFirstThroughEveryHistoryItTookOver() {
    // given C restored from a backup of B, which was restored from one of A
    final var lineage =
        new HistoryLineage(
            List.of(
                history(A, 0, Map.of(1, Base.NOTHING)),
                history(B, 1, Map.of(1, new Base(A, 900, 400))),
                history(C, 2, Map.of(1, new Base(B, 1300, 600)))));

    // when
    final var path = lineage.path(C, 1);

    // then each stretch starts where its history took over, and ends where the next one did
    assertThat(path)
        .containsExactly(
            new Stretch(A, Base.NOTHING, new Base(A, 900, 400)),
            new Stretch(B, new Base(A, 900, 400), new Base(B, 1300, 600)),
            new Stretch(C, new Base(B, 1300, 600), null));
    assertThat(lineage.path(B, 1))
        .containsExactly(
            new Stretch(A, Base.NOTHING, new Base(A, 900, 400)),
            new Stretch(B, new Base(A, 900, 400), null));
  }

  @Test
  void shouldHaveOneOpenStretchOnAPathOfAHistoryThatTookOverNothing() {
    // given
    final var lineage = new HistoryLineage(List.of(history(A, 0), history(B, 1)));

    // when, then
    assertThat(lineage.path(A, 1)).containsExactly(new Stretch(A, Base.NOTHING, null));
    assertThat(lineage.path(B, 1)).containsExactly(new Stretch(B, Base.NOTHING, null));
  }

  @Test
  void shouldHaveNoPathThroughAPartitionThatAHistoryLeavesOut() {
    // given
    final var lineage = new HistoryLineage(List.of(history(A, 0, Map.of(1, Base.NOTHING))));

    // when, then
    assertThat(lineage.path(A, 2)).isEmpty();
  }

  @Test
  void shouldWalkOnePathPerPartition() {
    // given B restored from A in partition 1, and a purge of A in partition 2
    final var lineage =
        new HistoryLineage(
            List.of(
                history(A, 0, Map.of(1, Base.NOTHING, 2, Base.NOTHING)),
                history(B, 1, Map.of(1, new Base(A, 900, 400), 2, Base.NOTHING))));

    // when, then
    assertThat(lineage.path(B, 1)).extracting(Stretch::history).containsExactly(A, B);
    assertThat(lineage.path(B, 2)).extracting(Stretch::history).containsExactly(B);
  }

  @Test
  void shouldNotWalkAPathThatCannotEnd() {
    // given a cycle
    final var lineage =
        new HistoryLineage(
            List.of(
                history(A, 0, Map.of(1, new Base(B, 900, 400))),
                history(B, 1, Map.of(1, new Base(A, 1300, 600)))));

    // when, then
    assertThatIllegalArgumentException()
        .isThrownBy(() -> lineage.path(A, 1))
        .isInstanceOf(InconsistentHistoryException.class);
  }

  @Test
  void shouldNotWalkAPathThroughASourceWithoutThePartition() {
    // given B taking over partition 2 of A, which A has no partition 2 for
    final var lineage =
        new HistoryLineage(
            List.of(
                history(A, 0, Map.of(1, Base.NOTHING)),
                history(B, 1, Map.of(2, new Base(A, 900, 400)))));

    // when, then
    assertThatIllegalArgumentException()
        .isThrownBy(() -> lineage.path(B, 2))
        .withMessage(
            "Expected history %s to have partition 2, which %s takes over, but got none"
                .formatted(A, B));
  }

  @Test
  void shouldNotWalkAPathWhileAManifestIsMissing() {
    // given
    final var lineage =
        new HistoryLineage(List.of(history(B, 1, Map.of(1, new Base(A, 900, 400)))));

    // when, then
    assertThatThrownBy(() -> lineage.path(B, 1)).isInstanceOf(MissingManifestException.class);
    assertThatThrownBy(() -> lineage.path(C, 1))
        .isInstanceOfSatisfying(
            MissingManifestException.class, e -> assertThat(e.history()).isEqualTo(C));
  }

  /** A history of cluster-1's default tenant, made some seconds after noon. */
  private static HistoryManifest history(
      final UUID id, final int seconds, final Map<Integer, Base> partitions) {
    return new HistoryManifest(
        1, "cluster-1", "default", id, NOON.plusSeconds(seconds), partitions);
  }

  /** A history that takes over nothing, in its one partition. */
  private static HistoryManifest history(final UUID id, final int seconds) {
    return history(id, seconds, Map.of(1, Base.NOTHING));
  }
}
