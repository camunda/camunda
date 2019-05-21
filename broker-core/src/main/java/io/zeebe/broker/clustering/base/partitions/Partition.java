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
package io.zeebe.broker.clustering.base.partitions;

import io.atomix.cluster.messaging.ClusterEventService;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.engine.EngineService;
import io.zeebe.broker.engine.impl.StateReplication;
import io.zeebe.broker.exporter.stream.ExportersState;
import io.zeebe.broker.logstreams.restore.BrokerRestoreServer;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.db.ZeebeDb;
import io.zeebe.distributedlog.StorageConfiguration;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.StateStorageFactory;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.NoneSnapshotReplication;
import io.zeebe.logstreams.state.SnapshotReplication;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.slf4j.Logger;

/** Service representing a partition. */
public class Partition implements Service<Partition> {
  private static final String PARTITION_NAME_FORMAT = "raft-atomix-partition-%d";
  public static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  public static String getPartitionName(final int partitionId) {
    return String.format(PARTITION_NAME_FORMAT, partitionId);
  }

  private final Injector<LogStream> logStreamInjector = new Injector<>();

  private final ClusterEventService clusterEventService;
  private final int partitionId;
  private final RaftState state;
  private final StorageConfiguration configuration;
  private final BrokerCfg brokerCfg;
  private final BrokerRestoreServer restoreServer;

  private StateSnapshotController snapshotController;
  private SnapshotReplication stateReplication;
  private LogStream logStream;
  private ZeebeDb zeebeDb;

  public Partition(
      final StorageConfiguration configuration,
      BrokerCfg brokerCfg,
      ClusterEventService clusterEventService,
      int partitionId,
      RaftState state,
      BrokerRestoreServer restoreServer) {
    this.configuration = configuration;
    this.brokerCfg = brokerCfg;
    this.clusterEventService = clusterEventService;
    this.partitionId = partitionId;
    this.state = state;
    this.restoreServer = restoreServer;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    final CompletableActorFuture<Void> startedFuture = new CompletableActorFuture<>();
    logStream = logStreamInjector.getValue();

    logStream = logStreamInjector.getValue();
    snapshotController = createSnapshotController();

    if (state == RaftState.FOLLOWER) {
      logStream.setExporterPositionSupplier(this::getLowestReplicatedExportedPosition);
      snapshotController.consumeReplicatedSnapshots(logStream::delete);
    } else {
      try {
        snapshotController.recover();
        zeebeDb = snapshotController.openDb();
      } catch (Exception e) {
        throw new IllegalStateException(
            String.format(
                "Unexpected error occurred while recovering snapshot controller during leader partition install for partition %d",
                partitionId),
            e);
      }
    }

    startRestoreServer(startedFuture);
    startContext.async(startedFuture, true);
  }

  private void startRestoreServer(CompletableActorFuture<Void> startedFuture) {
    restoreServer
        .start(logStream, snapshotController)
        .whenComplete(
            (nothing, error) -> {
              if (error != null) {
                startedFuture.completeExceptionally(error);
              } else {
                startedFuture.complete(null);
              }
            });
  }

  private long getLowestReplicatedExportedPosition() {
    try {
      if (snapshotController.getValidSnapshotsCount() > 0) {
        snapshotController.recover();
        final ZeebeDb zeebeDb = snapshotController.openDb();
        final ExportersState exporterState = new ExportersState(zeebeDb, zeebeDb.createContext());

        final long lowestPosition = exporterState.getLowestPosition();

        LOG.debug(
            "The lowest exported position at follower {} is {}.",
            brokerCfg.getCluster().getNodeId(),
            lowestPosition);
        return lowestPosition;
      } else {
        LOG.info(
            "Follower {} has no exporter snapshot so it can't delete data.",
            brokerCfg.getCluster().getNodeId());
      }
    } catch (Exception e) {
      LOG.error(
          "Unexpected error occurred while obtaining the lowest exported position at a follower.",
          e);
    } finally {
      try {
        snapshotController.close();
      } catch (Exception e) {
        LOG.error("Unexpected error occurred while closing the DB.", e);
      }
    }

    return -1;
  }

  public StorageConfiguration getConfiguration() {
    return configuration;
  }

  private StateSnapshotController createSnapshotController() {
    final String streamProcessorName = EngineService.PROCESSOR_NAME;

    final StateStorageFactory storageFactory =
        new StateStorageFactory(configuration.getStatesDirectory());
    final StateStorage stateStorage = storageFactory.create(partitionId, streamProcessorName);

    stateReplication =
        shouldReplicateSnapshots()
            ? new StateReplication(clusterEventService, partitionId, streamProcessorName)
            : new NoneSnapshotReplication();

    return new StateSnapshotController(
        DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
        stateStorage,
        stateReplication,
        brokerCfg.getData().getMaxSnapshots());
  }

  private boolean shouldReplicateSnapshots() {
    return brokerCfg.getCluster().getReplicationFactor() > 1;
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stateReplication.close();
    restoreServer.close();

    try {
      snapshotController.close();
    } catch (Exception e) {
      LOG.error(
          "Unexpected error occurred while closing the state snapshot controller for partition {}.",
          partitionId,
          e);
    }
  }

  @Override
  public Partition get() {
    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public StateSnapshotController getSnapshotController() {
    return snapshotController;
  }

  public RaftState getState() {
    return state;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public Injector<LogStream> getLogStreamInjector() {
    return logStreamInjector;
  }

  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }
}
