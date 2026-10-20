/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.eventplane.segment.Level;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RollupTest extends StoreTestCase {

  private static final Duration ROLLUP_AGE = Duration.ofDays(1);

  private Maintenance maintenance() {
    return maintenance(MaintenanceConfig.DEFAULT_ROLLUP_TARGET_BYTES);
  }

  private Maintenance maintenance(final long rollupTargetBytes) {
    return new Maintenance(
        store, new MaintenanceConfig(ROLLUP_AGE, rollupTargetBytes, null), clock);
  }

  @Test
  void shouldMergeConsecutiveOldSegmentsIntoOneRollup() throws Exception {
    // given
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TWO_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, TWO_DAYS, entries(7, 9));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(1, 3, 0));
    assertThat(segments.segments(HISTORY)).containsExactly("1-9 L1");
    final var rollup = segments.keys(HISTORY).getFirst();
    assertThat(segments.entries(rollup)).isEqualTo(entries(1, 9));
    final var footer = segments.footer(rollup);
    assertThat(footer.level()).isEqualTo(Level.ROLLUP);
    assertThat(footer.history()).isEqualTo(HISTORY);
    assertThat(footer.firstPosition()).isEqualTo(1);
    assertThat(footer.lastPosition()).isEqualTo(18);
    assertThat(footer.latestRecordTimestamp()).isEqualTo(SegmentFixture.TIMESTAMP + 9);
  }

  @Test
  void shouldNameTheRollupLikeASegmentOfLevelOneWithAFreshUlid() throws Exception {
    // given
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TWO_DAYS, entries(4, 6));

    // when
    maintenance().runOnce();

    // then
    final var name = ObjectNames.parseSegment(segments.keys(HISTORY).getFirst());
    assertThat(name.level()).isEqualTo(1);
    assertThat(name.history()).isEqualTo(HISTORY);
    assertThat(UlidTime.of(name.ulid()))
        .isBetween(clock.instant(), clock.instant().plusSeconds(60));
  }

  @Test
  void shouldNotTouchSegmentsYoungerThanTheRollupAge() throws Exception {
    // given
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TWO_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, ONE_HOUR, entries(7, 9));
    segments.put(HISTORY, 0, ONE_HOUR, entries(10, 12));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(1, 2, 0));
    assertThat(segments.segments(HISTORY)).containsExactly("1-6 L1", "7-9 L0", "10-12 L0");
  }

  @Test
  void shouldDoNothingWhileEverySegmentIsYoung() throws Exception {
    // given
    segments.put(HISTORY, 0, ONE_HOUR, entries(1, 3));
    segments.put(HISTORY, 0, ONE_HOUR, entries(4, 6));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(MaintenanceReport.NONE);
    assertThat(store.writes()).isEmpty();
  }

  @Test
  void shouldRollUpSegmentsThatAreExactlyAsOldAsTheRollupAge() throws Exception {
    // given
    segments.put(HISTORY, 0, ROLLUP_AGE, entries(1, 3));
    segments.put(HISTORY, 0, ROLLUP_AGE, entries(4, 6));

    // when
    maintenance().runOnce();

    // then
    assertThat(segments.segments(HISTORY)).containsExactly("1-6 L1");
  }

  @Test
  void shouldNotRollUpAChainOfOneSegment() throws Exception {
    // given
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TWO_DAYS, entries(7, 9));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(MaintenanceReport.NONE);
    assertThat(segments.segments(HISTORY)).containsExactly("1-3 L0", "7-9 L0");
  }

  @Test
  void shouldEndAChainAtAGap() throws Exception {
    // given
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TWO_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, TWO_DAYS, entries(10, 12));
    segments.put(HISTORY, 0, TWO_DAYS, entries(13, 15));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(2, 4, 0));
    assertThat(segments.segments(HISTORY)).containsExactly("1-6 L1", "10-15 L1");
    assertThat(segments.entries(segments.keys(HISTORY).get(0))).isEqualTo(entries(1, 6));
    assertThat(segments.entries(segments.keys(HISTORY).get(1))).isEqualTo(entries(10, 15));
  }

  @Test
  void shouldStopAChainAtTheTargetSize() throws Exception {
    // given: no segment is as large as the target, and any two are
    final var keys = new ArrayList<String>();
    keys.add(segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3)));
    keys.add(segments.put(HISTORY, 0, TWO_DAYS, entries(4, 6)));
    keys.add(segments.put(HISTORY, 0, TWO_DAYS, entries(7, 9)));
    keys.add(segments.put(HISTORY, 0, TWO_DAYS, entries(10, 12)));
    long largest = 0;
    for (final var key : keys) {
      largest = Math.max(largest, store.head(key).orElseThrow().size());
    }

    // when
    final var report = maintenance(largest + 1).runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(2, 4, 0));
    assertThat(segments.segments(HISTORY)).containsExactly("1-6 L1", "7-12 L1");
  }

  @Test
  void shouldRollUpDuplicatesFromTwoLeadersWithoutRepeatingEntries() throws Exception {
    // given: a new leader resumed after the floor of 3, while the old one went on to upload 4 to 6
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TWO_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, TWO_DAYS, entries(7, 9));
    segments.put(HISTORY, 0, TWO_DAYS, entries(4, 9));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(1, 4, 0));
    assertThat(segments.segments(HISTORY)).containsExactly("1-9 L1");
    assertThat(segments.entries(segments.keys(HISTORY).getFirst())).isEqualTo(entries(1, 9));
  }

  @Test
  void shouldKeepASegmentThatReachesBeyondTheRollup() throws Exception {
    // given: the segment of 7 to 9 holds an entry that the segment of 4 to 8 doesn't
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TWO_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, TWO_DAYS, entries(7, 9));
    segments.put(HISTORY, 0, TWO_DAYS, entries(4, 8));

    // when
    maintenance().runOnce();

    // then
    assertThat(segments.segments(HISTORY)).containsExactly("1-8 L1", "7-9 L0");
    assertThat(segments.entries(segments.keys(HISTORY).getFirst())).isEqualTo(entries(1, 8));
  }

  @Test
  void shouldKeepEntriesWithoutRecords() throws Exception {
    // given
    final var mixed =
        List.of(
            journal.initial(),
            journal.records(2, 64),
            journal.configuration(),
            journal.records(2, 64),
            journal.initial(),
            journal.configuration());
    segments.put(HISTORY, 0, TWO_DAYS, mixed.subList(0, 3));
    segments.put(HISTORY, 0, TWO_DAYS, mixed.subList(3, 6));

    // when
    maintenance().runOnce();

    // then
    assertThat(segments.segments(HISTORY)).containsExactly("16-21 L1");
    assertThat(segments.entries(segments.keys(HISTORY).getFirst())).isEqualTo(mixed);
  }

  @Test
  void shouldDeleteTheLaterOfTwoRollupsOfTheSameRange() throws Exception {
    // given
    final var earlier = segments.put(HISTORY, 1, Duration.ofDays(3), entries(1, 9));
    segments.put(HISTORY, 1, TWO_DAYS, entries(1, 9));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(0, 1, 0));
    assertThat(segments.keys(HISTORY)).containsExactly(earlier);
  }

  @Test
  void shouldDeleteALaterRollupThatAnEarlierOneCovers() throws Exception {
    // given
    final var earlier = segments.put(HISTORY, 1, Duration.ofDays(3), entries(1, 9));
    segments.put(HISTORY, 1, TWO_DAYS, entries(4, 6));

    // when
    maintenance().runOnce();

    // then
    assertThat(segments.keys(HISTORY)).containsExactly(earlier);
  }

  @Test
  void shouldKeepRollupsThatOnlyOverlapInPart() throws Exception {
    // given: deleting either would lose entries that no segment of level 0 holds any more
    segments.put(HISTORY, 1, Duration.ofDays(3), entries(1, 6));
    segments.put(HISTORY, 1, TWO_DAYS, entries(4, 9));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(MaintenanceReport.NONE);
    assertThat(segments.segments(HISTORY)).containsExactly("1-6 L1", "4-9 L1");
  }

  @Test
  void shouldDeleteSegmentsThatAnExistingRollupCovers() throws Exception {
    // given: a rollup whose inputs were not deleted, as when maintenance stopped between the two
    segments.put(HISTORY, 1, ONE_HOUR, entries(1, 6));
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TWO_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, ONE_HOUR, entries(4, 6));
    segments.put(HISTORY, 0, ONE_HOUR, entries(5, 8));
    segments.put(HISTORY, 0, ONE_HOUR, entries(7, 9));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(0, 3, 0));
    assertThat(segments.segments(HISTORY)).containsExactly("1-6 L1", "5-8 L0", "7-9 L0");
  }

  @Test
  void shouldLeaveAChainWhoseSegmentIsGoneAndRollUpTheOthers() throws Exception {
    // given
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    final var gone = segments.put(HISTORY, 0, TWO_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, TWO_DAYS, entries(10, 12));
    segments.put(HISTORY, 0, TWO_DAYS, entries(13, 15));
    store.vanish(gone);

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(1, 2, 0));
    assertThat(segments.segments(HISTORY)).containsExactly("1-3 L0", "4-6 L0", "10-15 L1");
  }

  @Test
  void shouldLeaveAChainWithACorruptSegmentAndRollUpTheOthers() throws Exception {
    // given
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    final var corrupt = segments.put(HISTORY, 0, TWO_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, TWO_DAYS, entries(10, 12));
    segments.put(HISTORY, 0, TWO_DAYS, entries(13, 15));
    store.put(corrupt, "not a segment, just bytes".getBytes(StandardCharsets.UTF_8));

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(1, 2, 0));
    assertThat(segments.segments(HISTORY)).containsExactly("1-3 L0", "4-6 L0", "10-15 L1");
  }

  @Test
  void shouldLeaveAChainWithASegmentThatHoldsOtherEntriesThanItsNameSays() throws Exception {
    // given: the segment of 4 to 6 holds entries 4 and 5 only
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    final var short4to6 = segments.put(HISTORY, 0, TWO_DAYS, entries(4, 5));
    final var misnamed =
        ObjectNames.segment(
            SegmentFixture.LOG.cluster(),
            SegmentFixture.LOG.tenant(),
            SegmentFixture.LOG.partition(),
            HISTORY,
            4,
            6,
            0,
            ObjectNames.parseSegment(short4to6).ulid());
    store.put(misnamed, store.get(short4to6).orElseThrow());
    store.delete(short4to6);

    // when
    final var report = maintenance().runOnce();

    // then
    assertThat(report.rollupsWritten()).isZero();
    assertThat(segments.segments(HISTORY)).containsExactly("1-3 L0", "4-6 L0");
  }

  @Test
  void shouldChangeNothingTheSecondTime() throws Exception {
    // given
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TWO_DAYS, entries(4, 6));
    segments.put(HISTORY, 0, TWO_DAYS, entries(7, 9));
    segments.put(HISTORY, 0, ONE_HOUR, entries(10, 12));
    maintenance().runOnce();
    final var keysAfterTheFirst = segments.keys(HISTORY);
    final var writesAfterTheFirst = store.writes().size();

    // when
    final var second = maintenance().runOnce();

    // then
    assertThat(second).isEqualTo(MaintenanceReport.NONE);
    assertThat(segments.keys(HISTORY)).isEqualTo(keysAfterTheFirst);
    assertThat(store.writes()).hasSize(writesAfterTheFirst);
  }

  @Test
  void shouldRollUpAHistoryAfterTheTimeHasPassed() throws Exception {
    // given
    segments.put(HISTORY, 0, Duration.ofHours(20), entries(1, 3));
    segments.put(HISTORY, 0, Duration.ofHours(20), entries(4, 6));
    assertThat(maintenance().runOnce()).isEqualTo(MaintenanceReport.NONE);

    // when
    clock.advance(Duration.ofHours(5));
    final var report = maintenance().runOnce();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(1, 2, 0));
    assertThat(segments.segments(HISTORY)).containsExactly("1-6 L1");
  }
}
