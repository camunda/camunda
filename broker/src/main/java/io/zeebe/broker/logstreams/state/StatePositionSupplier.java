/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.state;

import io.zeebe.broker.exporter.stream.ExportersState;
import io.zeebe.db.ZeebeDb;

public class StatePositionSupplier {
  public static long getHighestExportedPosition(final ZeebeDb zeebeDb) {
    final ExportersState exporterState = new ExportersState(zeebeDb, zeebeDb.createContext());
    if (exporterState.hasExporters()) {
      return exporterState.getLowestPosition();
    } else {
      return Long.MAX_VALUE;
    }
  }
}
