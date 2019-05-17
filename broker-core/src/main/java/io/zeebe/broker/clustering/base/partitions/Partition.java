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

import static io.zeebe.broker.exporter.ExporterManagerService.EXPORTER_PROCESSOR_ID;
import static io.zeebe.broker.exporter.ExporterManagerService.PROCESSOR_NAME;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.engine.EngineService;
import io.zeebe.broker.exporter.stream.ExporterColumnFamilies;
import io.zeebe.broker.exporter.stream.ExportersState;
import io.zeebe.broker.logstreams.state.DefaultOnDemandSnapshotReplication;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.StateStorageFactory;
import io.zeebe.engine.state.replication.StateReplication;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.logstreams.state.NoneSnapshotReplication;
import io.zeebe.logstreams.state.SnapshotReplication;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;

/** Service representing a partition. */
public class Partition implements Service<Partition> {
  public static final String PARTITION_NAME_FORMAT = "raft-atomix-partition-%d";
  public static final Logger LOG = Loggers.CLUSTERING_LOGGER;
  private final ClusterEventService eventService;
  private final BrokerCfg brokerCfg;
  private final ClusterCommunicationService communicationService;
  private SnapshotReplication processorStateReplication;
  private SnapshotReplication exporterStateReplication;
  private DefaultOnDemandSnapshotReplication processorSnapshotRequestServer;
  private DefaultOnDemandSnapshotReplication exporterSnapshotRequestServer;
  private ExecutorService executor;

  public static String getPartitionName(final int partitionId) {
    return String.format(PARTITION_NAME_FORMAT, partitionId);
  }

  private final Injector<LogStream> logStreamInjector = new Injector<>();
  private final Injector<StateStorageFactory> stateStorageFactoryInjector = new Injector<>();
  private final int partitionId;
  private final RaftState state;

  private LogStream logStream;
  private SnapshotController exporterSnapshotController;
  private StateSnapshotController processorSnapshotController;

  public Partition(
      BrokerCfg brokerCfg,
      ClusterEventService eventService,
      ClusterCommunicationService communicationService,
      final int partitionId,
      final RaftState state) {
    this.brokerCfg = brokerCfg;
    this.partitionId = partitionId;
    this.state = state;
    this.eventService = eventService;
    this.communicationService = communicationService;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    final boolean noReplication = brokerCfg.getCluster().getReplicationFactor() == 1;

    logStream = logStreamInjector.getValue();
    final StateStorageFactory stateStorageFactory = stateStorageFactoryInjector.getValue();

    final String exporterProcessorName = PROCESSOR_NAME;
    final StateStorage exporterStateStorage =
        stateStorageFactory.create(EXPORTER_PROCESSOR_ID, exporterProcessorName);
    exporterStateReplication =
        noReplication
            ? new NoneSnapshotReplication()
            : new StateReplication(eventService, partitionId, exporterProcessorName);
    exporterSnapshotController =
        new StateSnapshotController(
            DefaultZeebeDbFactory.defaultFactory(ExporterColumnFamilies.class),
            exporterStateStorage,
            exporterStateReplication,
            brokerCfg.getData().getMaxSnapshots());

    final String streamProcessorName = EngineService.PROCESSOR_NAME;
    final StateStorage stateStorage = stateStorageFactory.create(partitionId, streamProcessorName);
    processorStateReplication =
        noReplication
            ? new NoneSnapshotReplication()
            : new StateReplication(eventService, partitionId, streamProcessorName);

    processorSnapshotController =
        new StateSnapshotController(
            DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
            stateStorage,
            processorStateReplication,
            brokerCfg.getData().getMaxSnapshots());

    if (state == RaftState.FOLLOWER) {
      logStream.setExporterPositionSupplier(this::getLowestReplicatedExportedPosition);

      processorSnapshotController.consumeReplicatedSnapshots(logStream::delete);
      exporterSnapshotController.consumeReplicatedSnapshots(pos -> {});
    } else {
      executor =
          Executors.newSingleThreadExecutor(
              (r) -> new Thread(r, String.format("snapshot-request-server-%d", partitionId)));
      processorSnapshotRequestServer =
          new DefaultOnDemandSnapshotReplication(
              communicationService, partitionId, streamProcessorName, executor);
      processorSnapshotRequestServer.serve(
          request -> {
            LOG.info("Received snapshot replication request for partition {}", partitionId);
            processorSnapshotController.replicateLatestSnapshot(r -> r.run());
          });
      exporterSnapshotRequestServer =
          new DefaultOnDemandSnapshotReplication(
              communicationService, partitionId, exporterProcessorName, executor);
      exporterSnapshotRequestServer.serve(
          request -> exporterSnapshotController.replicateLatestSnapshot(r -> r.run()));
    }
  }

  private long getLowestReplicatedExportedPosition() {
    try {
      if (exporterSnapshotController.getValidSnapshotsCount() > 0) {
        exporterSnapshotController.recover();
        final ZeebeDb zeebeDb = exporterSnapshotController.openDb();
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
        exporterSnapshotController.close();
      } catch (Exception e) {
        LOG.error("Unexpected error occurred while closing the DB.", e);
      }
    }

    return -1;
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    processorStateReplication.close();
    exporterStateReplication.close();
    if (processorSnapshotRequestServer != null) {
      processorSnapshotRequestServer.close();
    }
    if (exporterSnapshotRequestServer != null) {
      exporterSnapshotRequestServer.close();
    }
    if (executor != null) {
      executor.shutdown();
    }
  }

  @Override
  public Partition get() {
    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public SnapshotController getExporterSnapshotController() {
    return exporterSnapshotController;
  }

  public StateSnapshotController getProcessorSnapshotController() {
    return processorSnapshotController;
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

  public Injector<StateStorageFactory> getStateStorageFactoryInjector() {
    return stateStorageFactoryInjector;
  }
}
