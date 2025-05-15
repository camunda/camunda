/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot;

import io.camunda.zeebe.snapshots.SnapshotChunk;
import java.util.Arrays;
import java.util.Objects;

public record SnapshotChunkRecord(
    String snapshotId,
    int totalCount,
    String chunkName,
    long checksum,
    byte[] content,
    long fileBlockPosition,
    long totalFileSize)
    implements SnapshotChunk {

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

  // Overridden otherwise array equals is reference equality for content
  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SnapshotChunkRecord that = (SnapshotChunkRecord) o;
    return checksum == that.checksum
        && totalCount == that.totalCount
        && totalFileSize == that.totalFileSize
        && fileBlockPosition == that.fileBlockPosition
        && Objects.deepEquals(content, that.content)
        && Objects.equals(chunkName, that.chunkName)
        && Objects.equals(snapshotId, that.snapshotId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        snapshotId,
        totalCount,
        chunkName,
        checksum,
        Arrays.hashCode(content),
        fileBlockPosition,
        totalFileSize);
  }
}
