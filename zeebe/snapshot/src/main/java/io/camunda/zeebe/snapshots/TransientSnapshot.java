/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.nio.file.Path;
import java.util.function.Consumer;

/** A transient snapshot which can be persisted after taking a snapshot. */
public interface TransientSnapshot extends PersistableSnapshot {

  /**
   * Takes a snapshot on the given path. This can be persisted later via calling {@link
   * PersistableSnapshot#persist()}. Based on the implementation this could mean that this is
   * written before on a temporary folder and then moved to the valid snapshot directory.
   *
   * @param takeSnapshot the predicate which should take the snapshot and should return true on
   *     success
   * @return a future reflecting the result of the operation
   */
  ActorFuture<Void> take(Consumer<Path> takeSnapshot);

  /**
   * A snapshot is only valid if the accompanying logstream has events from processedPosition up to
   * the last followup event position. The last followUp event position is the position of an event
   * whose source position >= actual processed position in the state.
   *
   * @param followupEventPosition position of the followup event which must be in the logstream to
   *     ensure that the system can recover from the snapshot and the events in the logstream.
   * @return transient snapshot.
   */
  TransientSnapshot withLastFollowupEventPosition(long followupEventPosition);

  /** Sets the highest exported position that could be reflected in this snapshot. */
  TransientSnapshot withMaxExportedPosition(long maxExportedPosition);
}
