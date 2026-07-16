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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.eventbridge.broker.compaction.CompactionPass.Fault;
import io.camunda.eventbridge.broker.compaction.CompactionPass.Phase;
import io.camunda.eventbridge.broker.compaction.CompactionTestSupport.Harness;
import io.camunda.eventbridge.broker.compaction.CompactionTestSupport.ListDirtyLogReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Walks the crash matrix: a pass is interrupted at each phase boundary, then a fresh pass reruns
 * from the durable {@link ManifestStore} and must converge to exactly what an uninterrupted pass
 * produces. Before the commit the previously-committed manifest stays authoritative; after the
 * commit the new manifest is authoritative and superseded/half-written files are swept. No orphan
 * or temp file is left behind.
 */
final class CompactionCrashRecoveryTest {

  @TempDir Path base;

  private final AtomicLong lastCommitted = new AtomicLong();

  private static final class CrashSimulated extends RuntimeException {
    CrashSimulated(final Phase phase) {
      super("simulated crash at " + phase);
    }
  }

  // A log whose first pass (up to C=4) then second pass (up to C=9) both rewrite several rolled
  // segments, so a mid-sweep crash really does leave half-written segment files on disk.
  private List<CompactionRecord> log() {
    return List.of(
        put(1, "a", "v1"),
        put(2, "b", "v1"),
        put(3, "a", "v2"),
        put(4, "c", "v1"),
        put(5, "b", "v2"),
        put(6, "a", "v3"),
        put(7, "d", "v1"),
        put(8, "e", "v1"),
        put(9, "f", "v1"));
  }

  private Harness harness(final Path dir, final Fault fault) {
    final var config =
        new CompactionConfig(0, 96, Duration.ofHours(1), 1 << 16, Duration.ofSeconds(1));
    return CompactionTestSupport.harness(
        dir, config, new ListDirtyLogReader(log()), lastCommitted::get, fault);
  }

  private List<CompactionRecord> golden(final Path dir) throws IOException {
    Files.createDirectories(dir);
    final Harness h = harness(dir, Fault.none());
    lastCommitted.set(4);
    h.pass().runOnce();
    lastCommitted.set(9);
    h.pass().runOnce();
    return readCleanSet(dir, h.store());
  }

  @Test
  void shouldConvergeAfterCrashDuringMapBuild() throws IOException {
    assertConvergesAfterCrashAt(Phase.MAP_BUILT);
  }

  @Test
  void shouldConvergeAfterCrashMidSweepWithHalfWrittenSegment() throws IOException {
    assertConvergesAfterCrashAt(Phase.MID_SWEEP);
  }

  @Test
  void shouldConvergeAfterCrashBeforeCommit() throws IOException {
    assertConvergesAfterCrashAt(Phase.BEFORE_COMMIT);
  }

  @Test
  void shouldConvergeAfterCrashAfterCommitBeforeTrash() throws IOException {
    assertConvergesAfterCrashAt(Phase.AFTER_COMMIT);
  }

  private void assertConvergesAfterCrashAt(final Phase crashPhase) throws IOException {
    // given the golden result of an uninterrupted two-pass run
    final List<CompactionRecord> golden = golden(base.resolve("golden"));

    final Path dir = base.resolve("crash-" + crashPhase);
    Files.createDirectories(dir);

    // and a committed baseline from pass 1
    final Harness pass1 = harness(dir, Fault.none());
    lastCommitted.set(4);
    pass1.pass().runOnce();
    final long baselineCleanerPoint = pass1.store().latest().orElseThrow().cleanerPoint();
    assertThat(baselineCleanerPoint).isEqualTo(4);

    // when pass 2 crashes at the chosen phase
    final Harness crashing = harness(dir, faultAt(crashPhase));
    lastCommitted.set(9);
    assertThatThrownBy(() -> crashing.pass().runOnce()).isInstanceOf(CrashSimulated.class);

    // then before the commit the pass-1 manifest is still authoritative
    if (crashPhase != Phase.AFTER_COMMIT) {
      assertThat(crashing.store().latest().orElseThrow().cleanerPoint()).isEqualTo(4);
    } else {
      assertThat(crashing.store().latest().orElseThrow().cleanerPoint()).isEqualTo(9);
    }

    // and the crash additionally left a torn manifest write and a stale condemned marker behind
    Files.write(dir.resolve(FileManifestStore.MANIFEST_TMP), new byte[] {1, 2, 3});
    Files.write(
        dir.resolve(CleanSegmentFiles.condemnedName(CleanSegmentFiles.segmentName(700, 800))),
        new byte[] {4});

    // when a fresh pass reruns from the durable state
    final Harness rerun = harness(dir, Fault.none());
    lastCommitted.set(9);
    rerun.pass().runOnce();

    // then it converges to the uninterrupted result and leaves no orphan, temp, marker, or torn
    // manifest files
    assertThat(readCleanSet(dir, rerun.store())).containsExactlyElementsOf(golden);
    assertThat(rerun.store().latest().orElseThrow().cleanerPoint()).isEqualTo(9);
    assertNoOrphanFiles(dir, rerun.store());
  }

  private Fault faultAt(final Phase target) {
    return phase -> {
      if (phase == target) {
        throw new CrashSimulated(phase);
      }
    };
  }

  private void assertNoOrphanFiles(final Path dir, final ManifestStore store) throws IOException {
    final Set<String> referenced =
        store
            .latest()
            .map(m -> m.segments().stream().map(CleanSegment::fileName).collect(Collectors.toSet()))
            .orElse(Set.of());
    try (final Stream<Path> entries = Files.list(dir)) {
      entries.forEach(
          p -> {
            final String name = p.getFileName().toString();
            if (CleanSegmentFiles.isTmp(name)) {
              fail("leftover temp file: " + name);
            }
            if (CleanSegmentFiles.isCondemned(name)) {
              fail("leftover condemned marker: " + name);
            }
            if (FileManifestStore.MANIFEST_TMP.equals(name)) {
              fail("leftover torn manifest write: " + name);
            }
            if (CleanSegmentFiles.isSegment(name) && !referenced.contains(name)) {
              fail("leftover orphan segment: " + name);
            }
          });
    } catch (final UncheckedIOException e) {
      throw new IOException(e);
    }
  }
}
