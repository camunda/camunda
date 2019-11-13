/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
import static io.zeebe.broker.exporter.ExporterServiceNames.exporterDirectorServiceName;
import static io.zeebe.broker.system.SystemServiceNames.LEADER_MANAGEMENT_REQUEST_HANDLER;
import static io.zeebe.broker.transport.TransportServiceNames.COMMAND_API_SERVICE_NAME;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.distributedLogPartitionServiceName;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.protocols.raft.partition.RaftPartition;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames;
import io.zeebe.broker.engine.AsyncSnapshotingDirectorService;
import io.zeebe.broker.engine.StreamProcessorService;
import io.zeebe.broker.engine.StreamProcessorServiceNames;
import io.zeebe.broker.exporter.ExporterDirectorService;
import io.zeebe.broker.exporter.jar.ExporterJarLoadException;
import io.zeebe.broker.exporter.repo.ExporterLoadException;
import io.zeebe.broker.exporter.repo.ExporterRepository;
import io.zeebe.broker.logstreams.restore.BrokerRestoreServer;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.distributedlog.StorageConfiguration;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.logstreams.impl.service.LeaderOpenLogStreamAppenderService;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.DurationUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import org.slf4j.Logger;

/**
 * Service used to install the necessary services for creating a partition, namely logstream and
 * raft. Also listens to raft state changes (Leader, Follower) and installs the corresponding {@link
 * Partition} service(s) into the broker for other components (like client api or stream processing)
 * to attach to.
 */
