/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import static com.google.common.base.Preconditions.checkArgument;

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
   */
  public void update(final SnapshotChunk chunk) {
    fileChecksums.compute(
        chunk.getChunkName(),
        (name, checksum) -> {
          if (checksum == null) {
            return FileChecksum.of(chunk);
          } else {
            return checksum.next(chunk);
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
   */
  public ImmutableChecksumsSFV complete() {
    final var checksums = new TreeMap<String, Long>();
    for (final var entry : fileChecksums.entrySet()) {
      final var fileName = entry.getKey();
      final var fileChecksum = entry.getValue();
      if (!fileChecksum.isComplete()) {
        throw new IllegalStateException(
            "Checksum for file %s is not complete (had size %s, expected size %s)"
                .formatted(fileName, fileChecksum.size(), fileChecksum.totalSize()));
      }

      checksums.put(fileName, fileChecksum.checksum().getValue());
    }

    return new SfvChecksumImpl(checksums);
  }

  private record FileChecksum(Checksum checksum, long size, long totalSize) {
    public static FileChecksum of(final SnapshotChunk chunk) {
      final long fileBlockPosition = chunk.getFileBlockPosition();
      checkArgument(
          fileBlockPosition == 0, "Expected first chunk at offset 0 but got %s", fileBlockPosition);

      return new FileChecksum(new CRC32C(), 0, chunk.getTotalFileSize()).next(chunk);
    }

    public boolean isComplete() {
      return size == totalSize;
    }

    public FileChecksum next(final SnapshotChunk chunk) {
      final long fileBlockPosition = chunk.getFileBlockPosition();
      checkArgument(
          fileBlockPosition == size,
          "Expected next chunk at offset %s but got %s",
          size,
          fileBlockPosition);

      final long totalFileSize = chunk.getTotalFileSize();
      checkArgument(
          totalFileSize == totalSize,
          "Expected chunk to match totalSize %s but got %s",
          totalSize,
          totalFileSize);

      final byte[] content = chunk.getContent();
      final long newSize = size + content.length;
      checkArgument(
          newSize <= totalSize,
          "Chunk size %s + current size %s exceeds total size %s",
          content.length,
          size,
          totalSize);

      checksum.update(content);

      return new FileChecksum(checksum, newSize, totalSize);
    }
  }
}
