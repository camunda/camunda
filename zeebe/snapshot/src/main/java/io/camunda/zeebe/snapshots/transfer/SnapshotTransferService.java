/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.transfer;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import java.util.UUID;

public interface SnapshotTransferService extends AsyncClosable {

  /**
   * Initiate the transfer of the latest snapshot for a partition.
   *
   * @param partition the partition to get the snapshot from
   * @param lastProcessedPosition the minimum value of lastProcessedPosition that the snapshot must
   *     have
   * @param transferId an identifier to trace the transfer
   * @return the first {@link SnapshotChunk}. It must be used in subsequent calls to {@link
   *     SnapshotTransferService#getNextChunk}
   */
  ActorFuture<SnapshotChunk> getLatestSnapshot(
      int partition, long lastProcessedPosition, UUID transferId);

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

  interface TakeSnapshot {

    /**
     * Take a snapshot of a partition. The snapshot returned must have processed at least {@param
     * lastProcessedPosition}
     *
     * @param partition the partition to take the snapshot for
     * @param lastProcessedPosition the minimum value for lastProcessedPosition in the snapshot
     * @return
     */
    ActorFuture<PersistedSnapshot> takeSnapshot(
        int partition, long lastProcessedPosition, ConcurrencyControl control);
  }
  /**
   * Delete the underlying snapshot for bootstrap and terminate all pending transfers
   *
   * @param partitionId the partition for which to delete snapshots
   */
  ActorFuture<Void> deleteSnapshots(int partitionId);
}
