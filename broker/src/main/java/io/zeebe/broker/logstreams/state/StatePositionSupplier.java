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
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.ZeebeState;
import java.nio.file.Path;
import org.slf4j.Logger;

public class StatePositionSupplier {

  private final int partitionId;
  private final Logger logger;

  public StatePositionSupplier(final int partitionId, final Logger log) {
    this.partitionId = partitionId;
    this.logger = log;
  }

  public long getLowestPosition(final Path directory) {
    long processedPosition = -1;
    long exportedPosition = -1;

    try (final var db = open(directory)) {
      processedPosition = getLastProcessedPosition(directory, db);
      exportedPosition = getMinimumExportedPosition(directory, db);
    } catch (final Exception e) {
      logger.error(
          "Unexpected error occurred while obtaining the processed and exported position from the snapshot {}",
          directory,
          e);
    }

    return Math.min(processedPosition, exportedPosition);
  }

  public long getLastProcessedPosition(final Path directory) {
    long processedPosition = -1;

    try (final var db = open(directory)) {
      processedPosition = getLastProcessedPosition(directory, db);
    } catch (final Exception e) {
      logger.error(
          "Unexpected error occurred while obtaining the processed position from the given snapshot {}",
          directory,
          e);
    }

    return processedPosition;
  }

  private long getMinimumExportedPosition(
      final Path directory, final ZeebeDb<ZbColumnFamilies> zeebeDb) {
    final ExportersState exporterState = new ExportersState(zeebeDb, zeebeDb.createContext());

    if (exporterState.hasExporters()) {
      final long lowestPosition = exporterState.getLowestPosition();
      logger.debug("The lowest exported position for snapshot {} is {}", directory, lowestPosition);

      return lowestPosition;
    } else {
      logger.debug("No exporters present in snapshot {}", directory);
      return Long.MAX_VALUE;
    }
  }

  private long getLastProcessedPosition(
      final Path directory, final ZeebeDb<ZbColumnFamilies> zeebeDb) {
    final ZeebeState processorState = new ZeebeState(partitionId, zeebeDb, zeebeDb.createContext());
    final long lowestPosition = processorState.getLastSuccessfulProcessedRecordPosition();
    logger.debug("The last processed position for snapshot {} is {}", directory, lowestPosition);
    return lowestPosition;
  }

  private ZeebeDb<ZbColumnFamilies> open(final Path directory) {
    return DefaultZeebeDbFactory.defaultFactory(ZbColumnFamilies.class)
        .createDb(directory.toFile());
  }
}
