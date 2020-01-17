/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.exporter.stream.ExportersState;
import io.zeebe.db.ZeebeDb;
import org.slf4j.Logger;

public final class ExporterClearStateService {

  private static final Logger LOG = Loggers.EXPORTER_LOGGER;

  private final ZeebeDb zeebeDb;

  public ExporterClearStateService(final ZeebeDb zeebeDb) {
    this.zeebeDb = zeebeDb;
  }

  public void start() {
    // We need to remove the exporter positions from the state in case that one of the exporters is
    // configured later again. The processor would try to continue from the previous position which
    // may not
    // exist anymore in the logstream.

    try {
      final ExportersState state = new ExportersState(zeebeDb, zeebeDb.createContext());

      state.visitPositions(
          (exporterId, position) -> {
            state.removePosition(exporterId);

            LOG.info(
                "The exporter '{}' is not configured anymore. Its position is removed from the state.",
                exporterId);
          });
    } catch (final Exception e) {
      LOG.error("Failed to remove exporters from state", e);
    }
  }
}
