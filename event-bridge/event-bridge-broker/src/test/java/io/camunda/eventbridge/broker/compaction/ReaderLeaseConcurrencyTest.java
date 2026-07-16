/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import static io.camunda.eventbridge.broker.compaction.CompactionRecords.put;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Races a reader acquiring a lease against the trash queue condemning the same file, with real
 * threads aligned per iteration by a barrier. The invariant under test is the one the old two-step
 * acquire violated: a reader either gets a lease whose open channel reads the full, intact segment
 * bytes, or a clean retry signal (empty) — never a lease on missing bytes. After both sides finish,
 * the file and its marker are always fully gone.
 *
 * <p>All cross-thread completion points are {@link Future#get()} joins, so the post-race assertions
 * are deterministic — no sleeping or polling is needed anywhere.
 */
final class ReaderLeaseConcurrencyTest {

  private static final int ITERATIONS = 200;

  @TempDir Path dir;

  private final ExecutorService executor = Executors.newFixedThreadPool(2);

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void shouldNeverHandOutALeaseOnMissingBytesWhileDrainCondemns() throws Exception {
    final var store = new FileManifestStore(dir);
    final var leases = new ReaderLeaseRegistry();
    final var trash = new TrashQueue(dir, leases, store::latest, () -> true);

    for (int i = 0; i < ITERATIONS; i++) {
      // given a fresh superseded segment queued for deletion
      final var writer = new CleanSegmentWriter(dir, i, 1 << 20);
      writer.append(put(i, "k", "value-" + i));
      final CleanSegment segment = writer.finish().get(0);
      final Path file = dir.resolve(segment.fileName());
      trash.enqueue(file);
      final String expectedValue = "value-" + i;

      // when a reader acquires while the drain condemns, aligned to collide
      final var barrier = new CyclicBarrier(2);
      final Future<Optional<ReaderLease>> acquired =
          executor.submit(
              () -> {
                barrier.await();
                return leases.acquire(file);
              });
      final Future<?> drained =
          executor.submit(
              () -> {
                barrier.await();
                trash.drain();
                return null;
              });
      drained.get();
      final Optional<ReaderLease> lease = acquired.get();

      // then — a granted lease always reads intact bytes; empty is the clean retry signal
      if (lease.isPresent()) {
        final var reader = new CleanSegmentReader(lease.get());
        final CompactionRecord record = reader.next();
        assertThat(CompactionRecords.value(record)).isEqualTo(expectedValue);
        assertThat(record.position()).isEqualTo(i);
        lease.get().release();
      }

      // and afterwards both the original and the marker are deterministically gone: either the
      // drain unlinked the marker directly (no lease at its re-check) or the release above
      // performed the deferred unlink; a final drain covers the case where the racing drain ran
      // before the file became eligible. All threads are joined, so plain assertions suffice.
      trash.drain();
      final Path marker = dir.resolve(CleanSegmentFiles.condemnedName(segment.fileName()));
      assertThat(Files.exists(file)).isFalse();
      assertThat(Files.exists(marker)).isFalse();
      assertThat(leases.leaseCount(file)).isZero();
    }
  }
}
