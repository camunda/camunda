/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.transfer;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import java.util.UUID;

public interface SnapshotTransferService {

  /**
   * Initiate the transfer of the latest snapshot for a partition.
   *
   * @param partition the partition to get the snapshot from
   * @return the first {@link SnapshotChunk}. It must be used in subsequent calls to {@link
   *     SnapshotTransferService#getNextChunk}
   */
  ActorFuture<SnapshotChunk> getLatestSnapshot(int partition, UUID transferId);

  /**
   * Get the next chunk for the snapshot.
   *
   * @param partition the partition to request the snapshot from
   * @param snapshotId the snapshotId of the snapshot being transferred
   * @param previousChunkName the chunkName of the previous chunk, so that the next one can be
   *     identified
   * @return null if there is no other chunk or the SnapshotChunk after {@param previousChunkName}
   */
  ActorFuture<SnapshotChunk> getNextChunk(
      int partition, String snapshotId, String previousChunkName, UUID transferId);
}
