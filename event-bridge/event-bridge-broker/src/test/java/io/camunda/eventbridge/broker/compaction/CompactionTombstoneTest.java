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
import io.camunda.eventbridge.broker.compaction.CompactionTestSupport.MutableInstantSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tombstone two-touch grace: survives the first pass, dies only after grace, and resurrection. */
final class CompactionTombstoneTest {

  private static final long GRACE_MILLIS = 1_000;

  @TempDir Path dir;

  private final AtomicLong lastCommitted = new AtomicLong();
  private final MutableInstantSource clock = new MutableInstantSource(1_000);

  private Harness harness(final List<CompactionRecord> records) {
    final var config =
        new CompactionConfig(
            0, 1 << 20, Duration.ofMillis(GRACE_MILLIS), 1 << 16, Duration.ofSeconds(1));
    return CompactionTestSupport.harness(
        dir, config, new ListDirtyLogReader(records), clock, lastCommitted::get, Fault.none());
  }

  @Test
  void shouldSurviveFirstPassAndKeepKilledValueDead() {
    // given a put then a tombstone for the same key
    final Harness h = harness(List.of(put(1, "a", "v1"), tombstone(2, "a")));
    lastCommitted.set(2);

    // when the first pass folds them
    h.pass().runOnce();

    // then — only the tombstone survives (the killed value is dead); the tombstone is kept
    final List<CompactionRecord> clean = readCleanSet(dir, h.store());
    assertThat(clean).hasSize(1);
    assertThat(clean.get(0).isTombstone()).isTrue();
    assertThat(clean.get(0).position()).isEqualTo(2);
    assertThat(h.store().latest().orElseThrow().tombstoneStamp(2)).isEqualTo(1_000);
  }

  @Test
  void shouldDropTombstoneOnlyAfterGraceOnALaterPass() {
    // given a tombstone plus later unrelated records so C keeps advancing across passes
    final Harness h =
        harness(
            List.of(put(1, "a", "v1"), tombstone(2, "a"), put(10, "z", "vz"), put(20, "y", "vy")));

    // when — pass 1 stamps the tombstone at t=1000
    lastCommitted.set(2);
    h.pass().runOnce();

    // when — pass 2 still within grace (t=1500, delta 500)
    clock.set(1_500);
    lastCommitted.set(10);
    h.pass().runOnce();

    // then — the tombstone is still there
    assertThat(readCleanSet(dir, h.store()))
        .anySatisfy(r -> assertThat(r.isTombstone() && r.position() == 2).isTrue());

    // when — pass 3 past grace (t=2500, delta 1500 > 1000)
    clock.set(2_500);
    lastCommitted.set(20);
    h.pass().runOnce();

    // then — the tombstone is gone, unrelated records remain
    final List<CompactionRecord> clean = readCleanSet(dir, h.store());
    assertThat(clean).noneSatisfy(r -> assertThat(r.position()).isEqualTo(2L));
    assertThat(clean).extracting(CompactionRecords::key).containsExactly("z", "y");
  }

  @Test
  void shouldResurrectKeyWhenAPutIsNewerThanTheTombstone() {
    // given a put, a tombstone, then a newer put for the same key
    final Harness h = harness(List.of(put(1, "a", "v1"), tombstone(2, "a"), put(3, "a", "v2")));
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
