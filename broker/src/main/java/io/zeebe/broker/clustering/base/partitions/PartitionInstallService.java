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

import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.protocols.raft.RaftCommitListener;
import io.atomix.protocols.raft.partition.RaftPartition;
import io.atomix.protocols.raft.storage.log.entry.RaftLogEntry;
import io.atomix.protocols.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames;
import io.zeebe.broker.engine.AsyncSnapshotingDirectorService;
import io.zeebe.broker.engine.StreamProcessorService;
import io.zeebe.broker.engine.StreamProcessorServiceNames;
import io.zeebe.broker.exporter.ExporterDirectorService;
import io.zeebe.broker.exporter.jar.ExporterJarLoadException;
import io.zeebe.broker.exporter.repo.ExporterLoadException;
import io.zeebe.broker.exporter.repo.ExporterRepository;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.service.LeaderOpenLogStreamAppenderService;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
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
    implements Service<PartitionInstallService>, PartitionRoleChangeListener, RaftCommitListener {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final ClusterEventService clusterEventService;
  private final BrokerCfg brokerCfg;
  private final RaftPartition partition;
  private final ServiceContainer serviceContainer;
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
  private LogStream logStream;
  private ActorFuture<LogStream> logStreamFuture;

  public PartitionInstallService(
      final RaftPartition partition,
      final ClusterEventService clusterEventService,
      final ServiceContainer serviceContainer,
      final BrokerCfg brokerCfg) {
    this.partition = partition;
    this.clusterEventService = clusterEventService;
    this.serviceContainer = serviceContainer;
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
            LOG.error("Could not install leader election for partition {}", partition.id().id(), e);
          }
        });
    actor.runOnCompletion(
        logStreamFuture,
        (log, error) -> {
          if (error == null) {
            setLogStream(log);
          } else {
            LOG.error(
                "Failed to install log stream service for partition {}",
                partition.id().id(),
                error);
          }
        });
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    this.startContext = startContext;

    final int partitionId = partition.id().id();
    logName = Partition.getPartitionName(partitionId);

    // TODO: rename/remove?
    final ServiceName<Void> raftInstallServiceName = raftInstallServiceName(partitionId);

    final CompositeServiceBuilder partitionInstall =
        startContext.createComposite(raftInstallServiceName);

    // installs the logstream service
    logStreamServiceName = LogStreamServiceNames.logStreamServiceName(logName);
    logStreamFuture =
        LogStreams.createAtomixLogStream(partition)
            .withMaxFragmentSize((int) brokerCfg.getNetwork().getMaxMessageSize().toBytes())
            .withServiceContainer(serviceContainer)
            .buildAsync();

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
    for (final ExporterCfg exporterCfg : brokerCfg.getExporters()) {
      try {
        exporterRepository.load(exporterCfg);
      } catch (ExporterLoadException | ExporterJarLoadException e) {
        throw new IllegalStateException(
            "Failed to load exporter with configuration: " + exporterCfg, e);
      }
    }

    leaderPartitionServiceName = leaderPartitionServiceName(logName);
    openLogStreamServiceName = leaderOpenLogStreamServiceName(logName);
    followerPartitionServiceName = followerPartitionServiceName(logName);
    startContext.getScheduler().submitActor(this);
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    leaderElection.removeListener(this);
    partition.getServer().removeCommitListener(this);

    if (logStream != null) {
      stopContext.async(logStream.closeAsync());
    }
  }

  @Override
  public PartitionInstallService get() {
    return this;
  }

  private void transitionToLeader(final CompletableActorFuture<Void> transitionComplete) {
    actor.runOnCompletion(
        removeFollowerPartitionService(),
        (nothing, error) ->
            actor.runOnCompletion(
                installLeaderPartition(), (v, e) -> transitionComplete.complete(null)));
  }

  @Override
  public void onTransitionToFollower(final int partitionId) {
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
  public void onTransitionToLeader(final int partitionId) {
    actor.call(
        () -> {
          final CompletableActorFuture<Void> nextTransitionFuture = new CompletableActorFuture<>();
          if (transitionFuture != null && !transitionFuture.isDone()) {
            // wait until previous transition is complete
            actor.runOnCompletion(
                transitionFuture, (r, e) -> transitionToLeader(nextTransitionFuture));

          } else {
            transitionToLeader(nextTransitionFuture);
          }
          transitionFuture = nextTransitionFuture;
        });
  }

  private void transitionToFollower(final CompletableActorFuture<Void> transitionComplete) {
    actor.runOnCompletion(
        removeLeaderPartitionService(),
        (nothing, error) ->
            actor.runOnCompletion(
                installFollowerPartition(), (ignored, err) -> transitionComplete.complete(null)));
  }

  private ActorFuture<Void> removeLeaderPartitionService() {
    LOG.debug("Removing leader partition services for partition {}", partition.id());
    return startContext.removeService(leaderInstallRootServiceName);
  }

  private ActorFuture<Void> installLeaderPartition() {
    LOG.debug("Installing leader partition service for partition {}", partition.id());
    final Partition partition =
        new Partition(brokerCfg, this.partition, clusterEventService, RaftState.LEADER);

    final CompositeServiceBuilder leaderInstallService =
        startContext.createComposite(leaderInstallRootServiceName);

    // Open logStreamAppender
    final LeaderOpenLogStreamAppenderService leaderOpenLogStreamAppenderService =
        new LeaderOpenLogStreamAppenderService();
    leaderInstallService
        .createService(openLogStreamServiceName, leaderOpenLogStreamAppenderService)
        .dependency(logStreamServiceName, leaderOpenLogStreamAppenderService.getLogStreamInjector())
        .install();

    leaderInstallService
        .createService(leaderPartitionServiceName, partition)
        .dependency(openLogStreamServiceName)
        .dependency(logStreamServiceName, partition.getLogStreamInjector())
        .group(LEADER_PARTITION_GROUP_NAME)
        .install();

    createEngineServices(leaderInstallService);
    createExporterServices(leaderInstallService);

    return leaderInstallService.install();
  }

  private void createEngineServices(final CompositeServiceBuilder leaderInstallService) {
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

  private void createExporterServices(final CompositeServiceBuilder leaderInstallService) {
    final ExporterDirectorService exporterDirectorService =
        new ExporterDirectorService(brokerCfg, exporterRepository);

    leaderInstallService
        .createService(exporterDirectorServiceName(partition.id().id()), exporterDirectorService)
        .dependency(leaderPartitionServiceName, exporterDirectorService.getPartitionInjector())
        .install();
  }

  private ActorFuture<Partition> installFollowerPartition() {
    LOG.debug("Installing follower partition service for partition {}", partition.id());
    final Partition partition =
        new Partition(brokerCfg, this.partition, clusterEventService, RaftState.FOLLOWER);

    return startContext
        .createService(followerPartitionServiceName, partition)
        .dependency(logStreamServiceName, partition.getLogStreamInjector())
        .group(FOLLOWER_PARTITION_GROUP_NAME)
        .install();
  }

  private ActorFuture<Void> removeFollowerPartitionService() {
    LOG.debug("Removing follower partition service for partition {}", partition.id());
    return startContext.removeService(followerPartitionServiceName);
  }

  private void setLogStream(final LogStream logStream) {
    this.logStream = logStream;
    partition.getServer().addCommitListener(this);
  }

  @Override
  public <T extends RaftLogEntry> void onCommit(final Indexed<T> indexed) {
    if (indexed.type() == ZeebeEntry.class) {
      this.logStream.setCommitPosition(indexed.<ZeebeEntry>cast().entry().highestPosition());
    }
  }
}
