/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.raft.protocol.PersistedRaftRecord;
import io.atomix.raft.protocol.ReplicatableJournalRecord;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.camunda.zeebe.broker.system.partitions.AtomixRecordEntrySupplier;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotListener;
import io.camunda.zeebe.snapshots.SnapshotChunkReader;
import io.camunda.zeebe.snapshots.SnapshotException;
import io.camunda.zeebe.snapshots.SnapshotId;
import io.camunda.zeebe.snapshots.SnapshotMetadata;
import io.camunda.zeebe.snapshots.SnapshotReservation;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the {@link ManifestStore} &rarr; Raft-snapshot adapter, driven against an
 * in-memory {@link ConstructableSnapshotStore} test double rather than the real {@code
 * FileBasedSnapshotStore} (which requires a running actor scheduler — no precedent for that exists
 * yet in this module, and adding one would need a new test-scoped pom dependency; see the task
 * report). The double still exercises real file I/O (hardlinking, manifest read/write) against
 * {@code @TempDir}s, so the adapter's own logic — position resolution, content placement, and the
 * install-on-latest self-heal — is genuinely under test.
 */
final class SnapshotManifestStoreTest {

  private static final int PARTITION_ID = 1;

  @TempDir private Path compactionDir;
  @TempDir private Path snapshotStoreRoot;

  private SnapshotManifestStore storeWith(final AtomixRecordEntrySupplier entrySupplier) {
    return storeWith(entrySupplier, new FakeSnapshotStore(snapshotStoreRoot));
  }

  private SnapshotManifestStore storeWith(
      final AtomixRecordEntrySupplier entrySupplier, final FakeSnapshotStore snapshotStore) {
    return new SnapshotManifestStore(
        PARTITION_ID, compactionDir, snapshotStore, entrySupplier, Duration.ofSeconds(5));
  }

  @Test
  void shouldReturnEmptyBeforeAnyCommit() {
    // given
    final var store = storeWith(position -> Optional.empty());

    // then
    assertThat(store.latest()).isEmpty();
  }

