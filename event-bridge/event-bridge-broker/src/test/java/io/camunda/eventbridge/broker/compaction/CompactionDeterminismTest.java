/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import static io.camunda.eventbridge.broker.compaction.CompactionRecords.put;
import static io.camunda.eventbridge.broker.compaction.CompactionRecords.tombstone;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.broker.compaction.CompactionPass.Fault;
import io.camunda.eventbridge.broker.compaction.CompactionTestSupport.Harness;
import io.camunda.eventbridge.broker.compaction.CompactionTestSupport.ListDirtyLogReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the replica-determinism property (ADR 0001 consequences): cleaner runs over identical input
 * logs produce byte-identical clean segments and manifest, even when the two simulated replicas run
 * genuinely diverging schedules — different pass counts at different (wall-clock) moments. The pass
 * takes no wall-clock input and the manifest contains only log-derived data, so nothing about
 * <em>when</em> or <em>how often</em> a replica cleans can leak into its output.
 */
final class CompactionDeterminismTest {

  @TempDir Path root;

  private static CompactionConfig config() {
    // small segment bound so the run rolls several segments — determinism must hold across rolls;
    // a grace window large enough that the in-flight tombstone stays within grace on both replicas
    return new CompactionConfig(0, 96, Duration.ofHours(1), 1 << 16, Duration.ofSeconds(1));
  }

  // The log includes an in-flight tombstone (position 3) that survives on both replicas, so the
  // divergent schedules must agree even on grace-sensitive content.
  private List<CompactionRecord> log() {
    return List.of(
        put(1, 1_000, "a", "v1"),
        put(2, 1_100, "b", "v1"),
        tombstone(3, 1_200, "c"),
        put(4, 1_300, "a", "v2"),
        put(5, 1_400, "b", "v2"),
        put(6, 1_500, "d", "v1"));
  }

  @Test
  void shouldProduceByteIdenticalCleanSetsAndManifestAcrossDivergingSchedules() throws IOException {
    // given two replicas of the same log
    final Path dirA = root.resolve("replicaA");
    final Path dirB = root.resolve("replicaB");
    Files.createDirectories(dirA);
    Files.createDirectories(dirB);
    final var lastCommittedA = new AtomicLong();
    final var lastCommittedB = new AtomicLong();
    final Harness a =
        CompactionTestSupport.harness(
            dirA, config(), new ListDirtyLogReader(log()), lastCommittedA::get, Fault.none());
    final Harness b =
        CompactionTestSupport.harness(
            dirB, config(), new ListDirtyLogReader(log()), lastCommittedB::get, Fault.none());

    // when replica A cleans the whole log in ONE pass while replica B cleans it in TWO passes at
    // entirely different moments (nothing aligns their wall clocks — the pass never reads one)
    lastCommittedA.set(6);
    a.pass().runOnce();

    lastCommittedB.set(3);
    b.pass().runOnce();
    lastCommittedB.set(6);
    b.pass().runOnce();

    // then — byte-identical manifest and byte-identical segments under identical names
    final byte[] manifestA = Files.readAllBytes(dirA.resolve(FileManifestStore.MANIFEST_FILE));
    final byte[] manifestB = Files.readAllBytes(dirB.resolve(FileManifestStore.MANIFEST_FILE));
    assertThat(manifestA).isEqualTo(manifestB);

    final List<CleanSegment> segmentsA = a.store().latest().orElseThrow().segments();
    final List<CleanSegment> segmentsB = b.store().latest().orElseThrow().segments();
    assertThat(segmentsA).hasSizeGreaterThan(1).isEqualTo(segmentsB);
    for (final CleanSegment segment : segmentsA) {
      assertThat(Files.readAllBytes(dirA.resolve(segment.fileName())))
          .isEqualTo(Files.readAllBytes(dirB.resolve(segment.fileName())));
    }

    // and the surviving tombstone is present on both (still within grace on the log clock)
    assertThat(CompactionRecords.readCleanSet(dirA, a.store()))
        .anySatisfy(r -> assertThat(r.isTombstone() && r.position() == 3).isTrue());
  }
}
