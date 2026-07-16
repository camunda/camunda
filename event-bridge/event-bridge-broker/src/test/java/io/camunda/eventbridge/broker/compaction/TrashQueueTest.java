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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Deferred, condemn-then-unlink deletion: reference and external-predicate gates, the lease
 * interlock (marker unlinked by the last release), open-descriptor survival, and crash recovery
 * from the directory/manifest diff.
 */
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
    final var writer = new CleanSegmentWriter(dir, 5, 1 << 20);
    writer.append(put(1, "a", "va"));
    final List<CleanSegment> segments = writer.finish();
    store.commit(
        new CompactionManifest(CompactionManifest.VERSION_1, 5, 1, segments),
        List.of(dir.resolve(segments.get(0).fileName())));
    keptFile = dir.resolve(segments.get(0).fileName());
  }

  private TrashQueue trashQueue(final BooleanSupplier externalPredicate) {
    final Supplier<Optional<CompactionManifest>> latest = store::latest;
    return new TrashQueue(dir, leases, latest, externalPredicate);
  }

  /** Writes a real (readable) orphan segment not referenced by the committed manifest. */
  private Path orphanSegment(final long firstPosition, final long cleanerPoint) {
    final var writer = new CleanSegmentWriter(dir, cleanerPoint, 1 << 20);
    writer.append(put(firstPosition, "orphan", "orphan-value"));
    final CleanSegment segment = writer.finish().get(0);
    return dir.resolve(segment.fileName());
  }

  private Path condemnedPathOf(final Path original) {
    return dir.resolve(CleanSegmentFiles.condemnedName(original.getFileName().toString()));
  }

  @Test
  void shouldDeferUnlinkToLeaseReleaseAndKeepBytesReadable() {
    // given a superseded file with a reader lease (and its open channel) held
    final Path orphan = orphanSegment(100, 200);
    final TrashQueue trash = trashQueue(() -> true);
    trash.enqueue(orphan);
    final ReaderLease lease = leases.acquire(orphan).orElseThrow();

    // when draining while the lease is held
    final int unlinkedNow = trash.drain();

    // then — the file is condemned (renamed) but not unlinked; the lease keeps the bytes readable
    assertThat(unlinkedNow).isZero();
    assertThat(Files.exists(orphan)).isFalse();
    assertThat(Files.exists(condemnedPathOf(orphan))).isTrue();
    final var reader = new CleanSegmentReader(lease);
    assertThat(CompactionRecords.value(reader.next())).isEqualTo("orphan-value");

    // when the lease is released
    lease.release();

    // then — the last release performs the deferred unlink of the marker
    assertThat(Files.exists(condemnedPathOf(orphan))).isFalse();
  }

  @Test
  void shouldKeepLeasedBytesReadableEvenAfterTheMarkerIsUnlinked() {
    // given a held lease on a file that gets condemned AND its marker swept (as recoverOrphans
    // does on the next pass after a crash)
    final Path orphan = orphanSegment(100, 200);
    final TrashQueue trash = trashQueue(() -> true);
    trash.enqueue(orphan);
    final ReaderLease lease = leases.acquire(orphan).orElseThrow();
    trash.drain();

    // when the marker is unlinked while the lease is still held
    final TrashQueue recovered = trashQueue(() -> true);
    recovered.recoverOrphans();
    assertThat(Files.exists(condemnedPathOf(orphan))).isFalse();

    // then — the lease's open descriptor still reads the full content (POSIX unlink semantics)
    final var reader = new CleanSegmentReader(lease);
    assertThat(CompactionRecords.value(reader.next())).isEqualTo("orphan-value");
    lease.release();
  }

  @Test
  void shouldSignalRetryWhenAcquiringACondemnedFile() {
    // given a file that the queue has already condemned
    final Path orphan = orphanSegment(100, 200);
    final TrashQueue trash = trashQueue(() -> true);
    trash.enqueue(orphan);
    assertThat(trash.drain()).isEqualTo(1);

    // when a reader tries to acquire it under its original name
    final Optional<ReaderLease> lease = leases.acquire(orphan);

    // then — no lease; the caller re-resolves from the latest committed manifest
    assertThat(lease).isEmpty();
    assertThat(leases.leaseCount(orphan)).isZero();
  }

  @Test
  void shouldNotCondemnWhileExternalPredicateFalse() {
    // given an external predicate that is currently false
    final Path orphan = orphanSegment(100, 200);
    final var allowed = new AtomicBoolean(false);
    final TrashQueue trash = trashQueue(allowed::get);
    trash.enqueue(orphan);

    // when draining
    assertThat(trash.drain()).isZero();
    // then — the file is untouched under its original name (not even condemned)
    assertThat(Files.exists(orphan)).isTrue();

    // when the predicate permits deletion
    allowed.set(true);
    // then — condemned and unlinked in one drain (no leases)
    assertThat(trash.drain()).isEqualTo(1);
    assertThat(Files.exists(orphan)).isFalse();
    assertThat(Files.exists(condemnedPathOf(orphan))).isFalse();
  }

  @Test
  void shouldNeverCondemnFileReferencedByLatestManifest() {
    // given the committed manifest's own segment is (incorrectly) enqueued
    final TrashQueue trash = trashQueue(() -> true);
    trash.enqueue(keptFile);

    // when draining
    final int deleted = trash.drain();

    // then — it is protected by the reference check, untouched under its original name
    assertThat(deleted).isZero();
    assertThat(Files.exists(keptFile)).isTrue();
  }

  @Test
  void shouldRecoverOrphansMarkersAndTornManifestTmpAfterCrash() throws IOException {
    // given a crash lost the in-memory queue: an orphan segment, a stale staging temp file, a
    // stale condemned marker, and a torn manifest.tmp are on disk
    final Path orphan = orphanSegment(100, 200);
    final Path staleTmp = dir.resolve(CleanSegmentFiles.tmpName(300, 400));
    Files.write(staleTmp, new byte[] {9});
    final Path staleMarker =
        dir.resolve(CleanSegmentFiles.condemnedName(CleanSegmentFiles.segmentName(500, 600)));
    Files.write(staleMarker, new byte[] {8});
    final Path tornManifestTmp = dir.resolve(FileManifestStore.MANIFEST_TMP);
    Files.write(tornManifestTmp, new byte[] {7});

    // when a fresh queue recovers from the directory/manifest diff on restart
    final TrashQueue trash = trashQueue(() -> true);
    trash.recoverOrphans();

    // then — all unconditional garbage is gone immediately and the orphan is re-enqueued
    assertThat(Files.exists(staleTmp)).isFalse();
    assertThat(Files.exists(staleMarker)).isFalse();
    assertThat(Files.exists(tornManifestTmp)).isFalse();
    assertThat(trash.pendingCount()).isEqualTo(1);

    // when draining
    assertThat(trash.drain()).isEqualTo(1);

    // then — the orphan is unlinked and the referenced file is untouched
    assertThat(Files.exists(orphan)).isFalse();
    assertThat(Files.exists(keptFile)).isTrue();
  }
}
