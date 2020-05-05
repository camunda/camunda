/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.util;

import io.atomix.raft.impl.zeebe.snapshot.Snapshot;
import io.atomix.raft.impl.zeebe.snapshot.SnapshotDeletionListener;
import io.atomix.raft.impl.zeebe.snapshot.SnapshotMetrics;
import io.atomix.raft.impl.zeebe.snapshot.SnapshotStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;
import org.agrona.IoUtil;

public final class TestSnapshotStorage implements SnapshotStorage {

  private final Path pendingDirectory;
  private final Path snapshotsDirectory;
  private final Path runtimeDirectory;
  private final SortedSet<Snapshot> snapshots;
  private final Set<SnapshotDeletionListener> deletionListeners;
  private final SnapshotMetrics metrics;

  public TestSnapshotStorage(final Path rootDirectory) {
    this.pendingDirectory = rootDirectory.resolve("pending");
    this.snapshotsDirectory = rootDirectory.resolve("snapshots");
    this.runtimeDirectory = rootDirectory.resolve("runtime");

    this.snapshots = new ConcurrentSkipListSet<>();
    this.deletionListeners = new CopyOnWriteArraySet<>();
    this.metrics = new SnapshotMetrics(0);

    open();
  }

  public void open() {
    IoUtil.ensureDirectoryExists(snapshotsDirectory.toFile(), "snapshots directory");
    IoUtil.ensureDirectoryExists(pendingDirectory.toFile(), "pending snapshots directory");

    try {
      Files.list(snapshotsDirectory).map(SnapshotImpl::new).forEach(this::commitSnapshot);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public Optional<Snapshot> getPendingSnapshotFor(final long snapshotPosition) {
    final var pendingDirectory =
        getPendingDirectoryFor(snapshotPosition + "_" + System.currentTimeMillis());
    return pendingDirectory.map(SnapshotImpl::new);
  }

  @Override
  public Optional<Path> getPendingDirectoryFor(final String id) {
    return Optional.of(pendingDirectory.resolve(id));
  }

  @Override
  public Optional<Snapshot> commitSnapshot(final Path snapshotPath) {
    final var existingSnapshot =
        snapshots.stream()
            .filter(s -> s.getPath().getFileName().equals(snapshotPath.getFileName()))
            .findFirst();
    if (existingSnapshot.isPresent()) {
      return existingSnapshot;
    }

    final var destination = snapshotsDirectory.resolve(snapshotPath.getFileName());
    try {
      Files.move(snapshotPath, destination);
    } catch (final FileAlreadyExistsException ignored) {
      // safe to ignore
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final var committedSnapshot = new SnapshotImpl(destination);
    snapshots.add(committedSnapshot);
    return Optional.of(committedSnapshot);
  }

  @Override
  public Optional<Snapshot> getLatestSnapshot() {
    // must check as `snapshots.last()` throws an exception if empty...
    if (snapshots.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(snapshots.last());
  }

  @Override
  public Stream<Snapshot> getSnapshots() {
    return snapshots.stream();
  }

  @Override
  public Path getRuntimeDirectory() {
    return runtimeDirectory;
  }

  @Override
  public boolean exists(final String id) {
    return getSnapshots()
        .map(Snapshot::getPath)
        .map(Path::getFileName)
        .anyMatch(p -> p.toString().equals(id));
  }

  @Override
  public void close() {
    // nothing to be done
  }

  @Override
  public void addDeletionListener(final SnapshotDeletionListener listener) {
    deletionListeners.add(listener);
  }

  @Override
  public void removeDeletionListener(final SnapshotDeletionListener listener) {
    deletionListeners.remove(listener);
  }

  @Override
  public SnapshotMetrics getMetrics() {
    return metrics;
  }

  private static final class SnapshotImpl implements Snapshot {
    private final Path path;
    private final long compactionBound;

    private SnapshotImpl(final Path path) {
      this.path = path;
      final var snapshotDir = path.getFileName().toString();
      final var parts = snapshotDir.split("_");
      this.compactionBound = Long.valueOf(parts[0]);
    }

    @Override
    public long getCompactionBound() {
      return compactionBound;
    }

    @Override
    public Path getPath() {
      return path;
    }

    @Override
    public int compareTo(final Snapshot other) {
      return Comparator.comparing(Snapshot::getCompactionBound)
          .thenComparing(Snapshot::getPath)
          .compare(this, other);
    }

    @Override
    public int hashCode() {
      return Objects.hash(path);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final SnapshotImpl snapshot = (SnapshotImpl) o;
      return path.equals(snapshot.path);
    }

    @Override
    public String toString() {
      return "SnapshotImpl{" + "path=" + path + ", compactionBound=" + compactionBound + '}';
    }
  }
}
