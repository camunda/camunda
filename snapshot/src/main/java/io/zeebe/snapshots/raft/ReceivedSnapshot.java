/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.snapshots.raft;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A received volatile snapshot, which consist of several {@link SnapshotChunk}'s. It can be
 * persisted after all chunks have been received and consumed.
 */
public interface ReceivedSnapshot extends PersistableSnapshot {

  /**
   * The index of the current receiving snapshot.
   *
   * @return the snapshot's index
   */
  long index();

  /**
   * Returns true if the chunk identified by the given ID has already been applied to the snapshot.
   *
   * @param chunkId the chunk ID to check for
   * @return true if already applied, false otherwise
   */
  boolean containsChunk(ByteBuffer chunkId);

  /**
   * Returns true if the chunk identified by chunkId is the expected next chunk, false otherwise.
   *
   * @param chunkId the ID of the new chunk
   * @return true if is expected, false otherwise
   */
  boolean isExpectedChunk(ByteBuffer chunkId);

  /**
   * Sets that the next expected chunk ID is the one with the given {@code nextChunkId}.
   *
   * @param nextChunkId the next expected chunk ID
   */
  void setNextExpected(ByteBuffer nextChunkId);

  /**
   * Applies the next {@link SnapshotChunk} to the snapshot. Based on the implementation the chunk
   * can be validated before applied to the snapshot.
   *
   * @param chunk the {@link SnapshotChunk} which should be applied
   * @return returns true if everything succeeds, false otherwise
   */
  boolean apply(SnapshotChunk chunk) throws IOException;
}