  @Test
  void shouldFailCommitWhenCleanerPointIsNotYetIndexed() {
    // given: an empty/very young log — the position can't be resolved to a raft index/term yet
    final var store = storeWith(position -> Optional.empty());
    final var manifest = new CompactionManifest(CompactionManifest.VERSION_1, 5, 100, List.of());

    // when / then
    assertThatThrownBy(() -> store.commit(manifest, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot resolve a raft index/term");
    assertThat(store.latest()).isEmpty();
  }

  @Test
  void shouldCommitTheManifestAsTheLatestPersistedSnapshot() throws IOException {
    // given
    final var store = storeWith(position -> Optional.of(fakeEntry(7, 3)));
    final Path segmentFile = writeSegment("seg-a", "hello");
    final var segment = segmentOf(segmentFile, 1, "hello");
    final var manifest =
        new CompactionManifest(CompactionManifest.VERSION_1, 5, 999, List.of(segment));

    // when
    store.commit(manifest, List.of(segmentFile));

    // then
    final var latest = store.latest();
    assertThat(latest).isPresent();
    assertThat(latest.get().cleanerPoint()).isEqualTo(5);
    assertThat(latest.get().maxLogTimestamp()).isEqualTo(999);
    assertThat(latest.get().segments()).extracting(CleanSegment::fileName).containsExactly("seg-a");
  }

  @Test
  void shouldInstallMissingSegmentsFromThePersistedSnapshotOnLatest() throws IOException {
    // given a committed manifest whose segment is then lost locally (fresh disk / a follower right
    // after Raft's InstallSnapshot landed new content)
    final var store = storeWith(position -> Optional.of(fakeEntry(7, 3)));
    final Path segmentFile = writeSegment("seg-a", "hello");
    final var segment = segmentOf(segmentFile, 1, "hello");
    final var manifest =
        new CompactionManifest(CompactionManifest.VERSION_1, 5, 999, List.of(segment));
    store.commit(manifest, List.of(segmentFile));
    Files.delete(segmentFile);
    assertThat(segmentFile).doesNotExist();

    // when
    final var reread = store.latest();

    // then: latest() re-installed the segment into the compaction working directory
    assertThat(reread).isPresent();
    assertThat(segmentFile).exists();
    assertThat(Files.readString(segmentFile)).isEqualTo("hello");
  }

  @Test
  void shouldLeavePriorSnapshotAuthoritativeWhenTakeFails() throws IOException {
    // given a first successful commit
    final var snapshotStore = new FakeSnapshotStore(snapshotStoreRoot);
    final var firstStore = storeWith(position -> Optional.of(fakeEntry(4, 1)), snapshotStore);
    final Path firstSegment = writeSegment("seg-1", "v1");
    final var firstManifest =
        new CompactionManifest(
            CompactionManifest.VERSION_1, 3, 100, List.of(segmentOf(firstSegment, 1, "v1")));
    firstStore.commit(firstManifest, List.of(firstSegment));

    // when a second pass's take() fails (simulated I/O failure while writing snapshot content)
    snapshotStore.failNextTake = true;
    final var secondStore = storeWith(position -> Optional.of(fakeEntry(9, 2)), snapshotStore);
    final Path secondSegment = writeSegment("seg-2", "v2");
    final var secondManifest =
        new CompactionManifest(
            CompactionManifest.VERSION_1, 6, 200, List.of(segmentOf(secondSegment, 4, "v2")));

    // then
    assertThatThrownBy(() -> secondStore.commit(secondManifest, List.of(secondSegment)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("failed to take");

    // and: the first manifest remains authoritative
    final var latest = firstStore.latest();
    assertThat(latest).isPresent();
    assertThat(latest.get().cleanerPoint()).isEqualTo(3);
  }

  private Path writeSegment(final String name, final String content) throws IOException {
    final Path file = compactionDir.resolve(name);
    Files.writeString(file, content);
    return file;
  }

  private static CleanSegment segmentOf(
      final Path file, final long firstPosition, final String content) {
    final var crc = new CRC32();
    crc.update(content.getBytes());
    return new CleanSegment(
        file.getFileName().toString(), firstPosition, content.length(), crc.getValue());
  }

  private static IndexedRaftLogEntry fakeEntry(final long index, final long term) {
    return new IndexedRaftLogEntry() {
      @Override
      public long index() {
        return index;
      }

      @Override
      public long term() {
        return term;
      }

      @Override
      public io.atomix.raft.storage.log.entry.RaftEntry entry() {
        throw new UnsupportedOperationException();
      }

      @Override
      public ApplicationEntry getApplicationEntry() {
        throw new UnsupportedOperationException();
      }

      @Override
      public PersistedRaftRecord getPersistedRaftRecord() {
        throw new UnsupportedOperationException();
      }

      @Override
      public ReplicatableJournalRecord getReplicatableJournalRecord() {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * A minimal, stateful {@link ConstructableSnapshotStore} double: {@code take} writes into a real
   * temp "pending" directory and {@code persist} moves it into a real "persisted" directory,
   * becoming the new {@link #getLatestSnapshot()}. Every method beyond what {@link
   * SnapshotManifestStore} actually calls throws — this double exists to exercise the adapter's
   * logic, not to be a general-purpose snapshot store.
   */
  private static final class FakeSnapshotStore implements ConstructableSnapshotStore {

    private final Path root;
    private PersistedSnapshot latest;
    private int nextId;
    boolean failNextTake;

    private FakeSnapshotStore(final Path root) {
      this.root = root;
    }

    @Override
    public Either<SnapshotException, TransientSnapshot> newTransientSnapshot(
        final long index,
        final long term,
        final long processedPosition,
        final long exportedPosition,
        final boolean forceSnapshot) {
      final Path pendingDir = root.resolve("pending-" + nextId++);
      try {
        Files.createDirectories(pendingDir);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
      final boolean shouldFailTake = failNextTake;
      failNextTake = false;
      return Either.right(new FakeTransientSnapshot(pendingDir, shouldFailTake));
    }

    private final class FakeTransientSnapshot implements TransientSnapshot {
      private final Path pendingDir;
      private final boolean shouldFailTake;

      private FakeTransientSnapshot(final Path pendingDir, final boolean shouldFailTake) {
        this.pendingDir = pendingDir;
        this.shouldFailTake = shouldFailTake;
      }

      @Override
      public ActorFuture<Void> take(final Consumer<Path> takeSnapshot) {
        if (shouldFailTake) {
          return CompletableActorFuture.completedExceptionally(
              new UncheckedIOException(new IOException("simulated take failure")));
        }
        takeSnapshot.accept(pendingDir);
        return CompletableActorFuture.completed(null);
      }

      @Override
      public TransientSnapshot withLastFollowupEventPosition(final long followupEventPosition) {
        return this;
      }

      @Override
      public TransientSnapshot withMaxExportedPosition(final long maxExportedPosition) {
        return this;
      }

      @Override
      public ActorFuture<Void> abort() {
        try {
          io.camunda.zeebe.util.FileUtil.deleteFolderIfExists(pendingDir);
        } catch (final IOException e) {
          // best-effort, mirrors production abort semantics
        }
        return CompletableActorFuture.completed(null);
      }

      @Override
      public ActorFuture<PersistedSnapshot> persist() {
        final Path persistedDir = root.resolve("persisted-" + nextId++);
        try {
          Files.move(pendingDir, persistedDir, StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException e) {
          return CompletableActorFuture.completedExceptionally(new UncheckedIOException(e));
        }
        final var persisted = new FakePersistedSnapshot(persistedDir);
        latest = persisted;
        return CompletableActorFuture.completed(persisted);
      }

      @Override
      public SnapshotId snapshotId() {
        throw new UnsupportedOperationException();
      }

      @Override
      public Path getPath() {
        return pendingDir;
      }
    }

    private record FakePersistedSnapshot(Path path) implements PersistedSnapshot {
      @Override
      public Path getPath() {
        return path;
      }

      @Override
      public int version() {
        throw new UnsupportedOperationException();
      }

      @Override
      public SnapshotId snapshotId() {
        throw new UnsupportedOperationException();
      }

      @Override
      public long getIndex() {
        throw new UnsupportedOperationException();
      }

      @Override
      public long getTerm() {
        throw new UnsupportedOperationException();
      }

      @Override
      public SnapshotChunkReader newChunkReader() {
        throw new UnsupportedOperationException();
      }

      @Override
      public Path getChecksumPath() {
        throw new UnsupportedOperationException();
      }

      @Override
      public long getCompactionBound() {
        throw new UnsupportedOperationException();
      }

      @Override
      public String getId() {
        throw new UnsupportedOperationException();
      }

      @Override
      public io.camunda.zeebe.snapshots.ImmutableChecksumsSFV getChecksums() {
        throw new UnsupportedOperationException();
      }

      @Override
      public SnapshotMetadata getMetadata() {
        return null;
      }

      @Override
      public ActorFuture<SnapshotReservation> reserve() {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean isReserved() {
        return false;
      }
    }

    @Override
    public Optional<PersistedSnapshot> getLatestSnapshot() {
      return Optional.ofNullable(latest);
    }

    @Override
    public boolean hasSnapshotId(final String id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ActorFuture<Set<PersistedSnapshot>> getAvailableSnapshots() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ActorFuture<Long> getCompactionBound() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ActorFuture<Void> abortPendingSnapshots() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ActorFuture<Boolean> addSnapshotListener(final PersistedSnapshotListener listener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ActorFuture<Boolean> removeSnapshotListener(final PersistedSnapshotListener listener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getCurrentSnapshotIndex() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ActorFuture<Void> delete() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Path getPath() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<PersistedSnapshot> getBootstrapSnapshot() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ActorFuture<PersistedSnapshot> copyForBootstrap(
        final PersistedSnapshot persistedSnapshot, final BiConsumer<Path, Path> copySnapshot) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ActorFuture<Void> deleteBootstrapSnapshots() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() {}
  }
}
