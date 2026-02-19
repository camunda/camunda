/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import java.util.OptionalLong;
import java.util.Set;

interface InProgressBackup {

  OptionalLong getFirstLogPosition();

  BackupDescriptor backupDescriptor();

  BackupIdentifier id();

  ActorFuture<Set<PersistedSnapshot>> findValidSnapshot();

  ActorFuture<Void> reserveSnapshot();

  ActorFuture<Void> findSnapshotFiles();

  ActorFuture<Void> findSegmentFiles();

  Backup createBackup();

  void close();
}
