/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.snapshots.ImmutableChecksumsSFV;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;

/**
 * Supports calculating rolling CRC32C checksums for a set of snapshot files received as sequential
 * chunks.
 *
 * <p>Chunks must be sequential and contiguous with regards to each file, but chunks from different
 * files may be interleaved.
 */
final class IncrementalChecksums {
  private final Map<String, FileChecksum> fileChecksums = new HashMap<>();

  /**
   * Update the rolling checksum for a single file with the next received chunk for that file.
   *
   * <p>This method will throw an exception if the chunk is not the next expected chunk for the
   * given file (according to the previously seen offsets and sizes), or if the observed total size
   * for the file changes.
   *
   * @param chunk The chunk.
   * @throws IllegalArgumentException If the chunk was not the next expected chunk for the given
   *     file, or had a different total file size.
   */
  public void update(final SnapshotChunk chunk) {
    fileChecksums.compute(
        chunk.getChunkName(),
        (name, checksum) -> {
          if (checksum == null) {
            return FileChecksum.of(chunk);
          } else {
            checksum.update(chunk);
            return checksum;
          }
        });
  }

  /**
   * Validate and return the completed checksums.
   *
   * <p>This method will throw an exception if any file was only partially processed (i.e. we saw at
   * least one chunk for it, but not all chunks).
   *
   * @return The completed checksums.
   * @throws IllegalStateException If any file was only partially processed.
   */
  public ImmutableChecksumsSFV complete() {
    final var checksums = new TreeMap<String, Long>();
    for (final var entry : fileChecksums.entrySet()) {
      final var fileName = entry.getKey();
      final var fileChecksum = entry.getValue();
      if (!fileChecksum.isComplete()) {
        throw new IllegalStateException(
            "Checksum for file %s is not complete (had size %s, expected size %s)"
                .formatted(fileName, fileChecksum.currentSize, fileChecksum.totalSize));
      }

      checksums.put(fileName, fileChecksum.checksum.getValue());
    }

    return new SfvChecksumImpl(checksums);
  }

  private static final class FileChecksum {
    private final Checksum checksum;
    private long currentSize;
    private final long totalSize;

    private FileChecksum(final Checksum checksum, final long currentSize, final long totalSize) {
      this.checksum = checksum;
      this.currentSize = currentSize;
      this.totalSize = totalSize;
    }

    public static FileChecksum of(final SnapshotChunk chunk) {
      final long fileBlockPosition = chunk.getFileBlockPosition();
      if (fileBlockPosition != 0) {
        throw new IllegalArgumentException(
            "Expected first chunk at offset 0 but got %s".formatted(fileBlockPosition));
      }

      final var checksum = new FileChecksum(new CRC32C(), 0, chunk.getTotalFileSize());
      checksum.update(chunk);
      return checksum;
    }

    public boolean isComplete() {
      return currentSize == totalSize;
    }

    public void update(final SnapshotChunk chunk) {
      final long fileBlockPosition = chunk.getFileBlockPosition();
      if (fileBlockPosition != currentSize) {
        throw new IllegalArgumentException(
            "Expected next chunk at offset %s but got %s"
                .formatted(currentSize, fileBlockPosition));
      }

      final long totalFileSize = chunk.getTotalFileSize();
      if (totalFileSize != totalSize) {
        throw new IllegalArgumentException(
            "Expected chunk to match totalSize %s but got %s".formatted(totalSize, totalFileSize));
      }

      final byte[] content = chunk.getContent();
      final long newSize = currentSize + content.length;
      if (newSize > totalSize) {
        throw new IllegalArgumentException(
            "Chunk size %s + current size %s exceeds total size %s"
                .formatted(content.length, currentSize, totalSize));
      }

      checksum.update(content);
      currentSize = newSize;
    }
  }
}
