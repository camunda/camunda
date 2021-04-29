/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.impl;

import io.zeebe.snapshots.SnapshotChunk;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

  static SnapshotChunk createSnapshotChunkFromFile(
      final Path chunkFile,
      final String snapshotId,
      final int totalCount,
      final long snapshotChecksum)
      throws IOException {
    final byte[] content = Files.readAllBytes(chunkFile);
    final long checksum = createChecksum(content);
    return new SnapshotChunkImpl(
        snapshotId,
        totalCount,
        chunkFile.getFileName().toString(),
        checksum,
        content,
        snapshotChecksum);
  }

  private static final class SnapshotChunkImpl implements SnapshotChunk {
    private final String snapshotId;
    private final int totalCount;
    private final String chunkName;
    private final byte[] content;
    private final long snapshotChecksum;
    private final long checksum;

    SnapshotChunkImpl(
        final String snapshotId,
        final int totalCount,
        final String chunkName,
        final long checksum,
        final byte[] content,
        final long snapshotChecksum) {
      this.snapshotId = snapshotId;
      this.totalCount = totalCount;
      this.chunkName = chunkName;
      this.checksum = checksum;
      this.content = content;
      this.snapshotChecksum = snapshotChecksum;
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
    public long getSnapshotChecksum() {
      return snapshotChecksum;
    }
  }
}