public class PartitionInstallService extends Actor
    implements Service<PartitionInstallService>, PartitionRoleChangeListener {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final StorageConfiguration configuration;
  private final int partitionId;
  private final ClusterEventService clusterEventService;
  private final ClusterCommunicationService communicationService;
  private final BrokerCfg brokerCfg;
  private final RaftPartition partition;
  private final ExporterRepository exporterRepository = new ExporterRepository();

  private ServiceStartContext startContext;
  private ServiceName<LogStream> logStreamServiceName;
  private ServiceName<Void> openLogStreamServiceName;
  private ServiceName<Partition> leaderPartitionServiceName;
  private ServiceName<Partition> followerPartitionServiceName;
  private ServiceName<Void> leaderInstallRootServiceName;
  private String logName;
  private ActorFuture<PartitionLeaderElection> leaderElectionInstallFuture;
  private PartitionLeaderElection leaderElection;
  private ActorFuture<Void> transitionFuture;
  private ServiceName<LogStorage> logStorageServiceName;

  public PartitionInstallService(
      RaftPartition partition,
      ClusterEventService clusterEventService,
      ClusterCommunicationService communicationService,
      final StorageConfiguration configuration,
      BrokerCfg brokerCfg) {
    this.partition = partition;
    this.configuration = configuration;
    this.partitionId = configuration.getPartitionId();
    this.clusterEventService = clusterEventService;
    this.communicationService = communicationService;
    this.brokerCfg = brokerCfg;
  }

  @Override
  public String getName() {
    return "PartitionInstallService";
  }

  @Override
  protected void onActorStarted() {
    actor.runOnCompletion(
        leaderElectionInstallFuture,
        (leaderElection, e) -> {
          if (e == null) {
            leaderElection.addListener(this);
          } else {
            LOG.error("Could not install leader election for partition {}", partitionId, e);
          }
        });
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
    logStorageServiceName = LogStreamServiceNames.logStorageServiceName(logName);
    leaderInstallRootServiceName = PartitionServiceNames.leaderInstallServiceRootName(logName);

    leaderElection = new PartitionLeaderElection(partition);
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

    // load and validate exporters
    for (ExporterCfg exporterCfg : brokerCfg.getExporters()) {
      try {
        exporterRepository.load(exporterCfg);
      } catch (ExporterLoadException | ExporterJarLoadException e) {
        throw new RuntimeException("Failed to load exporter with configuration: " + exporterCfg, e);
      }
    }

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
  public PartitionInstallService get() {
    return this;
  }

  private void transitionToLeader(CompletableActorFuture<Void> transitionComplete, long term) {
    actor.runOnCompletion(
        removeFollowerPartitionService(),
        (nothing, error) ->
            actor.runOnCompletion(
                installLeaderPartition(term), (v, e) -> transitionComplete.complete(null)));
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

  private void transitionToFollower(CompletableActorFuture<Void> transitionComplete) {
    actor.runOnCompletion(
        removeLeaderPartitionService(),
        (nothing, error) ->
            actor.runOnCompletion(
                installFollowerPartition(), (partition, err) -> transitionComplete.complete(null)));
  }

  private ActorFuture<Void> removeLeaderPartitionService() {
    LOG.debug("Removing leader partition services for partition {}", partitionId);
    return startContext.removeService(leaderInstallRootServiceName);
  }

  private ActorFuture<Void> installLeaderPartition(long leaderTerm) {
    LOG.debug("Installing leader partition service for partition {}", partitionId);
    final BrokerRestoreServer restoreServer =
        new BrokerRestoreServer(communicationService, partitionId);
    final Partition partition =
        new Partition(
            configuration,
            brokerCfg,
            clusterEventService,
            partitionId,
            RaftState.LEADER,
            restoreServer);

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
        .dependency(logStorageServiceName, partition.getLogStorageInjector())
        .group(LEADER_PARTITION_GROUP_NAME)
        .install();

    createEngineServices(leaderInstallService);
    createExporterServices(leaderInstallService, partitionId);

    return leaderInstallService.install();
  }

  private void createEngineServices(CompositeServiceBuilder leaderInstallService) {
    final StreamProcessorService streamProcessorService = new StreamProcessorService(brokerCfg);
    leaderInstallService
        .createService(
            StreamProcessorServiceNames.streamProcessorService(logName), streamProcessorService)
        .dependency(leaderPartitionServiceName, streamProcessorService.getPartitionInjector())
        .dependency(COMMAND_API_SERVICE_NAME, streamProcessorService.getCommandApiServiceInjector())
        .dependency(
            ClusterBaseLayerServiceNames.TOPOLOGY_MANAGER_SERVICE,
            streamProcessorService.getTopologyManagerInjector())
        .dependency(
            ClusterBaseLayerServiceNames.ATOMIX_SERVICE, streamProcessorService.getAtomixInjector())
        .dependency(
            LEADER_MANAGEMENT_REQUEST_HANDLER,
            streamProcessorService.getLeaderManagementRequestInjector())
        .dependency(
            LogStreamServiceNames.logWriteBufferServiceName(logName),
            streamProcessorService.getLogStreamWriteBufferInjector())
        .dependency(logStorageServiceName, streamProcessorService.getLogStorageInjector())
        .install();

    final Duration snapshotPeriod = DurationUtil.parse(brokerCfg.getData().getSnapshotPeriod());
    final AsyncSnapshotingDirectorService snapshotDirectorService =
        new AsyncSnapshotingDirectorService(snapshotPeriod);

    final ServiceName<AsyncSnapshotingDirectorService> snapshotDirectorServiceName =
        StreamProcessorServiceNames.asyncSnapshotingDirectorService(logName);
    final ServiceName<StreamProcessor> streamProcessorControllerServiceName =
        StreamProcessorServiceNames.streamProcessorService(logName);

    leaderInstallService
        .createService(snapshotDirectorServiceName, snapshotDirectorService)
        .dependency(
            streamProcessorControllerServiceName,
            snapshotDirectorService.getStreamProcessorInjector())
        .dependency(leaderPartitionServiceName, snapshotDirectorService.getPartitionInjector())
        .install();
  }

  private void createExporterServices(
      CompositeServiceBuilder leaderInstallService, int partitionId) {
    final ExporterDirectorService exporterDirectorService =
        new ExporterDirectorService(brokerCfg, exporterRepository);

    leaderInstallService
        .createService(exporterDirectorServiceName(partitionId), exporterDirectorService)
        .dependency(leaderPartitionServiceName, exporterDirectorService.getPartitionInjector())
        .dependency(logStorageServiceName, exporterDirectorService.getLogStorageInjector())
        .install();
  }

  private ActorFuture<Partition> installFollowerPartition() {
    LOG.debug("Installing follower partition service for partition {}", partitionId);
    final BrokerRestoreServer restoreServer =
        new BrokerRestoreServer(communicationService, partitionId);
    final Partition partition =
        new Partition(
            configuration,
            brokerCfg,
            clusterEventService,
            partitionId,
            RaftState.FOLLOWER,
            restoreServer);

    return startContext
        .createService(followerPartitionServiceName, partition)
        .dependency(logStreamServiceName, partition.getLogStreamInjector())
        .dependency(logStorageServiceName, partition.getLogStorageInjector())
        .group(FOLLOWER_PARTITION_GROUP_NAME)
        .install();
  }

  private ActorFuture<Void> removeFollowerPartitionService() {
    LOG.debug("Removing follower partition service for partition {}", partitionId);
    return startContext.removeService(followerPartitionServiceName);
  }
}
