/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots;

import io.zeebe.util.sched.future.ActorFuture;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** A transient snapshot which can be persisted after taking a snapshot. */
public interface TransientSnapshot extends PersistableSnapshot {

  /**
   * Takes a snapshot on the given path. This can be persisted later via calling {@link
   * PersistableSnapshot#persist()}. Based on the implementation this could mean that this is writen
   * before on a temporary folder and then moved to the valid snapshot directory.
   *
   * @param takeSnapshot the predicate which should take the snapshot and should return true on
   *     success
   * @return true on success, false otherwise
   */
  ActorFuture<Boolean> take(Predicate<Path> takeSnapshot);

  /**
   * Execute an operation after {@link TransientSnapshot#take(Predicate)} is completed.
   *
   * @param runnable the operation that should be executed after the transient snapshot is taken
   */
  void onSnapshotTaken(BiConsumer<Boolean, Throwable> runnable);
}
