/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots;

import io.camunda.zeebe.scheduler.future.ActorFuture;

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
   * Applies the next {@link SnapshotChunk} to the snapshot. Based on the implementation the chunk
   * can be validated before applied to the snapshot. In case of failure, the future will be
   * completed with a SnapshotWriteException.
   *
   * @param chunk the {@link SnapshotChunk} which should be applied
   */
  ActorFuture<Void> apply(SnapshotChunk chunk);
}
