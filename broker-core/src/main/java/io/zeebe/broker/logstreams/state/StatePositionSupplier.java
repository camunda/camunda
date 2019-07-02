/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.logstreams.state;

import io.zeebe.broker.exporter.stream.ExportersState;
import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.util.collection.Tuple;
import org.slf4j.Logger;

public class StatePositionSupplier {

  private final SnapshotController snapshotController;
  private final int partitionId;
  private final String brokerId;
  private final Logger logger;

  public StatePositionSupplier(
      SnapshotController snapshotController, int partitionId, String brokerId, Logger log) {
    this.snapshotController = snapshotController;
    this.partitionId = partitionId;
    this.brokerId = brokerId;
    this.logger = log;
  }

  public Tuple<Long, Long> getLatestPositions() {
    long processedPosition = -1;
    long exportedPosition = -1;
    try {
      if (snapshotController.getValidSnapshotsCount() > 0) {
        snapshotController.recover();
        final ZeebeDb zeebeDb = snapshotController.openDb();
        processedPosition = getLastProcessedPosition(zeebeDb);
        exportedPosition = getMinimumExportedPosition(zeebeDb);
      }
    } catch (Exception e) {
      logger.error(
          "Unexpected error occurred while obtaining the processed and exported position at broker {} for partition {}.",
          brokerId,
          partitionId,
          e);
    } finally {
      try {
        snapshotController.close();
      } catch (Exception e) {
        logger.error("Unexpected error occurred while closing the DB.", e);
      }
    }

    return new Tuple(exportedPosition, processedPosition);
  }

  public long getMinimumExportedPosition() {
    try {
      if (snapshotController.getValidSnapshotsCount() > 0) {
        snapshotController.recover();
        final ZeebeDb zeebeDb = snapshotController.openDb();
        return getMinimumExportedPosition(zeebeDb);
      }
    } catch (Exception e) {
      logger.error(
          "Unexpected error occurred while obtaining the minimum exported position at broker {} for partition {}.",
          brokerId,
          partitionId,
          e);
    } finally {
      try {
        snapshotController.close();
      } catch (Exception e) {
        logger.error("Unexpected error occurred while closing the DB.", e);
      }
    }
    return -1;
  }

  private long getMinimumExportedPosition(ZeebeDb zeebeDb) {
    final ExportersState exporterState = new ExportersState(zeebeDb, zeebeDb.createContext());

    if (exporterState.hasExporters()) {
      final long lowestPosition = exporterState.getLowestPosition();

      logger.debug(
          "The lowest exported position for partition {} at broker {} is {}.",
          partitionId,
          brokerId,
          lowestPosition);

      return lowestPosition;
    } else {
      logger.debug(
          "No exporters present in snapshot for partition {} at broker {}.", partitionId, brokerId);
      return Long.MAX_VALUE;
    }
  }

  private long getLastProcessedPosition(ZeebeDb zeebeDb) {
    final ZeebeState processorState = new ZeebeState(partitionId, zeebeDb, zeebeDb.createContext());
    final long lowestPosition = processorState.getLastSuccessfulProcessedRecordPosition();

    logger.debug(
        "The last processed position for partition {} at broker {} is {}.",
        partitionId,
        brokerId,
        lowestPosition);

    return lowestPosition;
  }
}
