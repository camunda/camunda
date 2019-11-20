/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.util;

import io.zeebe.logstreams.state.Snapshot;
import io.zeebe.logstreams.state.SnapshotDeletionListener;
import io.zeebe.logstreams.state.SnapshotStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;
import org.agrona.IoUtil;

public class TestSnapshotStorage implements SnapshotStorage {

  private final Path pendingDirectory;
  private final Path snapshotsDirectory;
  private final Path runtimeDirectory;
  private final SortedSet<Snapshot> snapshots;
  private final Set<SnapshotDeletionListener> deletionListeners;

  public TestSnapshotStorage(final Path rootDirectory) {
    this.pendingDirectory = rootDirectory.resolve("pending");
    this.snapshotsDirectory = rootDirectory.resolve("snapshots");
    this.runtimeDirectory = rootDirectory.resolve("runtime");

    this.snapshots = new ConcurrentSkipListSet<>();
    this.deletionListeners = new CopyOnWriteArraySet<>();

    open();
  }

  public void open() {
    IoUtil.ensureDirectoryExists(snapshotsDirectory.toFile(), "snapshots directory");
    IoUtil.ensureDirectoryExists(pendingDirectory.toFile(), "pending snapshots directory");

    try {
      Files.list(snapshotsDirectory).forEach(this::commitSnapshot);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public Path getPendingDirectoryFor(final long snapshotPosition) {
    return getPendingDirectoryFor(String.valueOf(snapshotPosition));
  }

  @Override
  public Path getPendingDirectoryFor(final String id) {
    return pendingDirectory.resolve(id);
  }

  @Override
  public boolean commitSnapshot(final Path snapshotPath) {
    final var id = snapshotPath.getFileName().toString();
    if (exists(id)) {
      return true;
    }

    final var destination = snapshotsDirectory.resolve(snapshotPath.getFileName());
    final var snapshot = new SnapshotImpl(destination);
    try {
      Files.move(snapshotPath, destination);
    } catch (FileAlreadyExistsException ignored) {
      // safe to ignore
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    snapshots.add(snapshot);
    return true;
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

  private static final class SnapshotImpl implements Snapshot {
    private final Path path;
    private final long position;

    private SnapshotImpl(final Path path) {
      this.path = path;
      this.position = Long.valueOf(path.getFileName().toString());
    }

    @Override
    public long getPosition() {
      return position;
    }

    @Override
    public Path getPath() {
      return path;
    }

    @Override
    public String toString() {
      return "SnapshotImpl{" + "path=" + path + ", position=" + position + '}';
    }
  }
}
