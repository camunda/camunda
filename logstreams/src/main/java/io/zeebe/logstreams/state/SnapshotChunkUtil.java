/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.state;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.CRC32;

public class SnapshotChunkUtil {

  public static long createChecksum(byte[] content) {
    final CRC32 crc32 = new CRC32();
    crc32.update(content);
    return crc32.getValue();
  }

  public static SnapshotChunk createSnapshotChunkFromFile(
      File snapshotChunkFile, long snapshotPosition, int totalCount) throws IOException {
    final byte[] content;
    content = Files.readAllBytes(snapshotChunkFile.toPath());
    final long checksum = createChecksum(content);
    return new SnapshotChunkImpl(
        snapshotPosition, totalCount, snapshotChunkFile.getName(), checksum, content);
  }

  private static final class SnapshotChunkImpl implements SnapshotChunk {
    private final long snapshotPosition;
    private final int totalCount;
    private final String chunkName;
    private final byte[] content;
    private final long checksum;

    SnapshotChunkImpl(
        long snapshotPosition, int totalCount, String chunkName, long checksum, byte[] content) {
      this.snapshotPosition = snapshotPosition;
      this.totalCount = totalCount;
      this.chunkName = chunkName;
      this.checksum = checksum;
      this.content = content;
    }

    @Override
    public long getSnapshotPosition() {
      return snapshotPosition;
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
  }
}
