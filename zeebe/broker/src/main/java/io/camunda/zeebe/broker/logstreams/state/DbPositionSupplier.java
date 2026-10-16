/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.state;

import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointState;
import io.camunda.zeebe.broker.exporter.stream.ExportersState;
import io.camunda.zeebe.db.ZeebeDb;
import org.jspecify.annotations.Nullable;

public final class DbPositionSupplier implements StatePositionSupplier {

  private final boolean continuousBackup;
  private final ExportersState exporterState;
  private final DbCheckpointState checkpointState;
  private final @Nullable DbLogArchiveState logArchiveState;

  public DbPositionSupplier(final ZeebeDb zeebeDb, final boolean continuousBackup) {
    this(zeebeDb, continuousBackup, false);
  }

  public DbPositionSupplier(
      final ZeebeDb zeebeDb, final boolean continuousBackup, final boolean logArchiveEnabled) {
    this.continuousBackup = continuousBackup;
    final var context = zeebeDb.createContext();
    exporterState = new ExportersState(zeebeDb, context);
    checkpointState = new DbCheckpointState(zeebeDb, context);
    logArchiveState = logArchiveEnabled ? new DbLogArchiveState(zeebeDb, context) : null;
  }

  @Override
  public long getLowestExportedPosition() {
    if (exporterState.hasExporters()) {
      return exporterState.getLowestPosition();
    } else {
      return Long.MAX_VALUE;
    }
  }

  @Override
  public long getHighestExportedPosition() {
    return exporterState.getHighestPosition();
  }

  /**
   * The highest position that compaction may remove: the minimum of the latest backup position
   * (with continuous backups) and the archived position F (with the log archive). Either one is
   * {@link Long#MAX_VALUE} when its feature is disabled, so that it does not control compaction. F
   * is -1 until the first application entry is archived, which keeps the previous snapshot's
   * compaction bound.
   */
  @Override
  public long getHighestBackupPosition() {
    final long backupPosition =
        continuousBackup ? checkpointState.getLatestBackupPosition() : Long.MAX_VALUE;
    final long archivedPosition =
        logArchiveState != null ? logArchiveState.meta().getArchivedPosition() : Long.MAX_VALUE;
    return Math.min(backupPosition, archivedPosition);
  }
}
