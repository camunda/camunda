/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.base.partitions;

import io.atomix.cluster.messaging.ClusterEventService;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.engine.EngineServiceNames;
import io.zeebe.broker.engine.impl.StateReplication;
import io.zeebe.broker.exporter.ExporterServiceNames;
import io.zeebe.broker.logstreams.delete.FollowerLogStreamDeletionService;
import io.zeebe.broker.logstreams.delete.LeaderLogStreamDeletionService;
import io.zeebe.broker.logstreams.restore.BrokerRestoreServer;
import io.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.db.ZeebeDb;
import io.zeebe.distributedlog.StorageConfiguration;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.StateStorageFactory;
import io.zeebe.logstreams.impl.delete.DeletionService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.LogStorage;
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
  public static final String GROUP_NAME = "raft-atomix";
  public static final Logger LOG = Loggers.CLUSTERING_LOGGER;
  private static final String PARTITION_NAME_FORMAT = "raft-atomix-partition-%d";
  private final Injector<LogStorage> logStorageInjector = new Injector<>();
  private Injector<LogStream> logStreamInjector = new Injector<>();
  private final ClusterEventService clusterEventService;
  private final int partitionId;
  private final RaftState state;
  private final long term; // term, if the state = LEADER
  private final StorageConfiguration configuration;
  private final BrokerCfg brokerCfg;
  private final BrokerRestoreServer restoreServer;
  private StateSnapshotController snapshotController;
  private SnapshotReplication stateReplication;
  private LogStream logStream;
  private LogStorage logStorage;
  private ZeebeDb zeebeDb;

  public Partition(
      final StorageConfiguration configuration,
      BrokerCfg brokerCfg,
      ClusterEventService clusterEventService,
      int partitionId,
      RaftState state,
      long term,
      BrokerRestoreServer restoreServer) {
    this.configuration = configuration;
    this.brokerCfg = brokerCfg;
    this.clusterEventService = clusterEventService;
    this.partitionId = partitionId;
    this.state = state;
    this.term = term;
    this.restoreServer = restoreServer;
  }

  public static String getPartitionName(final int partitionId) {
    return String.format(PARTITION_NAME_FORMAT, partitionId);
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    final CompletableActorFuture<Void> startedFuture = new CompletableActorFuture<>();
    logStream = logStreamInjector.getValue();
    logStorage = logStorageInjector.getValue();
    snapshotController = createSnapshotController();

    final DeletionService deletionService;
    if (state == RaftState.FOLLOWER) {
      final StatePositionSupplier positionSupplier =
          new StatePositionSupplier(
              snapshotController,
              partitionId,
              String.valueOf(brokerCfg.getCluster().getNodeId()),
              LOG);
      deletionService = new FollowerLogStreamDeletionService(logStream, positionSupplier);

      snapshotController.setDeletionService(deletionService);
      snapshotController.consumeReplicatedSnapshots();
    } else {
      final LeaderLogStreamDeletionService leaderDeletionService =
          new LeaderLogStreamDeletionService(logStream);
      startContext
          .createService(
              EngineServiceNames.leaderLogStreamDeletionService(partitionId), leaderDeletionService)
          .dependency(
              ExporterServiceNames.exporterDirectorServiceName(partitionId),
              leaderDeletionService.getExporterDirectorInjector())
          .install();
      deletionService = leaderDeletionService;
      snapshotController.setDeletionService(deletionService);

      try {
        snapshotController.recover();
        zeebeDb = snapshotController.openDb();
      } catch (final Exception e) {
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

  @Override
  public void stop(final ServiceStopContext stopContext) {
    try {
      stateReplication.close();
      LOG.debug("State replication closed for partition {}", partitionId);
    } catch (final Exception e) {
      LOG.error(
          "Unexpected error occurred while closing the state replication for partition {}.",
          partitionId,
          e);
    }
    restoreServer.close();
    LOG.debug("Restore server closed for partition {}", partitionId);

    try {
      snapshotController.close();
      LOG.debug("Snapshot controller closed for partition {}", partitionId);
    } catch (final Exception e) {
      LOG.error(
          "Unexpected error occurred while closing the state snapshot controller for partition {}.",
          partitionId,
          e);
    }
    LOG.debug("Partition {} closed.", partitionId);
  }

  @Override
  public Partition get() {
    return this;
  }

  private void startRestoreServer(final CompletableActorFuture<Void> startedFuture) {
    restoreServer
        .start(partitionId, logStorage, snapshotController)
        .whenComplete(
            (nothing, error) -> {
              if (error != null) {
                startedFuture.completeExceptionally(error);
              } else {
                startedFuture.complete(null);
              }
            });
  }

  public StorageConfiguration getConfiguration() {
    return configuration;
  }

  private StateSnapshotController createSnapshotController() {

    final StateStorageFactory storageFactory =
        new StateStorageFactory(configuration.getStatesDirectory());
    final StateStorage stateStorage = storageFactory.create();

    stateReplication =
        shouldReplicateSnapshots()
            ? new StateReplication(clusterEventService, partitionId)
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

  public int getPartitionId() {
    return partitionId;
  }

  public StateSnapshotController getSnapshotController() {
    return snapshotController;
  }

  public RaftState getState() {
    return state;
  }

  /**
   * Return the current raft term of this partition
   *
   * @return current raft term
   */
  public long getTerm() {
    return term;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public Injector<LogStream> getLogStreamInjector() {
    return logStreamInjector;
  }

  public Injector<LogStorage> getLogStorageInjector() {
    return logStorageInjector;
  }

  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }
}
