/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots;

import java.util.Optional;

/** A persisted snapshot store than can create a new snapshot and persists it. */
public interface ConstructableSnapshotStore extends PersistedSnapshotStore {

  /**
   * Starts a new transient snapshot which can be persisted after the snapshot was taken.
   *
   * @param index the index to which the snapshot corresponds to
   * @param term the term to which the snapshots corresponds to
   * @param processedPosition the processed position in the snapshot
   * @param exportedPosition the exported position in the snapshot
   * @return an optional with a transient snapshot if new transient snapshot was taken successfully,
   *     otherwise return an empty optional
   */
  Optional<TransientSnapshot> newTransientSnapshot(
      long index, long term, long processedPosition, long exportedPosition);
}
