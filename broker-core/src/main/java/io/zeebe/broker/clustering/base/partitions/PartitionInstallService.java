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
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.FOLLOWER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADERSHIP_SERVICE_GROUP;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.raftInstallServiceName;
import static io.zeebe.broker.clustering.base.partitions.PartitionServiceNames.followerPartitionServiceName;
import static io.zeebe.broker.clustering.base.partitions.PartitionServiceNames.leaderOpenLogStreamServiceName;
import static io.zeebe.broker.clustering.base.partitions.PartitionServiceNames.leaderPartitionServiceName;
import static io.zeebe.broker.clustering.base.partitions.PartitionServiceNames.partitionLeaderElectionServiceName;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.stateStorageFactoryServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.distributedLogPartitionServiceName;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.logstreams.state.StateStorageFactory;
import io.zeebe.broker.logstreams.state.StateStorageFactoryService;
import io.zeebe.distributedlog.StorageConfiguration;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.logstreams.impl.service.LeaderOpenLogStreamAppenderService;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import org.slf4j.Logger;

/**
 * Service used to install the necessary services for creating a partition, namely logstream and
 * raft. Also listens to raft state changes (Leader, Follower) and installs the corresponding {@link
 * Partition} service(s) into the broker for other components (like client api or stream processing)
 * to attach to.
 */
public class PartitionInstallService extends Actor
    implements Service<Void>, PartitionRoleChangeListener {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final StorageConfiguration configuration;
  private final int partitionId;

  private ServiceStartContext startContext;
  private ServiceName<LogStream> logStreamServiceName;
  private ServiceName<StateStorageFactory> stateStorageFactoryServiceName;
  private ServiceName<Void> openLogStreamServiceName;
  private ServiceName<Partition> leaderPartitionServiceName;
  private ServiceName<Partition> followerPartitionServiceName;
  private String logName;
  private ActorFuture<PartitionLeaderElection> leaderElectionInstallFuture;
  private PartitionLeaderElection leaderElection;

  public PartitionInstallService(final StorageConfiguration configuration) {
    this.configuration = configuration;
    this.partitionId = configuration.getPartitionId();
  }

  @Override
  public Void get() {
    return null;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    this.startContext = startContext;

    final int partitionId = configuration.getPartitionId();
    logName = Partition.getPartitionName(partitionId);

    // TODO: rename/remove?
    final ServiceName<Void> raftInstallServiceName = raftInstallServiceName(partitionId);

    final CompositeServiceBuilder partitionInstall =
        startContext.createComposite(raftInstallServiceName);

    logStreamServiceName = LogStreamServiceNames.logStreamServiceName(logName);

    // TODO: Do we have to move to distributed log service
    final StateStorageFactoryService stateStorageFactoryService =
        new StateStorageFactoryService(configuration.getStatesDirectory());
    stateStorageFactoryServiceName = stateStorageFactoryServiceName(logName);
    partitionInstall
        .createService(stateStorageFactoryServiceName, stateStorageFactoryService)
        .install();

    leaderElection = new PartitionLeaderElection(partitionId);
    final ServiceName<PartitionLeaderElection> partitionLeaderElectionServiceName =
        partitionLeaderElectionServiceName(logName);
    leaderElectionInstallFuture =
        partitionInstall
            .createService(partitionLeaderElectionServiceName, leaderElection)
            .dependency(ATOMIX_SERVICE, leaderElection.getAtomixInjector())
            .dependency(ATOMIX_JOIN_SERVICE)
            .group(LEADERSHIP_SERVICE_GROUP)
            .install();

    partitionInstall.install();

    leaderPartitionServiceName = leaderPartitionServiceName(logName);
    openLogStreamServiceName = leaderOpenLogStreamServiceName(logName);
    followerPartitionServiceName = followerPartitionServiceName(logName);

    startContext.getScheduler().submitActor(this);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    leaderElection.removeListener(this);
  }

  @Override
  protected void onActorStarted() {
    actor.runOnCompletion(
        leaderElectionInstallFuture, (leaderElection, e) -> leaderElection.addListener(this));
  }

  public void onTransitionToLeader(int partitionId, long term) {
    actor.call(
        () -> {
          removeFollowerPartitionService();
          installLeaderPartition(term);
        });
  }

  public void onTransitionToFollower(int partitionId) {
    actor.call(
        () -> {
          removeLeaderPartitionService();
          installFollowerPartition();
        });
  }

  private void removeLeaderPartitionService() {
    if (startContext.hasService(leaderPartitionServiceName)) {
      LOG.debug("Removing leader partition services for partition {}", partitionId);
      startContext.removeService(leaderPartitionServiceName);
      startContext.removeService(openLogStreamServiceName);

      // Remove distributedlog partition service. It is needed only by the leader to append.
      startContext.removeService(distributedLogPartitionServiceName(logName));
    }
  }

  private void installLeaderPartition(long leaderTerm) {
    LOG.debug("Installing leader partition service for partition {}", partitionId);
    final Partition partition = new Partition(partitionId, RaftState.LEADER);

    // Get an instance of DistributedLog
    final DistributedLogstreamPartition distributedLogstreamPartition =
        new DistributedLogstreamPartition(partitionId, leaderTerm);
    startContext
        .createService(distributedLogPartitionServiceName(logName), distributedLogstreamPartition)
        .dependency(ATOMIX_SERVICE, distributedLogstreamPartition.getAtomixInjector())
        .install();

    // Open logStreamAppender
    final LeaderOpenLogStreamAppenderService leaderOpenLogStreamAppenderService =
        new LeaderOpenLogStreamAppenderService();
    startContext
        .createService(openLogStreamServiceName, leaderOpenLogStreamAppenderService)
        .dependency(logStreamServiceName, leaderOpenLogStreamAppenderService.getLogStreamInjector())
        .dependency(distributedLogPartitionServiceName(logName))
        .install();

    startContext
        .createService(leaderPartitionServiceName, partition)
        .dependency(openLogStreamServiceName)
        .dependency(logStreamServiceName, partition.getLogStreamInjector())
        .dependency(stateStorageFactoryServiceName, partition.getStateStorageFactoryInjector())
        .group(LEADER_PARTITION_GROUP_NAME)
        .install();
  }

  private void installFollowerPartition() {
    LOG.debug("Installing follower partition service for partition {}", partitionId);
    final Partition partition = new Partition(partitionId, RaftState.FOLLOWER);

    startContext
        .createService(followerPartitionServiceName, partition)
        .dependency(logStreamServiceName, partition.getLogStreamInjector())
        .dependency(stateStorageFactoryServiceName, partition.getStateStorageFactoryInjector())
        .group(FOLLOWER_PARTITION_GROUP_NAME)
        .install();
  }

  private void removeFollowerPartitionService() {
    if (startContext.hasService(followerPartitionServiceName)) {
      LOG.debug("Removing follower partition service for partition {}", partitionId);
      startContext.removeService(followerPartitionServiceName);
    }
  }
}
