/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.transfer;

import io.camunda.zeebe.scheduler.future.ActorFuture;

public interface SnapshotSenderService extends SnapshotTransferService {

  /**
   * Delete the underlying snapshot for bootstrap and terminate all pending transfers
   *
   * @param partitionId the partition for which to delete snapshots
   */
  ActorFuture<Void> deleteSnapshots(int partitionId);
}
