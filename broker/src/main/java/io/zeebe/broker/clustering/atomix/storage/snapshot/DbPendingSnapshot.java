/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.storage.snapshot.PendingSnapshot;
import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.util.ZbLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

/**
 * Represents a pending snapshot, that is a snapshot in the process of being written. It is not in a
 * usable state as it may be mutated at any moment; primarily used for replication.
 */
public class DbPendingSnapshot implements PendingSnapshot {
  private static final Logger LOGGER = new ZbLogger(DbPendingSnapshot.class);

  private final long index;
  private final long term;
  private final WallClockTimestamp timestamp;

  private final Path directory;
  private final DbSnapshotStore snapshotStore;

  private ByteBuffer expectedId;

  /**
   * @param index the snapshot's index
   * @param term the snapshot's term
   * @param timestamp the snapshot's creation timestamp
   * @param directory the snapshot's working directory (i.e. where we should write chunks)
   * @param snapshotStore the store which will be called when the snapshot is to be committed
   */
  public DbPendingSnapshot(
      final long index,
      final long term,
      final WallClockTimestamp timestamp,
      final Path directory,
      final DbSnapshotStore snapshotStore) {
    this.index = index;
    this.term = term;
    this.timestamp = timestamp;
    this.directory = directory;
    this.snapshotStore = snapshotStore;
  }

  @Override
  public long index() {
    return index;
  }

  @Override
  public long term() {
    return term;
  }

  @Override
  public WallClockTimestamp timestamp() {
    return timestamp;
  }

  @Override
  public boolean containsChunk(final ByteBuffer chunkId) {
    return Files.exists(directory.resolve(getFile(chunkId)));
  }

  @Override
  public boolean isExpectedChunk(final ByteBuffer chunkId) {
    if (expectedId == null) {
      return chunkId == null;
    }

    return expectedId.equals(chunkId);
  }

  @Override
  public void write(final ByteBuffer chunkId, final ByteBuffer chunkData) {
    final var filename = getFile(chunkId);
    final var path = directory.resolve(filename);

    try (var channel =
        Files.newByteChannel(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      channel.write(chunkData);
    } catch (final FileAlreadyExistsException e) {
      LOGGER.debug("Chunk {} of pending snapshot {} already exists at {}", filename, this, path, e);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void setNextExpected(final ByteBuffer nextChunkId) {
    expectedId = nextChunkId;
  }

  @Override
  public void commit() {
    snapshotStore.put(this);
  }

  @Override
  public void abort() {
    try {
      Files.delete(directory);
    } catch (final IOException e) {
      LOGGER.warn("Failed to delete pending snapshot {}", this, e);
    }
  }

  @Override
  public String toString() {
    return "DbPendingSnapshot{"
        + "index="
        + index
        + ", term="
        + term
        + ", timestamp="
        + timestamp
        + ", directory="
        + directory
        + '}';
  }

  private String getFile(final ByteBuffer chunkId) {
    final var view = new UnsafeBuffer(chunkId);
    return view.getStringWithoutLengthAscii(0, chunkId.remaining());
  }
}
