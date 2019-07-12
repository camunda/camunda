/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.restore;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.Partition;
import io.atomix.primitive.partition.PartitionGroup;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionService;
import io.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.zeebe.distributedlog.StorageConfiguration;
import io.zeebe.distributedlog.impl.LogstreamConfig;
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.RestoreFactory;
import io.zeebe.distributedlog.restore.RestoreNodeProvider;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreContext;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.StateStorageFactory;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import org.slf4j.Logger;

public class BrokerRestoreFactory implements RestoreFactory {
  private final ClusterCommunicationService communicationService;
  private final PartitionService partitionService;
  private final String partitionGroupName;
  private final String localMemberId;

  public BrokerRestoreFactory(
      ClusterCommunicationService communicationService,
      PartitionService partitionService,
      String partitionGroupName,
      String localMemberId) {
    this.communicationService = communicationService;
    this.partitionService = partitionService;
    this.partitionGroupName = partitionGroupName;
    this.localMemberId = localMemberId;
  }

  @Override
  public RestoreNodeProvider createNodeProvider(int partitionId) {
    return new CyclicPartitionNodeProvider(() -> getPartition(partitionId), localMemberId);
  }

  @Override
  public RestoreClient createClient(int partitionId) {
    return new BrokerRestoreClient(communicationService, partitionId);
  }

  @Override
  public SnapshotRestoreContext createSnapshotRestoreContext(int partitionId, Logger logger) {
    final StorageConfiguration configuration =
        LogstreamConfig.getConfig(localMemberId, partitionId).join();
    final StateStorage restoreStateStorage =
        new StateStorageFactory(configuration.getStatesDirectory()).createTemporary("-restore-log");

    final SnapshotController stateSnapshotController =
        new StateSnapshotController(DefaultZeebeDbFactory.DEFAULT_DB_FACTORY, restoreStateStorage);

    final StatePositionSupplier positionSupplier =
        new StatePositionSupplier(stateSnapshotController, partitionId, localMemberId, logger);

    return new BrokerSnapshotRestoreContext(positionSupplier, restoreStateStorage);
  }

  private Partition getPartition(int partitionId) {
    final PartitionGroup group = partitionService.getPartitionGroup(partitionGroupName);
    return group.getPartition(PartitionId.from(partitionGroupName, partitionId));
  }

  static String getLogReplicationTopic(int partitionId) {
    return String.format("log-replication-%d", partitionId);
  }

  static String getRestoreInfoTopic(int partitionId) {
    return String.format("restore-info-%d", partitionId);
  }

  static String getSnapshotRequestTopic(int partitionId) {
    return String.format("snapshot-request-%d", partitionId);
  }

  static String getSnapshotInfoRequestTopic(int partitionId) {
    return String.format("snapshot-info-request-%d", partitionId);
  }
}
