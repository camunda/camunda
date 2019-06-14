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

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.Partition;
import io.atomix.primitive.partition.PartitionGroup;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionService;
import io.zeebe.broker.engine.EngineService;
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
        new StateStorageFactory(configuration.getStatesDirectory())
            .create(partitionId, EngineService.PROCESSOR_NAME, "-restore-log");

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
