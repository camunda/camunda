/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import static io.camunda.eventbridge.broker.compaction.CompactionRecords.put;
import static io.camunda.eventbridge.broker.compaction.CompactionRecords.readCleanSet;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.broker.compaction.CompactionPass.Fault;
import io.camunda.eventbridge.broker.compaction.CompactionTestSupport.Harness;
import io.camunda.eventbridge.broker.compaction.CompactionTestSupport.ListDirtyLogReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A key-offset map that overflows forces a lower cleaner point and a multi-pass; the final result
 * must equal what a single unbounded pass produces.
 */
final class CompactionOverflowTest {

  @TempDir Path base;

  private List<CompactionRecord> log() {
    return List.of(
        put(1, "a", "v1"),
        put(2, "b", "v1"),
        put(3, "c", "v1"),
        put(4, "a", "v2"),
        put(5, "d", "v1"));
  }

  private Harness harness(final Path dir, final int capacity) {
    final var config =
        new CompactionConfig(0, 1 << 20, Duration.ofHours(1), capacity, Duration.ofSeconds(1));
    return CompactionTestSupport.harness(
        dir, config, new ListDirtyLogReader(log()), () -> 5L, Fault.none());
  }

  @Test
  void shouldConvergeToSingleBigPassResultViaMultiplePasses() throws IOException {
    // given the golden single unbounded pass
    final Path bigDir = base.resolve("big");
    Files.createDirectories(bigDir);
    final Harness big = harness(bigDir, 100);
    big.pass().runOnce();
    final List<CompactionRecord> golden = readCleanSet(bigDir, big.store());
    assertThat(golden).extracting(CompactionRecord::position).containsExactly(2L, 3L, 4L, 5L);

    // when — a capacity-2 map forces multiple passes
    final Path smallDir = base.resolve("small");
    Files.createDirectories(smallDir);
    final Harness small = harness(smallDir, 2);

    final PassResult first = small.pass().runOnce();
    // then — the first pass could not absorb the whole range and stopped at a lower point
    assertThat(first.overflowed()).isTrue();
    assertThat(first.cleanerPoint()).isLessThan(5);

    // when — subsequent passes finish the range
    int guard = 0;
    while (small.store().latest().orElseThrow().cleanerPoint() < 5 && guard++ < 10) {
      small.pass().runOnce();
    }

    // then — the multi-pass result equals the single big pass, byte for byte
    assertThat(small.store().latest().orElseThrow().cleanerPoint()).isEqualTo(5);
    assertThat(readCleanSet(smallDir, small.store())).containsExactlyElementsOf(golden);
  }
}
