/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.transfer;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;

/**
 * Receive a complete snapshot from a {@link SnapshotTransferService} by repeatedly asking for the
 * next chunk until all chunks are received. No retry is done on the futures, if you want support
 * for retry, wrap {@param service} with retries.
 *
 * <p>Snapshots are received in the {@param snapshotStore}.
 */
public interface SnapshotTransfer {
  ActorFuture<PersistedSnapshot> getLatestSnapshot(final int partitionId);
}
