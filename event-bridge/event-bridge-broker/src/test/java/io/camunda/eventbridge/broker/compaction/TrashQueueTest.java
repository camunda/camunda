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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Deferred, refcount-guarded deletion: lease, external predicate, reference and crash recovery. */
final class TrashQueueTest {

  @TempDir Path dir;

  private FileManifestStore store;
  private ReaderLeaseRegistry leases;
  private Path keptFile;

  @BeforeEach
  void setUp() {
    store = new FileManifestStore(dir);
    leases = new ReaderLeaseRegistry();

    // Commit a manifest that references one "kept" segment.
    final var writer = new CleanSegmentWriter(dir, 5, 1, 1 << 20);
    writer.append(put(1, "a", "va"));
    final List<CleanSegment> segments = writer.finish();
    store.commit(
        new CompactionManifest(CompactionManifest.VERSION_1, 5, segments, Map.of()),
        List.of(dir.resolve(segments.get(0).fileName())));
    keptFile = dir.resolve(segments.get(0).fileName());
  }

  private TrashQueue trashQueue(final BooleanSupplier externalPredicate) {
    final Supplier<Optional<CompactionManifest>> latest = store::latest;
    return new TrashQueue(dir, leases, latest, externalPredicate);
  }

  private Path orphanFile(final long firstPosition, final long cleanerPoint) throws IOException {
    final Path orphan = dir.resolve(CleanSegmentFiles.segmentName(firstPosition, cleanerPoint));
    Files.write(orphan, new byte[] {1, 2, 3});
    return orphan;
  }

  @Test
  void shouldNotUnlinkFileWhileLeaseHeldThenUnlinkOnceReleased() throws IOException {
    // given a superseded file with a reader lease held
    final Path orphan = orphanFile(100, 200);
    final TrashQueue trash = trashQueue(() -> true);
    trash.enqueue(orphan);
    final ReaderLease lease = leases.acquire(orphan);

    // when draining while the lease is held
    assertThat(trash.drain()).isZero();

    // then the file survives all other conditions
    assertThat(Files.exists(orphan)).isTrue();

    // when the lease is released and we drain again
    lease.release();
    final int deleted = trash.drain();

    // then it is unlinked
    assertThat(deleted).isEqualTo(1);
    assertThat(Files.exists(orphan)).isFalse();
  }

  @Test
  void shouldNotUnlinkWhileExternalPredicateFalse() throws IOException {
    // given an external predicate that is currently false
    final Path orphan = orphanFile(100, 200);
    final var allowed = new AtomicBoolean(false);
    final TrashQueue trash = trashQueue(allowed::get);
    trash.enqueue(orphan);

    // when draining
    assertThat(trash.drain()).isZero();
    // then it survives
    assertThat(Files.exists(orphan)).isTrue();

    // when the predicate permits deletion
    allowed.set(true);
    // then it is unlinked
    assertThat(trash.drain()).isEqualTo(1);
    assertThat(Files.exists(orphan)).isFalse();
  }

  @Test
  void shouldNeverUnlinkFileReferencedByLatestManifest() {
    // given the committed manifest's own segment is (incorrectly) enqueued
    final TrashQueue trash = trashQueue(() -> true);
    trash.enqueue(keptFile);

    // when draining
    final int deleted = trash.drain();

    // then it is protected by the reference check
    assertThat(deleted).isZero();
    assertThat(Files.exists(keptFile)).isTrue();
  }

  @Test
  void shouldRecoverOrphansFromManifestDiffAfterCrashBeforeDrain() throws IOException {
    // given a crash lost the in-memory queue: an orphan segment and a stale temp file are on disk
    final Path orphan = orphanFile(100, 200);
    final Path staleTmp = dir.resolve(CleanSegmentFiles.tmpName(300, 400));
    Files.write(staleTmp, new byte[] {9});

    // when a fresh queue recovers from the directory/manifest diff on restart
    final TrashQueue trash = trashQueue(() -> true);
    trash.recoverOrphans();

    // then the temp file is gone immediately and the orphan is re-enqueued
    assertThat(Files.exists(staleTmp)).isFalse();
    assertThat(trash.pendingCount()).isEqualTo(1);

    // when draining
    assertThat(trash.drain()).isEqualTo(1);

    // then the orphan is unlinked and the referenced file is untouched
    assertThat(Files.exists(orphan)).isFalse();
    assertThat(Files.exists(keptFile)).isTrue();
  }
}
