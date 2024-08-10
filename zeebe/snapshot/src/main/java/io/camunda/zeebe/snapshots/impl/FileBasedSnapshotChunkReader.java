/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.snapshots.SnapshotChunkReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Implements a chunk reader where each chunk is a single file in a root directory. Chunks are then
 * ordered lexicographically, and the files are assumed to be immutable, i.e. no more are added to
 * the directory once this is created.
 */
public final class FileBasedSnapshotChunkReader implements SnapshotChunkReader {
  private final Path directory;
  private final NavigableSet<CharSequence> chunks;

  private long offset;
  private NavigableSet<CharSequence> chunksView;
  private final int totalCount;
  private final String snapshotID;
  private long maximumChunkSize;

  public FileBasedSnapshotChunkReader(final Path directory) throws IOException {
    this(directory, Long.MAX_VALUE);
  }

  FileBasedSnapshotChunkReader(final Path directory, final long maximumChunkSize)
      throws IOException {
    this.directory = directory;
    chunks = collectChunks(directory);
    totalCount = chunks.size();
    chunksView = new TreeSet<>(chunks);

    snapshotID = directory.getFileName().toString();

    this.maximumChunkSize = maximumChunkSize;
  }

  private NavigableSet<CharSequence> collectChunks(final Path directory) throws IOException {
    final var set = new TreeSet<>(CharSequence::compare);
    try (final var stream = Files.list(directory).sorted()) {
      stream.map(directory::relativize).map(Path::toString).forEach(set::add);
    }
    return set;
  }

  @Override
  public void reset() {
    chunksView = new TreeSet<>(chunks);
  }

  @Override
  public void seek(final ByteBuffer id) {
    if (id == null) {
      return;
    }

    final var chunkId = new SnapshotChunkId(id);

    offset = chunkId.offset();

    chunksView = new TreeSet<>(chunks.tailSet(chunkId.fileName(), true));
  }

  @Override
  public ByteBuffer nextId() {
    if (chunksView.isEmpty()) {
      return null;
    }

    return new SnapshotChunkId(chunksView.first().toString(), offset).id();
  }

  @Override
  public void setMaximumChunkSize(final int maximumChunkSize) {
    this.maximumChunkSize = maximumChunkSize;
  }

  @Override
  public void close() {
    chunks.clear();
    chunksView.clear();
  }

  @Override
  public boolean hasNext() {
    return !chunksView.isEmpty();
  }

  @Override
  public SnapshotChunk next() {
    final var fileName = chunksView.first().toString();
    final var filePath = directory.resolve(fileName).toString();

    try (final var file = new RandomAccessFile(filePath, "r")) {
      final var fileLength = file.length();
      final var bytesToRead = Math.min(maximumChunkSize, fileLength - offset);
      final byte[] buffer = new byte[(int) bytesToRead];
      file.seek(offset);
      file.readFully(buffer);

      final var fileBlockPosition = offset;
      offset += bytesToRead;
      if (offset == fileLength) {
        offset = 0;
        chunksView.pollFirst();
      }

      return SnapshotChunkUtil.createSnapshotChunkFromFileChunk(
          snapshotID, totalCount, fileName, buffer, fileBlockPosition, fileLength);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
