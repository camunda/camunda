/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class RetentionTest extends StoreTestCase {

  private static final UUID RESTORED = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
  private static final UUID OTHER = UUID.fromString("c0ffee00-1234-4abc-8def-0123456789ab");
  private static final Duration RETENTION = Duration.ofDays(7);
  private static final Duration TEN_DAYS = Duration.ofDays(10);
  private static final Duration THREE_DAYS = Duration.ofDays(3);
  private static final Duration ONE_DAY = Duration.ofDays(1);

  @BeforeEach
  void putTheFirstHistory() throws Exception {
    segments.putHistory(HISTORY, Duration.ofDays(30), Base.NOTHING);
  }

  /** Retention only: a rollup would need segments older than ten years. */
  private Maintenance maintenance() {
    return new Maintenance(
        store, new MaintenanceConfig(Duration.ofDays(3650), 1 << 20, RETENTION), clock);
  }

  @Test
  void shouldWriteTheMarkerBeforeItDeletesTheOldSegmentsBelowTheCutoff() throws Exception {
    // given
    final var first = segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    final var second = segments.put(HISTORY, 0, Duration.ofDays(9), entries(4, 6));
    segments.put(HISTORY, 0, THREE_DAYS, entries(7, 9));
    segments.put(HISTORY, 0, ONE_DAY, entries(10, 12));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(0, 2, 1));
    assertThat(segments.segments(HISTORY)).containsExactly("7-9 L0", "10-12 L0");
    final var markers = segments.markers();
    assertThat(markers).hasSize(1);
    assertThat(store.writes()).hasSize(3).first().isEqualTo("PUT " + markers.getFirst());
    assertThat(store.writes())
        .containsExactlyInAnyOrder(
            "PUT " + markers.getFirst(), "DELETE " + first, "DELETE " + second);
  }

  @Test
  void shouldNameTheCurrentHistoryAndTheFirstEntryThatIsKept() throws Exception {
    // given: entry 7 holds the positions 13 and 14
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 6));
    segments.put(HISTORY, 0, THREE_DAYS, entries(7, 9));

    // when
    maintenance().runOnce();

    // then
    final var marker = store.get(segments.markers().getFirst()).orElseThrow();
    assertThat(new String(marker, StandardCharsets.UTF_8))
        .isEqualTo(
            "{\"version\":1,\"history\":\"%s\",\"firstIndex\":7,\"firstPosition\":13,\"createdAt\":\"%s\"}"
                .formatted(HISTORY, clock.instant()));
  }

  @Test
  void shouldOnlyDeleteSegmentsBelowTheCutoff() throws Exception {
    // given: the segment of 7 to 9 is old, but not below the first young one, which starts at 4
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, ONE_DAY, entries(4, 6));
    segments.put(HISTORY, 0, TEN_DAYS, entries(7, 9));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(0, 1, 1));
    assertThat(segments.segments(HISTORY)).containsExactly("4-6 L0", "7-9 L0");
  }

  @Test
  void shouldKeepASegmentThatHoldsEntriesAboveTheCutoff() throws Exception {
    // given: the old segment of 1 to 6 reaches the cutoff at 4
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 6));
    segments.put(HISTORY, 0, ONE_DAY, entries(4, 9));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(MaintenanceReport.NONE);
    assertThat(segments.segments(HISTORY)).containsExactly("1-6 L0", "4-9 L0");
    assertThat(segments.markers()).isEmpty();
  }

  @Test
  void shouldKeepEverythingWhileNoSegmentIsYoungerThanTheRetention() throws Exception {
    // given: a log that stopped uploading
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, Duration.ofDays(9), entries(4, 6));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(MaintenanceReport.NONE);
    assertThat(segments.segments(HISTORY)).containsExactly("1-3 L0", "4-6 L0");
    assertThat(segments.markers()).isEmpty();
  }

  @Test
  void shouldNameMinusOneAsTheFirstPositionOfASegmentWithoutRecords() throws Exception {
    // given: the segment at the cutoff holds one entry that has no records
    final var withoutRecords = List.of(journal.initial());
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 15));
    segments.put(HISTORY, 0, ONE_DAY, withoutRecords);

    // when
    maintenance().runOnce();

    // then
    final var marker = store.get(segments.markers().getFirst()).orElseThrow();
    assertThat(new String(marker, StandardCharsets.UTF_8))
        .contains("\"firstIndex\":16,\"firstPosition\":-1,");
  }

  @Test
  void shouldDeleteSegmentsOfAHistoryOffThePathByAge() throws Exception {
    // given: another history that the current one doesn't descend from
    segments.putHistory(OTHER, Duration.ofDays(40), Base.NOTHING);
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, ONE_DAY, entries(4, 6));
    segments.put(OTHER, 0, TEN_DAYS, entries(1, 3));
    segments.put(OTHER, 0, ONE_DAY, entries(4, 6));

    // when
    final var report = maintenance().runOnce();

    // then: no marker, as nothing on the path goes
    assertThat(report).isEqualTo(new MaintenanceReport(0, 1, 0));
    assertThat(segments.segments(OTHER)).containsExactly("4-6 L0");
    assertThat(segments.segments(HISTORY)).containsExactly("1-3 L0", "4-6 L0");
    assertThat(segments.markers()).isEmpty();
  }

  @Test
  void shouldFollowThePathThroughARestoredHistory() throws Exception {
    // given: a restore at entry 6, position 12, so the first history's entries from 7 on are off
    // the path, and the restored history is the current one
    segments.putHistory(RESTORED, Duration.ofDays(5), new Base(HISTORY, 6, 12));
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TEN_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, TEN_DAYS, entries(7, 9));
    segments.put(HISTORY, 0, ONE_DAY, entries(10, 12));
    segments.put(RESTORED, 0, THREE_DAYS, entries(7, 9));
    segments.put(RESTORED, 0, ONE_DAY, entries(10, 12));

    // when
    final var report = maintenance().runOnce();

    // then: the old segments of the path go, and the old one that it left
    assertThat(report).isEqualTo(new MaintenanceReport(0, 3, 1));
    assertThat(segments.segments(HISTORY)).containsExactly("10-12 L0");
    assertThat(segments.segments(RESTORED)).containsExactly("7-9 L0", "10-12 L0");
    final var marker = store.get(segments.markers().getFirst()).orElseThrow();
    assertThat(new String(marker, StandardCharsets.UTF_8))
        .contains("\"history\":\"" + RESTORED + "\",\"firstIndex\":7,\"firstPosition\":13,");
  }

  @Test
  void shouldKeepOnlyTheNewestMarker() throws Exception {
    // given
    segments.putMarker(THREE_DAYS);
    segments.putMarker(TWO_DAYS);
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, ONE_DAY, entries(4, 6));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(0, 1, 1));
    final var markers = segments.markers();
    assertThat(markers).hasSize(1);
    final var written = store.writes().stream().filter(write -> write.startsWith("PUT ")).toList();
    assertThat(written).containsExactly("PUT " + markers.getFirst());
  }

  @Test
  void shouldDeleteOlderMarkersEvenWhenItWritesNone() throws Exception {
    // given
    segments.putMarker(THREE_DAYS);
    final var newest = segments.putMarker(TWO_DAYS);
    segments.put(HISTORY, 0, ONE_DAY, entries(1, 3));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(MaintenanceReport.NONE);
    assertThat(segments.markers()).containsExactly(newest);
  }

  @Test
  void shouldChangeNothingTheSecondTime() throws Exception {
    // given
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, ONE_DAY, entries(4, 6));
    maintenance().runOnce();
    final var writesAfterTheFirst = store.writes().size();

    // when
    final var second = maintenance().runOnce();

    // then
    assertThat(second).isEqualTo(MaintenanceReport.NONE);
    assertThat(store.writes()).hasSize(writesAfterTheFirst);
    assertThat(segments.markers()).hasSize(1);
  }

  @Test
  void shouldDeleteMoreAsTheTimePasses() throws Exception {
    // given
    segments.put(HISTORY, 0, Duration.ofDays(6), entries(1, 3));
    segments.put(HISTORY, 0, Duration.ofDays(2), entries(4, 6));
    segments.put(HISTORY, 0, ONE_DAY, entries(7, 9));
    assertThat(maintenance().runOnce()).isEqualTo(MaintenanceReport.NONE);

    // when
    clock.advance(Duration.ofDays(3));
    final var report = maintenance().runOnce();

    // then: the first is 9 days old now, the second 5
    assertThat(report).isEqualTo(new MaintenanceReport(0, 1, 1));
    assertThat(segments.segments(HISTORY)).containsExactly("4-6 L0", "7-9 L0");
  }

  @Test
  void shouldKeepEverythingWithoutARetention() throws Exception {
    // given
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, ONE_DAY, entries(4, 6));
    final var keepAll =
        new Maintenance(store, new MaintenanceConfig(Duration.ofDays(3650), 1 << 20, null), clock);

    // when
    final var report = keepAll.runOnce();

    // then
    assertThat(report).isEqualTo(MaintenanceReport.NONE);
    assertThat(segments.segments(HISTORY)).containsExactly("1-3 L0", "4-6 L0");
  }

  @Test
  void shouldNotApplyRetentionToALogThatTheCurrentHistoryLeavesOut() throws Exception {
    // given: the current history has partition 2 only, so far
    segments.putHistory(RESTORED, Duration.ofDays(5), Map.of(2, Base.NOTHING));
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TEN_DAYS, entries(4, 6));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(MaintenanceReport.NONE);
    assertThat(segments.segments(HISTORY)).containsExactly("1-3 L0", "4-6 L0");
  }

  @Test
  void shouldNotApplyRetentionWhileAManifestIsMissing() throws Exception {
    // given: the current history takes over one whose manifest isn't there
    segments.putHistory(RESTORED, Duration.ofDays(5), new Base(OTHER, 6, 12));
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(RESTORED, 0, ONE_DAY, entries(7, 9));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(MaintenanceReport.NONE);
    assertThat(segments.segments(HISTORY)).containsExactly("1-3 L0");
  }

  @Test
  void shouldDeleteOldSegmentsBelowTheCutoffWhateverSnapshotsExist() throws Exception {
    // given: a young snapshot at index 5, which no segment is kept for: entry 6 is in the old 4-6
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, Duration.ofDays(9), entries(4, 6));
    segments.put(HISTORY, 0, Duration.ofDays(9), entries(7, 9));
    segments.put(HISTORY, 0, ONE_DAY, entries(10, 12));
    final var snapshot = segments.putSnapshot(HISTORY, THREE_DAYS, 5);

    // when
    final var report = maintenance().runOnce();

    // then: the cutoff is the first young segment's, and the snapshot is young too
    assertThat(report).isEqualTo(new MaintenanceReport(0, 3, 1, 0));
    assertThat(segments.segments(HISTORY)).containsExactly("10-12 L0");
    assertThat(segments.snapshots()).containsExactly(snapshot);
    final var marker = store.get(segments.markers().getFirst()).orElseThrow();
    assertThat(new String(marker, StandardCharsets.UTF_8))
        .contains("\"firstIndex\":10,\"firstPosition\":19,");
  }

  @Test
  void shouldKeepTheSnapshotAndTheSegmentsThatABackupNeeds() throws Exception {
    // given: a backup names the old snapshot at index 3, and needs entries 4 and 5, which the old
    // segment of 4 to 6 holds; the old snapshot at 5 is named by none
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TEN_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, Duration.ofDays(9), entries(7, 9));
    segments.put(HISTORY, 0, ONE_DAY, entries(10, 12));
    final var named = segments.putSnapshot(HISTORY, TEN_DAYS, 3);
    segments.putSnapshot(HISTORY, Duration.ofDays(9), 5);
    segments.putBackupLog(HISTORY, 17, 4, 5, named);

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(0, 2, 1, 1));
    assertThat(segments.segments(HISTORY)).containsExactly("4-6 L0", "10-12 L0");
    assertThat(segments.snapshots()).containsExactly(named);
  }

  @Test
  void shouldKeepAnOldSnapshotThatABackupLogNames() throws Exception {
    // given
    segments.put(HISTORY, 0, ONE_DAY, entries(1, 3));
    final var named = segments.putSnapshot(HISTORY, TEN_DAYS, 2);
    segments.putBackupLog(HISTORY, 17, 1, 3, named);

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(MaintenanceReport.NONE);
    assertThat(segments.snapshots()).containsExactly(named);
  }

  @Test
  void shouldDeleteAnOldSnapshotThatNoBackupLogNames() throws Exception {
    // given: a backup log that names another snapshot
    segments.put(HISTORY, 0, ONE_DAY, entries(1, 3));
    final var named = segments.putSnapshot(HISTORY, TEN_DAYS, 2);
    segments.putSnapshot(HISTORY, Duration.ofDays(9), 5);
    segments.putBackupLog(HISTORY, 17, 1, 3, named);

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(0, 0, 0, 1));
    assertThat(segments.snapshots()).containsExactly(named);
  }

  @Test
  void shouldKeepAYoungSnapshotThatNoBackupLogNames() throws Exception {
    // given
    segments.put(HISTORY, 0, ONE_DAY, entries(1, 3));
    final var young = segments.putSnapshot(HISTORY, THREE_DAYS, 2);

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(MaintenanceReport.NONE);
    assertThat(segments.snapshots()).containsExactly(young);
  }

  @Test
  void shouldDeleteTheSnapshotOnceTheBackupLogIsDeleted() throws Exception {
    // given: a run that kept the old snapshot, and the old segment, that the backup needs
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, ONE_DAY, entries(4, 6));
    final var snapshot = segments.putSnapshot(HISTORY, TEN_DAYS, 2);
    final var backupLog = segments.putBackupLog(HISTORY, 17, 1, 3, snapshot);
    assertThat(maintenance().runOnce()).isEqualTo(new MaintenanceReport(0, 0, 1, 0));
    assertThat(segments.snapshots()).containsExactly(snapshot);

    // when: the backup is deleted
    store.delete(backupLog);
    final var report = maintenance().runOnce();

    // then: the marker is at the cutoff already
    assertThat(report).isEqualTo(new MaintenanceReport(0, 1, 0, 1));
    assertThat(segments.snapshots()).isEmpty();
    assertThat(segments.segments(HISTORY)).containsExactly("4-6 L0");
  }

  @Test
  void shouldNotConsiderThePathOfASnapshot() throws Exception {
    // given: a restore at entry 6, position 12; the old history's snapshot at 8 relies on position
    // 18, past the checkpoint, so it is off the path, and a backup names it; the one at 3 relies
    // on position 8, before the checkpoint, and none names it
    segments.putHistory(RESTORED, Duration.ofDays(5), new Base(HISTORY, 6, 12));
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TEN_DAYS, entries(4, 6));
    segments.put(RESTORED, 0, ONE_DAY, entries(7, 9));
    segments.putSnapshot(HISTORY, TEN_DAYS, 3);
    final var offThePath = segments.putSnapshot(HISTORY, Duration.ofDays(9), 8);
    segments.putBackupLog(HISTORY, 17, 4, 6, offThePath);

    // when
    final var report = maintenance().runOnce();

    // then: the snapshot on the path goes, being unnamed and old
    assertThat(report).isEqualTo(new MaintenanceReport(0, 1, 1, 1));
    assertThat(segments.snapshots()).containsExactly(offThePath);
    assertThat(segments.segments(HISTORY)).containsExactly("4-6 L0");
  }

  @Test
  void shouldDeleteTheDescriptorOfASnapshotBeforeItsFiles() throws Exception {
    // given
    segments.put(HISTORY, 0, ONE_DAY, entries(1, 3));
    final var old = segments.putSnapshot(HISTORY, TEN_DAYS, 2);

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(0, 0, 0, 1));
    assertThat(store.writes())
        .containsExactly("DELETE " + descriptorKey(old), "DELETE " + fileKey(old));
  }

  @Test
  void shouldDeleteTheFilesOfASnapshotWithoutDescriptorWhenAllAreOld() throws Exception {
    // given: an upload that stopped before its descriptor
    final var id = "5-1-11-11-0-x";
    segments.put(HISTORY, 0, ONE_DAY, entries(1, 3));
    final var state = segments.putSnapshotFile(id, "state", TEN_DAYS);
    final var checksum = segments.putSnapshotFile(id, "checksum", Duration.ofDays(9));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(0, 0, 0, 1));
    assertThat(store.get(state)).isEmpty();
    assertThat(store.get(checksum)).isEmpty();
  }

  @Test
  void shouldKeepTheFilesOfASnapshotWithoutDescriptorWhileOneIsYoung() throws Exception {
    // given: an upload in progress, which has taken long
    final var id = "5-1-11-11-0-x";
    segments.put(HISTORY, 0, ONE_DAY, entries(1, 3));
    final var state = segments.putSnapshotFile(id, "state", TEN_DAYS);
    final var checksum = segments.putSnapshotFile(id, "checksum", ONE_DAY);

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(MaintenanceReport.NONE);
    assertThat(store.get(state)).isPresent();
    assertThat(store.get(checksum)).isPresent();
  }

  @Test
  void shouldKeepAnOldSegmentOnThePathThatABackupNeeds() throws Exception {
    // given: the backup needs entries 4 and 5, which the old segment of 4 to 6 holds
    final var unneeded = segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    final var needed = segments.put(HISTORY, 0, TEN_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, THREE_DAYS, entries(7, 9));
    segments.put(HISTORY, 0, ONE_DAY, entries(10, 12));
    segments.putBackupLog(HISTORY, 17, 4, 5);

    // when
    final var report = maintenance().runOnce();

    // then: the marker moves past the segment that is kept
    assertThat(report).isEqualTo(new MaintenanceReport(0, 1, 1));
    assertThat(segments.segments(HISTORY)).containsExactly("4-6 L0", "7-9 L0", "10-12 L0");
    assertThat(store.writes()).contains("DELETE " + unneeded).doesNotContain("DELETE " + needed);
    final var marker = store.get(segments.markers().getFirst()).orElseThrow();
    assertThat(new String(marker, StandardCharsets.UTF_8))
        .contains("\"firstIndex\":7,\"firstPosition\":13,");
  }

  @Test
  void shouldMoveTheMarkerPastSegmentsThatOnlyBackupsKeep() throws Exception {
    // given: the backup needs all the old segments below the cutoff
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TEN_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, ONE_DAY, entries(7, 9));
    segments.putBackupLog(HISTORY, 17, 1, 6);

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(0, 0, 1));
    assertThat(segments.segments(HISTORY)).containsExactly("1-3 L0", "4-6 L0", "7-9 L0");
    final var marker = store.get(segments.markers().getFirst()).orElseThrow();
    assertThat(new String(marker, StandardCharsets.UTF_8)).contains("\"firstIndex\":7,");
  }

  @Test
  void shouldNotWriteAnotherMarkerWhileBackupsKeepTheSegmentsBelowIt() throws Exception {
    // given
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TEN_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, ONE_DAY, entries(7, 9));
    segments.putBackupLog(HISTORY, 17, 1, 3);
    maintenance().runOnce();
    final var writesAfterTheFirst = store.writes().size();

    // when
    final var second = maintenance().runOnce();

    // then
    assertThat(second).isEqualTo(MaintenanceReport.NONE);
    assertThat(store.writes()).hasSize(writesAfterTheFirst);
    assertThat(segments.segments(HISTORY)).containsExactly("1-3 L0", "7-9 L0");
    assertThat(segments.markers()).hasSize(1);
  }

  @Test
  void shouldKeepAnOldSegmentOffThePathThatABackupOfItsHistoryNeeds() throws Exception {
    // given: a restore at entry 6, position 12 left the first history's entries from 7 on off the
    // path; a backup that was taken before the restore needs 7 to 9, not the segment of 10 to 12
    segments.putHistory(RESTORED, Duration.ofDays(5), new Base(HISTORY, 6, 12));
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TEN_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, TEN_DAYS, entries(7, 9));
    segments.put(HISTORY, 0, TEN_DAYS, entries(10, 12));
    segments.put(RESTORED, 0, THREE_DAYS, entries(7, 9));
    segments.put(RESTORED, 0, ONE_DAY, entries(10, 12));
    segments.putBackupLog(HISTORY, 17, 7, 9);

    // when
    final var report = maintenance().runOnce();

    // then: the old segments of the path go, and the old one off it that no backup needs
    assertThat(report).isEqualTo(new MaintenanceReport(0, 3, 1));
    assertThat(segments.segments(HISTORY)).containsExactly("7-9 L0");
    assertThat(segments.segments(RESTORED)).containsExactly("7-9 L0", "10-12 L0");
  }

  @Test
  void shouldKeepWhatABackupNeedsOfEveryHistoryOnItsPath() throws Exception {
    // given: a backup of the restored history needs 4 to 8, which the first history serves up to 6,
    // where the restore took over; the first history's segment of 7 to 9 is off that path
    segments.putHistory(RESTORED, Duration.ofDays(5), new Base(HISTORY, 6, 12));
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TEN_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, TEN_DAYS, entries(7, 9));
    segments.put(RESTORED, 0, TEN_DAYS, entries(7, 9));
    segments.put(RESTORED, 0, ONE_DAY, entries(10, 12));
    segments.putBackupLog(RESTORED, 17, 4, 8);

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(0, 2, 1));
    assertThat(segments.segments(HISTORY)).containsExactly("4-6 L0");
    assertThat(segments.segments(RESTORED)).containsExactly("7-9 L0", "10-12 L0");
  }

  @Test
  void shouldKeepWhatABackupNeedsOfAHistoryThatLeavesThePartitionOut() throws Exception {
    // given: no path for the backup's history, so all of its own segments that the backup's range
    // touches are held
    segments.putHistory(OTHER, Duration.ofDays(40), Map.of(2, Base.NOTHING));
    segments.put(HISTORY, 0, ONE_DAY, entries(1, 3));
    segments.put(OTHER, 0, TEN_DAYS, entries(1, 3));
    segments.put(OTHER, 0, TEN_DAYS, entries(4, 6));
    segments.put(OTHER, 0, TEN_DAYS, entries(7, 9));
    segments.putBackupLog(OTHER, 17, 4, 5);

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(0, 2, 0));
    assertThat(segments.segments(OTHER)).containsExactly("4-6 L0");
  }

  @Test
  void shouldDeleteTheSegmentOnceTheBackupLogIsDeleted() throws Exception {
    // given: a run that kept the old segment that the backup needs
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TEN_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, ONE_DAY, entries(7, 9));
    final var backupLog = segments.putBackupLog(HISTORY, 17, 4, 5);
    maintenance().runOnce();
    assertThat(segments.segments(HISTORY)).containsExactly("4-6 L0", "7-9 L0");

    // when: the backup is deleted
    store.delete(backupLog);
    final var report = maintenance().runOnce();

    // then: the marker is at the cutoff already
    assertThat(report).isEqualTo(new MaintenanceReport(0, 1, 0));
    assertThat(segments.segments(HISTORY)).containsExactly("7-9 L0");
    assertThat(segments.markers()).hasSize(1);
  }

  @Test
  void shouldNotApplyRetentionWhileABackupLogIsNotJson() throws Exception {
    // given: two old snapshots, which retention would delete as no readable backup log names them,
    // but the unreadable one could
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TEN_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, ONE_DAY, entries(7, 9));
    final var olderSnapshot = segments.putSnapshot(HISTORY, TEN_DAYS, 2);
    final var newerSnapshot = segments.putSnapshot(HISTORY, Duration.ofDays(9), 5);
    segments.putBackupLog(HISTORY, 16, 1, 3);
    store.put(
        ObjectNames.backupLog(SegmentFixture.LOG, 17),
        "{\"version\":".getBytes(StandardCharsets.UTF_8));
    final int writesBefore = store.writes().size();

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(MaintenanceReport.NONE);
    assertThat(store.writes()).hasSize(writesBefore);
    assertThat(segments.segments(HISTORY)).containsExactly("1-3 L0", "4-6 L0", "7-9 L0");
    assertThat(segments.snapshots()).containsExactly(olderSnapshot, newerSnapshot);
    assertThat(segments.markers()).isEmpty();
  }

  @Test
  void shouldNotApplyRetentionWhileABackupLogIsNotValid() throws Exception {
    // given: JSON that is not a backup log, such as one of a later version
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, ONE_DAY, entries(4, 6));
    store.put(
        ObjectNames.backupLog(SegmentFixture.LOG, 17),
        "{\"version\":2}".getBytes(StandardCharsets.UTF_8));
    final int writesBefore = store.writes().size();

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(MaintenanceReport.NONE);
    assertThat(store.writes()).hasSize(writesBefore);
    assertThat(segments.segments(HISTORY)).containsExactly("1-3 L0", "4-6 L0");
  }

  @Test
  void shouldSkipABackupLogThatWasDeletedAfterTheListing() throws Exception {
    // given: the backup is deleted between the listing of its log and the GET
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, ONE_DAY, entries(4, 6));
    store.vanish(segments.putBackupLog(HISTORY, 17, 1, 3));

    // when
    final var report = maintenance().runOnce();

    // then: it needs nothing
    assertThat(report).isEqualTo(new MaintenanceReport(0, 1, 1));
    assertThat(segments.segments(HISTORY)).containsExactly("4-6 L0");
  }

  @Test
  void shouldIgnoreObjectsInTheBackupsDirectoryThatAreNotBackupLogs() throws Exception {
    // given
    segments.put(HISTORY, 0, TEN_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, ONE_DAY, entries(4, 6));
    store.put(ObjectNames.backupLogsPrefix(SegmentFixture.LOG) + "readme.txt", new byte[] {1});

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(0, 1, 1));
    assertThat(segments.segments(HISTORY)).containsExactly("4-6 L0");
  }

  private static String descriptorKey(final String snapshotId) {
    return ObjectNames.snapshotDescriptor(SegmentFixture.LOG, snapshotId);
  }

  private static String fileKey(final String snapshotId) {
    return ObjectNames.snapshotFile(SegmentFixture.LOG, snapshotId, "state");
  }
}
