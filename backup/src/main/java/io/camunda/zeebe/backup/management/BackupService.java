/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

/** Backup manager that takes and manages backup asynchronously */
public class BackupService extends Actor implements BackupManager {

  private final int nodeId;
  private final int partitionId;
  private final String actorName;

  private final int numberOfPartitions;

  public BackupService(final int nodeId, final int partitionId, final int numberOfPartitions) {
    this.nodeId = nodeId;
    this.partitionId = partitionId;
    this.numberOfPartitions = numberOfPartitions;
    actorName = buildActorName(nodeId, "SnapshotStore", partitionId);
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  public void takeBackup(final long checkpointId, final long checkpointPosition) {
    // Will be implemented later
  }

  @Override
  public ActorFuture<BackupStatus> getBackupStatus(final long checkpointId) {
    return CompletableActorFuture.completedExceptionally(
        new UnsupportedOperationException("Not implemented"));
  }

  @Override
  public ActorFuture<Void> deleteBackup(final long checkpointId) {
    return CompletableActorFuture.completedExceptionally(
        new UnsupportedOperationException("Not implemented"));
  }
}
