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

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.ATOMIX_JOIN_SERVICE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.ATOMIX_SERVICE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADERSHIP_SERVICE_GROUP;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.raftInstallServiceName;
import static io.zeebe.broker.clustering.base.partitions.PartitionServiceNames.partitionLeaderElectionServiceName;
import static io.zeebe.broker.clustering.base.partitions.PartitionServiceNames.partitionLeadershipEventListenerServiceName;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.stateStorageFactoryServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.distributedLogPartitionServiceName;

import io.atomix.core.election.LeaderElection;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfiguration;
import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import io.zeebe.broker.logstreams.state.StateStorageFactory;
import io.zeebe.broker.logstreams.state.StateStorageFactoryService;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import org.slf4j.Logger;

/**
 * Service used to install the necessary services for creating a partition, namely logstream and
 * raft. Also listens to raft state changes (Leader, Follower) and installs the corresponding {@link
 * Partition} service(s) into the broker for other components (like client api or stream processing)
 * to attach to.
 */
public class PartitionInstallService implements Service<Void> {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final RaftPersistentConfiguration configuration;
  private final PartitionInfo partitionInfo;

  private ServiceStartContext startContext;
  private ServiceName<LogStream> logStreamServiceName;

  private ServiceName<StateStorageFactory> stateStorageFactoryServiceName;

  public PartitionInstallService(final RaftPersistentConfiguration configuration) {
    this.configuration = configuration;
    this.partitionInfo =
        new PartitionInfo(configuration.getPartitionId(), configuration.getReplicationFactor());
  }

  @Override
  public Void get() {
    return null;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    this.startContext = startContext;

    final int partitionId = configuration.getPartitionId();
    final String logName = String.format("partition-%d", partitionId);

    // TODO: rename/remove?
    final ServiceName<Void> raftInstallServiceName = raftInstallServiceName(partitionId);

    final CompositeServiceBuilder partitionInstall =
        startContext.createComposite(raftInstallServiceName);

    final String snapshotPath = configuration.getSnapshotsDirectory().getAbsolutePath();

    logStreamServiceName =
        LogStreams.createFsLogStream(partitionId)
            .logDirectory(configuration.getLogDirectory().getAbsolutePath())
            .logSegmentSize((int) configuration.getLogSegmentSize())
            .logName(logName)
            .snapshotStorage(LogStreams.createFsSnapshotStore(snapshotPath).build())
            .buildWith(partitionInstall);

    final StateStorageFactoryService stateStorageFactoryService =
        new StateStorageFactoryService(configuration.getStatesDirectory());
    stateStorageFactoryServiceName = stateStorageFactoryServiceName(logName);
    partitionInstall
        .createService(stateStorageFactoryServiceName, stateStorageFactoryService)
        .install();

    final DistributedLogstreamPartition distributedLogstreamPartition =
        new DistributedLogstreamPartition(partitionId);
    partitionInstall
        .createService(distributedLogPartitionServiceName(logName), distributedLogstreamPartition)
        .dependency(ATOMIX_SERVICE, distributedLogstreamPartition.getAtomixInjector())
        .dependency(ATOMIX_JOIN_SERVICE)
        .install();

    final PartitionLeaderElection leaderElection = new PartitionLeaderElection(partitionId);
    final ServiceName<LeaderElection> partitionLeaderElectionServiceName =
        partitionLeaderElectionServiceName(logName);
    partitionInstall
        .createService(partitionLeaderElectionServiceName, leaderElection)
        .dependency(ATOMIX_SERVICE, leaderElection.getAtomixInjector())
        .dependency(ATOMIX_JOIN_SERVICE)
        .group(LEADERSHIP_SERVICE_GROUP)
        .install();

    final PartitionRoleChangeListener roleChangeListener =
        new PartitionRoleChangeListener(partitionInfo);
    partitionInstall
        .createService(partitionLeadershipEventListenerServiceName(logName), roleChangeListener)
        .dependency(ATOMIX_SERVICE, roleChangeListener.getAtomixInjector())
        .dependency(partitionLeaderElectionServiceName, roleChangeListener.getElectionInjector())
        .dependency(logStreamServiceName, roleChangeListener.getLogStreamInjector())
        .install();

    partitionInstall.install();
  }
}
