/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import static io.camunda.eventbridge.broker.compaction.CompactionRecords.key;
import static io.camunda.eventbridge.broker.compaction.CompactionRecords.put;
import static io.camunda.eventbridge.broker.compaction.CompactionRecords.readCleanSet;
import static io.camunda.eventbridge.broker.compaction.CompactionRecords.tombstone;
import static io.camunda.eventbridge.broker.compaction.CompactionRecords.value;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.broker.compaction.CompactionPass.Fault;
import io.camunda.eventbridge.broker.compaction.CompactionTestSupport.Harness;
import io.camunda.eventbridge.broker.compaction.CompactionTestSupport.ListDirtyLogReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tombstone two-touch grace on the log clock: a tombstone at position P is dropped only when it
 * already survived a committed pass (P ≤ previous cleaner point) and the running maximum of record
 * timestamps up to C has advanced past the tombstone's own timestamp by more than the grace window.
 * All time in these tests is record timestamps — no wall clock anywhere.
 */
final class CompactionTombstoneTest {

  private static final long GRACE_MILLIS = 1_000;

  @TempDir Path dir;

  private final AtomicLong lastCommitted = new AtomicLong();

  private Harness harness(final List<CompactionRecord> records) {
    final var config =
        new CompactionConfig(
            0, 1 << 20, Duration.ofMillis(GRACE_MILLIS), 1 << 16, Duration.ofSeconds(1));
    return CompactionTestSupport.harness(
        dir, config, new ListDirtyLogReader(records), lastCommitted::get, Fault.none());
  }

  @Test
  void shouldSurviveFirstPassEvenWhenGraceAlreadyElapsedOnTheLogClock() {
    // given a tombstone whose grace is already elapsed relative to a much newer record
    final Harness h =
        harness(
            List.of(put(1, 1_000, "a", "v1"), tombstone(2, 1_000, "a"), put(3, 9_000, "z", "vz")));
    lastCommitted.set(3);

    // when the first pass folds them (previous cleaner point is -1, so two-touch cannot hold)
    h.pass().runOnce();

    // then — the tombstone survives its first pass; the killed value stays dead
    final List<CompactionRecord> clean = readCleanSet(dir, h.store());
    assertThat(clean).extracting(CompactionRecord::position).containsExactly(2L, 3L);
    assertThat(clean.get(0).isTombstone()).isTrue();
    // and the log clock is the running max of record timestamps up to C
    assertThat(h.store().latest().orElseThrow().maxLogTimestamp()).isEqualTo(9_000);
  }

  @Test
  void shouldDropTombstoneOnlyAfterGraceElapsesOnTheLogClock() {
    // given a tombstone at t=1500 plus later traffic that advances the log clock in two steps
    final Harness h =
        harness(
            List.of(
                put(1, 1_000, "a", "v1"),
                tombstone(2, 1_500, "a"),
                put(10, 2_000, "z", "vz"),
                put(20, 3_000, "y", "vy")));

    // when — pass 1 folds the tombstone into the clean set (first touch)
    lastCommitted.set(2);
    h.pass().runOnce();

    // and pass 2 runs with the log clock at 2000 (delta 500 ≤ grace)
    lastCommitted.set(10);
    h.pass().runOnce();

    // then — still within grace: the tombstone is retained
    assertThat(readCleanSet(dir, h.store()))
        .anySatisfy(
            r -> {
              assertThat(r.position()).isEqualTo(2L);
              assertThat(r.isTombstone()).isTrue();
            });

    // when — pass 3 runs with the log clock at 3000 (delta 1500 > grace)
    lastCommitted.set(20);
    h.pass().runOnce();

    // then — the tombstone is gone, unrelated records remain
    final List<CompactionRecord> clean = readCleanSet(dir, h.store());
    assertThat(clean).noneMatch(r -> r.position() == 2L);
    assertThat(clean).extracting(CompactionRecords::key).containsExactly("z", "y");
  }

  @Test
  void shouldRetainBoundaryTombstoneWhileTheLogClockIsFrozen() {
    // given later traffic whose timestamps do NOT advance (an idle-then-flat partition)
    final Harness h =
        harness(
            List.of(
                put(1, 1_000, "a", "v1"),
                tombstone(2, 1_000, "a"),
                put(10, 1_000, "z", "vz"),
                put(20, 1_000, "y", "vy")));

    // when — three passes, each advancing C but never the log clock
    lastCommitted.set(2);
    h.pass().runOnce();
    lastCommitted.set(10);
    h.pass().runOnce();
    lastCommitted.set(20);
    h.pass().runOnce();

    // then — the frozen clock retains the tombstone indefinitely (the safe failure direction)
    assertThat(readCleanSet(dir, h.store()))
        .anySatisfy(
            r -> {
              assertThat(r.position()).isEqualTo(2L);
              assertThat(r.isTombstone()).isTrue();
            });
    assertThat(h.store().latest().orElseThrow().maxLogTimestamp()).isEqualTo(1_000);
  }

  @Test
  void shouldNotRegressTheLogClockWhenTimestampsWobbleBackwards() {
    // given a first pass that observed t=5000, then later records with lower timestamps (as a
    // leader change can produce)
    final Harness h =
        harness(
            List.of(
                put(1, 5_000, "a", "v1"), put(10, 4_000, "z", "vz"), put(20, 4_500, "y", "vy")));

    // when
    lastCommitted.set(1);
    h.pass().runOnce();
    assertThat(h.store().latest().orElseThrow().maxLogTimestamp()).isEqualTo(5_000);
    lastCommitted.set(20);
    h.pass().runOnce();

    // then — the log clock is a running maximum: it never moves backwards
    assertThat(h.store().latest().orElseThrow().maxLogTimestamp()).isEqualTo(5_000);
  }

  @Test
  void shouldResurrectKeyWhenAPutIsNewerThanTheTombstone() {
    // given a put, a tombstone, then a newer put for the same key
    final Harness h =
        harness(
            List.of(put(1, 1_000, "a", "v1"), tombstone(2, 1_100, "a"), put(3, 1_200, "a", "v2")));
    lastCommitted.set(3);

    // when
    h.pass().runOnce();

    // then — the newer put wins; the tombstone is dropped immediately (superseded, not lingering)
    final List<CompactionRecord> clean = readCleanSet(dir, h.store());
    assertThat(clean).hasSize(1);
    assertThat(clean.get(0).isTombstone()).isFalse();
    assertThat(key(clean.get(0))).isEqualTo("a");
    assertThat(value(clean.get(0))).isEqualTo("v2");
    assertThat(clean.get(0).position()).isEqualTo(3);
  }
}
