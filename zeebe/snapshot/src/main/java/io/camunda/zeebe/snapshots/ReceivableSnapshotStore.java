/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import io.camunda.zeebe.scheduler.future.ActorFuture;

public interface ReceivableSnapshotStore extends PersistedSnapshotStore {

  /**
   * Starts a new received volatile snapshot which can be persisted later.
   *
   * @param snapshotId the snapshot id which is defined as {@code
   *     index-term-timestamp-processedposition-exportedposition}
   * @return the new volatile received snapshot
   */
  ActorFuture<? extends ReceivedSnapshot> newReceivedSnapshot(String snapshotId);
}
