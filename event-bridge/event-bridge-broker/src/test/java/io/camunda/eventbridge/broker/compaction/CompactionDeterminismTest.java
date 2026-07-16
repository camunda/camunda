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
import io.camunda.eventbridge.broker.compaction.CompactionTestSupport.MutableInstantSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the replica-determinism property (ADR 0001 consequences): two independent cleaner runs over
 * identical input logs, with identical clocks, produce byte-identical clean segments and manifest.
 */
final class CompactionDeterminismTest {

  @TempDir Path root;

  private static CompactionConfig config() {
    // small segment bound so the run rolls several segments — determinism must hold across rolls
    return new CompactionConfig(0, 96, Duration.ofHours(1), 1 << 16, Duration.ofSeconds(1));
  }

  private Harness run(final Path dir, final List<CompactionRecord> records) throws IOException {
    Files.createDirectories(dir);
    final Harness h =
        CompactionTestSupport.harness(
            dir,
            config(),
            new ListDirtyLogReader(records),
            new MutableInstantSource(5_000),
            () -> 6L,
            Fault.none());
    h.pass().runOnce();
    return h;
  }

  @Test
  void shouldProduceByteIdenticalCleanSetsAndManifest() throws IOException {
    // given identical input logs on two replicas
    final List<CompactionRecord> log =
        List.of(
            put(1, "a", "v1"),
            put(2, "b", "v1"),
            tombstone(3, "c"),
            put(4, "a", "v2"),
            put(5, "b", "v2"),
            put(6, "d", "v1"));
    final Path dirA = root.resolve("replicaA");
    final Path dirB = root.resolve("replicaB");

    // when both replicas clean independently
    final Harness a = run(dirA, log);
    final Harness b = run(dirB, log);

    // then — identical manifest bytes and identical segment names + bytes
    final byte[] manifestA = Files.readAllBytes(dirA.resolve(FileManifestStore.MANIFEST_FILE));
    final byte[] manifestB = Files.readAllBytes(dirB.resolve(FileManifestStore.MANIFEST_FILE));
    assertThat(manifestA).isEqualTo(manifestB);

    final List<CleanSegment> segments = a.store().latest().orElseThrow().segments();
    assertThat(segments).hasSizeGreaterThan(1);
    for (final CleanSegment segment : segments) {
      assertThat(Files.readAllBytes(dirA.resolve(segment.fileName())))
          .isEqualTo(Files.readAllBytes(dirB.resolve(segment.fileName())));
    }
  }
}
