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

  private String snapshotId;
  private Integer totalCount;
  private Long checksum;
  private Long snapshotChecksum;
  private byte[] contents;

  private SnapshotChunkWrapper(final SnapshotChunk wrappedChunk) {
    this.wrappedChunk = wrappedChunk;
  }

  public static SnapshotChunk withSnapshotId(
      final SnapshotChunk wrappedChunk, final String snapshotId) {
    final var wrapper = new SnapshotChunkWrapper(wrappedChunk);
    wrapper.snapshotId = snapshotId;

    return wrapper;
  }

  public static SnapshotChunk withTotalCount(
      final SnapshotChunk wrappedChunk, final Integer totalCount) {
    final var wrapper = new SnapshotChunkWrapper(wrappedChunk);
    wrapper.totalCount = totalCount;

    return wrapper;
  }

  public static SnapshotChunk withChecksum(final SnapshotChunk wrappedChunk, final Long checksum) {
    final var wrapper = new SnapshotChunkWrapper(wrappedChunk);
    wrapper.checksum = checksum;

    return wrapper;
  }

  public static SnapshotChunk withSnapshotChecksum(
      final SnapshotChunk wrappedChunk, final Long snapshotChecksum) {
    final var wrapper = new SnapshotChunkWrapper(wrappedChunk);
    wrapper.snapshotChecksum = snapshotChecksum;

    return wrapper;
  }

  public static SnapshotChunk withContents(
      final SnapshotChunk wrappedChunk, final byte[] contents) {
    final var wrapper = new SnapshotChunkWrapper(wrappedChunk);
    wrapper.contents = contents;

    return wrapper;
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
    if (contents == null) {
      return wrappedChunk.getContent();
    }
    return contents;
  }

  @Override
  public long getSnapshotChecksum() {
    if (snapshotChecksum == null) {
      return wrappedChunk.getSnapshotChecksum();
    }
    return snapshotChecksum;
  }
}
