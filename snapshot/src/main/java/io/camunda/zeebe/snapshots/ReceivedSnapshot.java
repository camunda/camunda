/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots;

import io.zeebe.util.sched.future.ActorFuture;
import java.io.IOException;

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
   * can be validated before applied to the snapshot.
   *
   * @param chunk the {@link SnapshotChunk} which should be applied
   * @return returns true if everything succeeds, false otherwise
   */
  ActorFuture<Boolean> apply(SnapshotChunk chunk) throws IOException;
}
