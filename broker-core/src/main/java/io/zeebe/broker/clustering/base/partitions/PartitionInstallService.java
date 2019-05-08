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
import static io.zeebe.broker.engine.EngineServiceNames.stateStorageFactoryServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.distributedLogPartitionServiceName;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.distributedlog.StorageConfiguration;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.distributedlog.impl.LogstreamConfig;
import io.zeebe.engine.state.StateStorageFactory;
import io.zeebe.engine.state.StateStorageFactoryService;
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
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.Arrays;
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
  private final String localMemberId;
  private final ClusterEventService clusterEventService;
  private final ClusterCommunicationService clusterCommunicationService;
  private final BrokerCfg brokerCfg;

  private ServiceStartContext startContext;
  private ServiceName<LogStream> logStreamServiceName;
  private ServiceName<StateStorageFactory> stateStorageFactoryServiceName;
  private ServiceName<Void> openLogStreamServiceName;
  private ServiceName<Partition> leaderPartitionServiceName;
  private ServiceName<Partition> followerPartitionServiceName;
  private ServiceName<Void> leaderInstallRootServiceName;
  private ServiceName<PartitionLeaderElection> partitionLeaderElectionServiceName;
  private String logName;
  private ActorFuture<PartitionLeaderElection> leaderElectionInstallFuture;
  private PartitionLeaderElection leaderElection;
  private ActorFuture<Void> transitionFuture;

  public PartitionInstallService(
      ClusterEventService clusterEventService,
      ClusterCommunicationService clusterCommunicationService,
      String localMemberId,
      final StorageConfiguration configuration,
      BrokerCfg brokerCfg) {
    this.configuration = configuration;
    this.partitionId = configuration.getPartitionId();
    this.localMemberId = localMemberId;
    this.clusterEventService = clusterEventService;
    this.clusterCommunicationService = clusterCommunicationService;
    this.brokerCfg = brokerCfg;
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
    leaderInstallRootServiceName = PartitionServiceNames.leaderInstallServiceRootName(logName);

    // TODO: Do we have to move to distributed log service
    final StateStorageFactoryService stateStorageFactoryService =
        new StateStorageFactoryService(configuration.getStatesDirectory());
    stateStorageFactoryServiceName = stateStorageFactoryServiceName(logName);
    partitionInstall
        .createService(stateStorageFactoryServiceName, stateStorageFactoryService)
        .install();

    leaderElection = new PartitionLeaderElection(partitionId);
    partitionLeaderElectionServiceName = partitionLeaderElectionServiceName(logName);
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
    LogstreamConfig.removeLeaderElectionController(localMemberId, partitionId);
  }

  @Override
  protected void onActorStarted() {
    actor.runOnCompletion(
        leaderElectionInstallFuture,
        (leaderElection, e) -> {
          if (e == null) {
            leaderElection.addListener(this);
            LogstreamConfig.putLeaderElectionController(localMemberId, partitionId, leaderElection);
          } else {
            LOG.error("Could not install leader election for partition {}", partitionId, e);
          }
        });
  }

  @Override
  public void onTransitionToLeader(int partitionId, long term) {
    actor.call(
        () -> {
          final CompletableActorFuture<Void> nextTransitionFuture = new CompletableActorFuture<>();
          if (transitionFuture != null && !transitionFuture.isDone()) {
            // wait until previous transition is complete
            actor.runOnCompletion(
                transitionFuture, (r, e) -> transitionToLeader(nextTransitionFuture, term));

          } else {
            transitionToLeader(nextTransitionFuture, term);
          }
          transitionFuture = nextTransitionFuture;
        });
  }

  private void transitionToLeader(CompletableActorFuture<Void> transitionComplete, long term) {
    final ActorFuture<Void> removeFuture = removeFollowerPartitionService();
    final ActorFuture<Void> installFuture = installLeaderPartition(term);
    actor.runOnCompletion(
        Arrays.asList((ActorFuture) removeFuture, (ActorFuture) installFuture),
        e -> transitionComplete.complete(null));
  }

  @Override
  public void onTransitionToFollower(int partitionId) {
    actor.call(
        () -> {
          final CompletableActorFuture<Void> nextTransitionFuture = new CompletableActorFuture<>();
          if (transitionFuture != null && !transitionFuture.isDone()) {
            // wait until previous transition is complete
            actor.runOnCompletion(
                transitionFuture, (r, e) -> transitionToFollower(nextTransitionFuture));

          } else {
            transitionToFollower(nextTransitionFuture);
          }
          transitionFuture = nextTransitionFuture;
        });
  }

  private void transitionToFollower(CompletableActorFuture<Void> transitionComplete) {
    final ActorFuture<Void> removeFuture = removeLeaderPartitionService();
    final ActorFuture<Partition> installFuture = installFollowerPartition();

    actor.runOnCompletion(
        Arrays.asList((ActorFuture) removeFuture, (ActorFuture) installFuture),
        e -> transitionComplete.complete(null));
  }

  private ActorFuture<Void> removeLeaderPartitionService() {
    if (startContext.hasService(leaderInstallRootServiceName)) {
      LOG.debug("Removing leader partition services for partition {}", partitionId);
      return startContext.removeService(leaderInstallRootServiceName);
    }
    return CompletableActorFuture.completed(null);
  }

  private ActorFuture<Void> installLeaderPartition(long leaderTerm) {
    LOG.debug("Installing leader partition service for partition {}", partitionId);
    final Partition partition =
        new Partition(
            brokerCfg,
            clusterEventService,
            clusterCommunicationService,
            partitionId,
            RaftState.LEADER);

    final CompositeServiceBuilder leaderInstallService =
        startContext.createComposite(leaderInstallRootServiceName);

    // Get an instance of DistributedLog
    final DistributedLogstreamPartition distributedLogstreamPartition =
        new DistributedLogstreamPartition(partitionId, leaderTerm);

    leaderInstallService
        .createService(distributedLogPartitionServiceName(logName), distributedLogstreamPartition)
        .dependency(ATOMIX_SERVICE, distributedLogstreamPartition.getAtomixInjector())
        .install();

    // Open logStreamAppender
    final LeaderOpenLogStreamAppenderService leaderOpenLogStreamAppenderService =
        new LeaderOpenLogStreamAppenderService();
    leaderInstallService
        .createService(openLogStreamServiceName, leaderOpenLogStreamAppenderService)
        .dependency(logStreamServiceName, leaderOpenLogStreamAppenderService.getLogStreamInjector())
        .dependency(distributedLogPartitionServiceName(logName))
        .install();

    leaderInstallService
        .createService(leaderPartitionServiceName, partition)
        .dependency(openLogStreamServiceName)
        .dependency(logStreamServiceName, partition.getLogStreamInjector())
        .dependency(stateStorageFactoryServiceName, partition.getStateStorageFactoryInjector())
        .group(LEADER_PARTITION_GROUP_NAME)
        .install();

    return leaderInstallService.install();
  }

  private ActorFuture<Partition> installFollowerPartition() {
    LOG.debug("Installing follower partition service for partition {}", partitionId);
    final Partition partition =
        new Partition(
            brokerCfg,
            clusterEventService,
            clusterCommunicationService,
            partitionId,
            RaftState.FOLLOWER);

    return startContext
        .createService(followerPartitionServiceName, partition)
        .dependency(logStreamServiceName, partition.getLogStreamInjector())
        .dependency(stateStorageFactoryServiceName, partition.getStateStorageFactoryInjector())
        .group(FOLLOWER_PARTITION_GROUP_NAME)
        .install();
  }

  private ActorFuture<Void> removeFollowerPartitionService() {
    if (startContext.hasService(followerPartitionServiceName)) {
      LOG.debug("Removing follower partition service for partition {}", partitionId);
      return startContext.removeService(followerPartitionServiceName);
    }
    return CompletableActorFuture.completed(null);
  }
}
