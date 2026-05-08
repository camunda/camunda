/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.protocol.impl.record.value.backupmetadata.BackupMetadataRecord;
import io.camunda.zeebe.protocol.record.intent.BackupMetadataIntent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.function.Consumer;

/**
 * Minimal facade for the system partition that the backup-scheduler workers use to submit
 * BackupMetadata commands and to read the persisted backup-metadata snapshot.
 *
 * <p>Defined here (in the dynamic-config module) so the {@code zeebe-backup} module — which does
 * not depend on {@code zeebe-system-partition} — can hold a reference. The system-partition facade
 * implements this interface alongside {@link ClusterConfigCommandSubmitter}.
 */
public interface SystemPartitionBackupCommandSubmitter {

  /** Submit a {@link BackupMetadataIntent} command. Same semantics as {@code submitCommand}. */
  ActorFuture<BackupMetadataRecord> submitBackupCommand(
      BackupMetadataIntent intent, BackupMetadataRecord record);

  /**
   * Iterate every persisted backup-metadata row through the supplied consumer. The future completes
   * when iteration finishes.
   */
  ActorFuture<Void> queryBackupMetadata(Consumer<BackupMetadataRecord> consumer);
}
