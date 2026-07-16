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
import static io.camunda.eventbridge.broker.compaction.CompactionRecords.unkeyed;
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

/** End-to-end latest-per-key retention, including position preservation and un-keyed records. */
final class CompactionPassTest {

  @TempDir Path dir;

  private final AtomicLong lastCommitted = new AtomicLong();

  private static CompactionConfig config() {
    return new CompactionConfig(0, 1 << 20, Duration.ofHours(1), 1 << 16, Duration.ofSeconds(1));
  }

  private Harness harness(final List<CompactionRecord> records) {
    return CompactionTestSupport.harness(
        dir, config(), new ListDirtyLogReader(records), lastCommitted::get, Fault.none());
  }

  @Test
  void shouldKeepLatestPerKeyAfterOnePass() {
    // given overwrites of key "a" and a single "b"
    final Harness h = harness(List.of(put(1, "a", "v1"), put(2, "b", "v1"), put(3, "a", "v2")));
    lastCommitted.set(3);

    // when
    h.pass().runOnce();

    // then — only the latest per key survives, positions preserved verbatim (gap at 1)
    final List<CompactionRecord> clean = readCleanSet(dir, h.store());
    assertThat(clean).extracting(CompactionRecord::position).containsExactly(2L, 3L);
    assertThat(clean).extracting(CompactionRecords::key).containsExactly("b", "a");
    assertThat(value(clean.get(1))).isEqualTo("v2");
  }

  @Test
  void shouldKeepLatestPerKeyAcrossMultiplePassesSpanningCleanAndDirty() {
    // given a first pass folds positions 1..3, a later overwrite of "b" and a new "c" land after C
    final Harness h =
        harness(
            List.of(
                put(1, "a", "v1"),
                put(2, "b", "v1"),
                put(3, "a", "v2"),
                put(4, "b", "v2"),
                put(5, "c", "v1")));

    // when — pass 1 up to position 3
    lastCommitted.set(3);
    h.pass().runOnce();
    final List<CompactionRecord> afterPass1 = readCleanSet(dir, h.store());

    // then
    assertThat(afterPass1).extracting(CompactionRecord::position).containsExactly(2L, 3L);

    // when — pass 2 up to position 5 (overwrite spans the clean set)
    lastCommitted.set(5);
    h.pass().runOnce();

    // then — b's clean-set copy at 2 is dropped in favor of 4; a survives from the clean set
    final List<CompactionRecord> afterPass2 = readCleanSet(dir, h.store());
    assertThat(afterPass2).extracting(CompactionRecord::position).containsExactly(3L, 4L, 5L);
    assertThat(afterPass2).extracting(CompactionRecords::key).containsExactly("a", "b", "c");
    assertThat(value(afterPass2.get(1))).isEqualTo("v2");
  }

  @Test
  void shouldCopyUnkeyedRecordsForwardVerbatimAndNeverCompactThem() {
    // given a mix of keyed overwrites and key-less records (legal pre-validation)
    final Harness h =
        harness(
            List.of(put(1, "a", "v1"), unkeyed(2, "raw1"), put(3, "a", "v2"), unkeyed(4, "raw2")));
    lastCommitted.set(4);

    // when
    h.pass().runOnce();

    // then — both un-keyed records survive verbatim; only the superseded keyed put(1) is dropped
    final List<CompactionRecord> clean = readCleanSet(dir, h.store());
    assertThat(clean).extracting(CompactionRecord::position).containsExactly(2L, 3L, 4L);
    assertThat(clean.get(0).hasKey()).isFalse();
    assertThat(value(clean.get(0))).isEqualTo("raw1");
    assertThat(key(clean.get(1))).isEqualTo("a");
    assertThat(value(clean.get(2))).isEqualTo("raw2");
  }

  @Test
  void shouldDoNothingWhenCleanerPointDoesNotAdvance() {
    // given the whole log is within the min-lag window
    final CompactionConfig lagged =
        new CompactionConfig(100, 1 << 20, Duration.ofHours(1), 1 << 16, Duration.ofSeconds(1));
    final Harness h =
        CompactionTestSupport.harness(
            dir,
            lagged,
            new ListDirtyLogReader(List.of(put(1, "a", "v1"))),
            lastCommitted::get,
            Fault.none());
    lastCommitted.set(1);

    // when
    final PassResult result = h.pass().runOnce();

    // then
    assertThat(result.outcome()).isEqualTo(PassResult.Outcome.NOTHING_TO_DO);
    assertThat(readCleanSet(dir, h.store())).isEmpty();
  }
}
