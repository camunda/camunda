/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.state;

import io.camunda.zeebe.backup.processing.state.DbCheckpointState;
import io.camunda.zeebe.broker.exporter.stream.ExportersState;
import io.camunda.zeebe.db.ZeebeDb;

public final class DbPositionSupplier implements StatePositionSupplier {

  private final boolean continuousBackup;
  private final ExportersState exporterState;
  private final DbCheckpointState checkpointState;

  public DbPositionSupplier(final ZeebeDb zeebeDb, final boolean continuousBackup) {
    this.continuousBackup = continuousBackup;
    final var context = zeebeDb.createContext();
    exporterState = new ExportersState(zeebeDb, context);
    checkpointState = new DbCheckpointState(zeebeDb, context);
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

  @Override
  public long getHighestBackupPosition() {
    if (continuousBackup) {
      return checkpointState.getLatestBackupPosition();
    } else {
      // When continuous backups are disabled, act as if everything is backed up. Ensures
      // that backups do not control compaction.
      return Long.MAX_VALUE;
    }
  }
}
