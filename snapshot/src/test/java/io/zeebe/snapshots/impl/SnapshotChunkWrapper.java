/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.impl;

import io.zeebe.snapshots.SnapshotChunk;

public final class SnapshotChunkWrapper implements SnapshotChunk {

  private final SnapshotChunk wrappedChunk;
  private final String snapshotId;
  private final Integer totalCount;
  private final Long checksum;
  private final Long snapshotChecksum;

  private SnapshotChunkWrapper(
      final SnapshotChunk wrappedChunk,
      final String snapshotId,
      final Integer totalCount,
      final Long checksum,
      final Long snapshotChecksum) {
    this.wrappedChunk = wrappedChunk;
    this.snapshotId = snapshotId;
    this.totalCount = totalCount;
    this.checksum = checksum;
    this.snapshotChecksum = snapshotChecksum;
  }

  public static SnapshotChunk withDifferentSnapshotId(
      final SnapshotChunk wrappedChunk, final String snapshotId) {
    return new SnapshotChunkWrapper(wrappedChunk, snapshotId, null, null, null);
  }

  public static SnapshotChunk withDifferentTotalCount(
      final SnapshotChunk wrappedChunk, final Integer totalCount) {
    return new SnapshotChunkWrapper(wrappedChunk, null, totalCount, null, null);
  }

  public static SnapshotChunk withDifferentChecksum(
      final SnapshotChunk wrappedChunk, final Long checksum) {
    return new SnapshotChunkWrapper(wrappedChunk, null, null, checksum, null);
  }

  public static SnapshotChunk withDifferentSnapshotChecksum(
      final SnapshotChunk wrappedChunk, final Long snapshotChecksum) {
    return new SnapshotChunkWrapper(wrappedChunk, null, null, null, snapshotChecksum);
  }

  @Override
  public String getSnapshotId() {
    if (snapshotId == null) {
      return wrappedChunk.getSnapshotId();
    }
    return snapshotId;
  }

  @Override
  public int getTotalCount() {
    if (totalCount == null) {
      return wrappedChunk.getTotalCount();
    }
    return totalCount;
  }

  @Override
  public String getChunkName() {
    return wrappedChunk.getChunkName();
  }

  @Override
  public long getChecksum() {
    if (checksum == null) {
      return wrappedChunk.getChecksum();
    }
    return checksum;
  }

  @Override
  public byte[] getContent() {
    return wrappedChunk.getContent();
  }

  @Override
  public long getSnapshotChecksum() {
    if (snapshotChecksum == null) {
      return wrappedChunk.getSnapshotChecksum();
    }
    return snapshotChecksum;
  }
}
