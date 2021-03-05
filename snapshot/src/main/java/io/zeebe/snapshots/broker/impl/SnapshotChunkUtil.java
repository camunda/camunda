/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.broker.impl;

import io.zeebe.snapshots.raft.SnapshotChunk;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.CRC32;

final class SnapshotChunkUtil {

  private SnapshotChunkUtil() {}

  static long createChecksum(final byte[] content) {
    final CRC32 crc32 = new CRC32();
    crc32.update(content);
    return crc32.getValue();
  }

  static SnapshotChunk createSnapshotChunkFromFile(
      final File snapshotChunkFile,
      final String snapshotId,
      final int totalCount,
      final long snapshotChecksum)
      throws IOException {
    final byte[] content;
    content = Files.readAllBytes(snapshotChunkFile.toPath());
    final long checksum = createChecksum(content);
    return new SnapshotChunkImpl(
        snapshotId, totalCount, snapshotChunkFile.getName(), checksum, content, snapshotChecksum);
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
