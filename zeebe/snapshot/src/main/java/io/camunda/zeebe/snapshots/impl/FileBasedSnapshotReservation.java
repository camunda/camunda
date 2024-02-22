/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.SnapshotReservation;

public class FileBasedSnapshotReservation implements SnapshotReservation {

  private final FileBasedSnapshot snapshot;

  public FileBasedSnapshotReservation(final FileBasedSnapshot snapshot) {
    this.snapshot = snapshot;
  }

  @Override
  public ActorFuture<Void> release() {
    return snapshot.removeReservation(this);
  }
}
