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
package io.zeebe.broker.logstreams.restore;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.engine.EngineService;
import io.zeebe.broker.exporter.stream.ExportersState;
import io.zeebe.db.ZeebeDb;
import io.zeebe.distributedlog.StorageConfiguration;
import io.zeebe.distributedlog.impl.LogstreamConfig;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreContext;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.StateStorageFactory;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import java.util.function.Supplier;

public class BrokerSnapshotRestoreContext implements SnapshotRestoreContext {

  private final String localMemberId;

  public BrokerSnapshotRestoreContext(String localMemberId) {
    this.localMemberId = localMemberId;
  }

  @Override
  public StateStorage getStateStorage(int partitionId) {
    final StorageConfiguration configuration =
        LogstreamConfig.getConfig(localMemberId, partitionId).join();
    return new StateStorageFactory(configuration.getStatesDirectory())
        .create(partitionId, EngineService.PROCESSOR_NAME, "-restore-log");
  }

  @Override
  public Supplier<Long> getExporterPositionSupplier(int partitionId) {
    return () -> getLowestReplicatedExportedPosition(partitionId);
  }

  @Override
  public Supplier<Long> getProcessorPositionSupplier(int partitionId) {
    return () -> getLatestProcessedPosition(partitionId);
  }

  private long getLowestReplicatedExportedPosition(int partitionId) {
    final StateStorage exporterStorage = getStateStorage(partitionId);
    final SnapshotController exporterSnapshotController =
        new StateSnapshotController(DefaultZeebeDbFactory.DEFAULT_DB_FACTORY, exporterStorage);

    try {
      if (exporterSnapshotController.getValidSnapshotsCount() > 0) {
        exporterSnapshotController.recover();
        final ZeebeDb zeebeDb = exporterSnapshotController.openDb();
        final ExportersState exporterState = new ExportersState(zeebeDb, zeebeDb.createContext());

        return exporterState.getLowestPosition();
      }
    } catch (Exception e) {

      Loggers.CLUSTERING_LOGGER.trace("Exception on opening snapshot db", e);
    } finally {
      try {
        exporterSnapshotController.close();
      } catch (Exception e) {

      }
    }
    return -1;
  }

  private long getLatestProcessedPosition(int partitionId) {
    final StateStorage stateStorage = getStateStorage(partitionId);
    final SnapshotController processorSnapshotController =
        new StateSnapshotController(DefaultZeebeDbFactory.DEFAULT_DB_FACTORY, stateStorage);

    try {
      if (processorSnapshotController.getValidSnapshotsCount() > 0) {
        processorSnapshotController.recover();
        final ZeebeDb zeebeDb = processorSnapshotController.openDb();
        final ZeebeState processorState =
            new ZeebeState(partitionId, zeebeDb, zeebeDb.createContext());

        return processorState.getLastSuccessfulProcessedRecordPosition();
      }
    } catch (Exception e) {

    } finally {
      try {
        processorSnapshotController.close();
      } catch (Exception e) {

      }
    }
    return -1;
  }
}
