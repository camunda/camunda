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

public final class StatePositionSupplier {
  private StatePositionSupplier() {}

  public static long getLowestExportedPosition(final ZeebeDb zeebeDb) {
    final var exporterState = new ExportersState(zeebeDb, zeebeDb.createContext());
    if (exporterState.hasExporters()) {
      return exporterState.getLowestPosition();
    } else {
      return Long.MAX_VALUE;
    }
  }

  public static long getHighestExportedPosition(final ZeebeDb zeebeDb) {
    return new ExportersState(zeebeDb, zeebeDb.createContext()).getHighestPosition();
  }

  public static long getHighestBackupPosition(final ZeebeDb zeebeDb) {
    final var checkpointState = new DbCheckpointState(zeebeDb, zeebeDb.createContext());
    return checkpointState.getLatestBackupPosition();
  }
}
