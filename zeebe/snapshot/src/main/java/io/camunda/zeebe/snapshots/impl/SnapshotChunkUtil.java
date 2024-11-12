/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.snapshots.SnapshotChunk;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;

final class SnapshotChunkUtil {

  private SnapshotChunkUtil() {}

  static long createChecksum(final byte[] content) {
    final Checksum checksum = newChecksum();
    checksum.update(content);
    return checksum.getValue();
  }

  static Checksum newChecksum() {
    return new CRC32C();
  }

  static SnapshotChunk createSnapshotChunkFromFileChunk(
      final String snapshotId,
      final int totalCount,
      final String fileName,
      final byte[] fileData,
      final long fileBlockPosition,
      final long totalFileSize) {

    final long checksum = createChecksum(fileData);
    return new SnapshotChunkImpl(
        snapshotId, totalCount, fileName, checksum, fileData, fileBlockPosition, totalFileSize);
  }

  private static final class SnapshotChunkImpl implements SnapshotChunk {
    private final String snapshotId;
    private final int totalCount;
    private final String chunkName;
    private final byte[] content;
    private final long checksum;
    private final long fileBlockPosition;
    private final long totalFileSize;

    SnapshotChunkImpl(
        final String snapshotId,
        final int totalCount,
        final String chunkName,
        final long checksum,
        final byte[] content,
        final long fileBlockPosition,
        final long totalFileSize) {
      this.snapshotId = snapshotId;
      this.totalCount = totalCount;
      this.chunkName = chunkName;
      this.checksum = checksum;
      this.content = content;
      this.fileBlockPosition = fileBlockPosition;
      this.totalFileSize = totalFileSize;
    }

    @Override
    public String getSnapshotId() {
      return snapshotId;
    }

    @Override
    public int getTotalCount() {
      return totalCount;
    }

    @Override
    public String getChunkName() {
      return chunkName;
    }

    @Override
    public long getChecksum() {
      return checksum;
    }

    @Override
    public byte[] getContent() {
      return content;
    }

    @Override
    public long getFileBlockPosition() {
      return fileBlockPosition;
    }

    @Override
    public long getTotalFileSize() {
      return totalFileSize;
    }
  }
}
