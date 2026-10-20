/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;

/** Takes a snapshot on request, regardless of whether enough has changed since the last one. */
@FunctionalInterface
public interface SnapshotTrigger {

  /**
   * @return future completed with the new snapshot, or with null if none was taken, e.g. because a
   *     snapshot is already being taken
   */
  ActorFuture<PersistedSnapshot> forceSnapshot();
}
