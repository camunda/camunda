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

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.ATOMIX_SERVICE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.FOLLOWER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.RAFT_SERVICE_GROUP;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.followerPartitionServiceName;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.leaderPartitionServiceName;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.raftInstallServiceName;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.stateStorageFactoryServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.distributedLogPartitionServiceName;
import static io.zeebe.raft.RaftServiceNames.leaderInitialEventCommittedServiceName;
import static io.zeebe.raft.RaftServiceNames.raftServiceName;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfiguration;
import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import io.zeebe.broker.logstreams.state.StateStorageFactory;
import io.zeebe.broker.logstreams.state.StateStorageFactoryService;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftStateListener;
import io.zeebe.raft.controller.MemberReplicateLogController;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.sched.channel.OneToOneRingBufferChannel;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.Collection;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.slf4j.Logger;

/**
 * Service used to install the necessary services for creating a partition, namely logstream and
 * raft. Also listens to raft state changes (Leader, Follower) and installs the corresponding {@link
 * Partition} service(s) into the broker for other components (like client api or stream processing)
 * to attach to.
 */
public class PartitionInstallService implements Service<Void>, RaftStateListener {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final BrokerCfg brokerCfg;
  private final Injector<ClientTransport> clientTransportInjector = new Injector<>();
  private final RaftPersistentConfiguration configuration;
  private final PartitionInfo partitionInfo;

  private ServiceStartContext startContext;
  private ServiceName<LogStream> logStreamServiceName;

  private ServiceName<StateStorageFactory> stateStorageFactoryServiceName;

  public PartitionInstallService(
      final BrokerCfg brokerCfg, final RaftPersistentConfiguration configuration) {
    this.brokerCfg = brokerCfg;
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

    final ClientTransport clientTransport = clientTransportInjector.getValue();

    final int partitionId = configuration.getPartitionId();
    final String logName = String.format("partition-%d", partitionId);

    final ServiceName<Void> raftInstallServiceName = raftInstallServiceName(partitionId);
    final ServiceName<Raft> raftServiceName = raftServiceName(logName);

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
        .install();

    final OneToOneRingBufferChannel messageBuffer =
        new OneToOneRingBufferChannel(
            new UnsafeBuffer(
                new byte
                    [(MemberReplicateLogController.REMOTE_BUFFER_SIZE)
                        + RingBufferDescriptor.TRAILER_LENGTH]));

    final Raft raftService =
        new Raft(
            logName,
            brokerCfg.getRaft(),
            brokerCfg.getCluster().getNodeId(),
            clientTransport,
            configuration,
            messageBuffer,
            this);

    raftService.addMembersWhenJoined(configuration.getMembers());

    partitionInstall
        .createService(raftServiceName, raftService)
        .dependency(logStreamServiceName, raftService.getLogStreamInjector())
        .dependency(stateStorageFactoryServiceName)
        .group(RAFT_SERVICE_GROUP)
        .install();

    partitionInstall.install();
  }

  @Override
  public ActorFuture<Void> onMemberLeaving(final Raft raft, final Collection<Integer> nodeIds) {
    final ServiceName<Partition> partitionServiceName = leaderPartitionServiceName(raft.getName());

    final int raftMemberSize = nodeIds.size() + 1; // raft does not count itself as member
    final int replicationFactor = partitionInfo.getReplicationFactor();

    ActorFuture<Void> leaveHandledFuture = CompletableActorFuture.completed(null);

    if (startContext.hasService(partitionServiceName)) {
      if (raftMemberSize < replicationFactor) {
        LOG.debug(
            "Removing partition service for {}. Replication factor not reached, got {}/{}.",
            partitionInfo,
            raftMemberSize,
            replicationFactor);

        leaveHandledFuture = startContext.removeService(partitionServiceName);
      } else {
        LOG.debug(
            "Not removing partition {}, replication factor still reached, got {}/{}.",
            partitionInfo,
            raftMemberSize,
            replicationFactor);
      }
    }

    return leaveHandledFuture;
  }

  @Override
  public void onMemberJoined(final Raft raft, final Collection<Integer> currentNodeIds) {
    if (raft.getState() == RaftState.LEADER) {
      installLeaderPartition(raft);
    }
  }

  @Override
  public void onStateChange(final Raft raft, final RaftState raftState) {
    switch (raftState) {
      case LEADER:
        removeFollowerPartitionService(raft);
        installLeaderPartition(raft);
        break;
      case FOLLOWER:
        installFollowerPartition(raft);
        break;
      case CANDIDATE:
      default:
        removeFollowerPartitionService(raft);
        break;
    }
  }

  private void installLeaderPartition(final Raft raft) {
    final ServiceName<Partition> partitionServiceName = leaderPartitionServiceName(raft.getName());

    final int raftMemberSize = raft.getMemberSize() + 1; // raft does not count itself as member
    final int replicationFactor = partitionInfo.getReplicationFactor();

    if (!startContext.hasService(partitionServiceName)) {
      if (raftMemberSize >= replicationFactor) {
        LOG.debug(
            "Installing partition service for {}. Replication factor reached, got {}/{}.",
            partitionInfo,
            raftMemberSize,
            replicationFactor);

        final Partition partition = new Partition(partitionInfo, RaftState.LEADER);

        startContext
            .createService(partitionServiceName, partition)
            .dependency(leaderInitialEventCommittedServiceName(raft.getName(), raft.getTerm()))
            .dependency(logStreamServiceName, partition.getLogStreamInjector())
            .dependency(stateStorageFactoryServiceName, partition.getStateStorageFactoryInjector())
            .group(LEADER_PARTITION_GROUP_NAME)
            .install();
      } else {
        LOG.debug(
            "Not installing partition service for {}. Replication factor not reached, got {}/{}.",
            partitionInfo,
            raftMemberSize,
            replicationFactor);
      }
    }
  }

  private void installFollowerPartition(final Raft raft) {
    final Partition partition = new Partition(partitionInfo, RaftState.FOLLOWER);
    final ServiceName<Partition> partitionServiceName =
        followerPartitionServiceName(raft.getName());

    if (!startContext.hasService(partitionServiceName)) {
      LOG.debug("Installing follower partition service for {}", partitionInfo);
      startContext
          .createService(partitionServiceName, partition)
          .dependency(logStreamServiceName, partition.getLogStreamInjector())
          .dependency(stateStorageFactoryServiceName, partition.getStateStorageFactoryInjector())
          .group(FOLLOWER_PARTITION_GROUP_NAME)
          .install();
    }
  }

  private void removeFollowerPartitionService(final Raft raft) {
    final ServiceName<Partition> partitionServiceName =
        followerPartitionServiceName(raft.getName());

    if (startContext.hasService(partitionServiceName)) {
      LOG.debug("Removing follower partition service for partition {}", partitionInfo);
      startContext.removeService(partitionServiceName);
    }
  }

  public Injector<ClientTransport> getClientTransportInjector() {
    return clientTransportInjector;
  }
}
