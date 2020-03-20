/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.partitions;

import io.atomix.protocols.raft.RaftCommitListener;
import io.atomix.protocols.raft.RaftRoleChangeListener;
import io.atomix.protocols.raft.RaftServer.Role;
import io.atomix.protocols.raft.partition.RaftPartition;
import io.atomix.protocols.raft.storage.log.entry.RaftLogEntry;
import io.atomix.protocols.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import io.atomix.storage.journal.JournalReader.Mode;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.clustering.atomix.storage.snapshot.AtomixRecordEntrySupplierImpl;
import io.zeebe.broker.clustering.atomix.storage.snapshot.AtomixSnapshotStorage;
import io.zeebe.broker.engine.impl.StateReplication;
import io.zeebe.broker.exporter.jar.ExporterJarLoadException;
import io.zeebe.broker.exporter.repo.ExporterLoadException;
import io.zeebe.broker.exporter.repo.ExporterRepository;
import io.zeebe.broker.exporter.stream.ExporterDirector;
import io.zeebe.broker.exporter.stream.ExporterDirectorContext;
import io.zeebe.broker.logstreams.AtomixLogCompactor;
import io.zeebe.broker.logstreams.LogCompactor;
import io.zeebe.broker.logstreams.LogDeletionService;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.broker.system.monitoring.HealthMetrics;
import io.zeebe.broker.transport.commandapi.CommandApiService;
import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.processor.AsyncSnapshotDirector;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.NoneSnapshotReplication;
import io.zeebe.logstreams.state.SnapshotMetrics;
import io.zeebe.logstreams.state.SnapshotReplication;
import io.zeebe.logstreams.state.SnapshotStorage;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.storage.atomix.AtomixLogStorage;
import io.zeebe.logstreams.storage.atomix.AtomixLogStorageReader;
import io.zeebe.logstreams.storage.atomix.ZeebeIndexMapping;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.util.health.CriticalComponentsHealthMonitor;
import io.zeebe.util.health.FailureListener;
import io.zeebe.util.health.HealthMonitor;
import io.zeebe.util.health.HealthMonitorable;
import io.zeebe.util.health.HealthStatus;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;

public final class ZeebePartition extends Actor
    implements RaftCommitListener, RaftRoleChangeListener, HealthMonitorable, FailureListener {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final int EXPORTER_PROCESSOR_ID = 1003;
  private static final String EXPORTER_NAME = "Exporter-%d";

  private final PartitionMessagingService messagingService;
  private final BrokerCfg brokerCfg;
  private final RaftPartition atomixRaftPartition;
  private final ExporterRepository exporterRepository = new ExporterRepository();

  private final ActorScheduler scheduler;
  private final TypedRecordProcessorsFactory typedRecordProcessorsFactory;
  private final CommandApiService commandApiService;
  private final List<PartitionListener> partitionListeners;
  private final List<Actor> closeables = new ArrayList<>();
  private final int partitionId;
  private final int maxFragmentSize;
  private final BrokerInfo localBroker;
  private ActorFuture<Void> transitionFuture;
  private LogStream logStream;
  private Role raftRole;
  private SnapshotReplication stateReplication;
  private SnapshotStorage snapshotStorage;
  private StateSnapshotController snapshotController;
  private ZeebeDb zeebeDb;
  private final String actorName;
  private FailureListener failureListener;
  private volatile HealthStatus healthStatus = HealthStatus.UNHEALTHY;
  private final HealthMonitor criticalComponentsHealthMonitor;
  private final ZeebeIndexMapping zeebeIndexMapping;
  private final HealthMetrics healthMetrics;
  private AtomixLogStorage atomixLogStorage;
  private long deferredCommitPosition;

  public ZeebePartition(
      final BrokerInfo localBroker,
      final RaftPartition atomixRaftPartition,
      final List<PartitionListener> partitionListeners,
      final PartitionMessagingService messagingService,
      final ActorScheduler actorScheduler,
      final BrokerCfg brokerCfg,
      final CommandApiService commandApiService,
      final ZeebeIndexMapping zeebeIndexMapping,
      final TypedRecordProcessorsFactory typedRecordProcessorsFactory) {
    this.localBroker = localBroker;
    this.atomixRaftPartition = atomixRaftPartition;
    this.messagingService = messagingService;
    this.brokerCfg = brokerCfg;
    this.typedRecordProcessorsFactory = typedRecordProcessorsFactory;
    this.commandApiService = commandApiService;
    this.partitionListeners = Collections.unmodifiableList(partitionListeners);
    this.partitionId = atomixRaftPartition.id().id();
    this.scheduler = actorScheduler;
    this.maxFragmentSize = (int) brokerCfg.getNetwork().getMaxMessageSizeInBytes();
    this.zeebeIndexMapping = zeebeIndexMapping;

    final var exporterEntries = brokerCfg.getExporters().entrySet();
    // load and validate exporters
    for (final var exporterEntry : exporterEntries) {
      final var id = exporterEntry.getKey();
      final var exporterCfg = exporterEntry.getValue();
      try {
        exporterRepository.load(id, exporterCfg);
      } catch (final ExporterLoadException | ExporterJarLoadException e) {
        throw new IllegalStateException(
            "Failed to load exporter with configuration: " + exporterCfg, e);
      }
    }

    this.actorName = buildActorName(localBroker.getNodeId(), "ZeebePartition-" + partitionId);
    criticalComponentsHealthMonitor = new CriticalComponentsHealthMonitor(actor, LOG);
    healthMetrics = new HealthMetrics(partitionId);
    healthMetrics.setUnhealthy();
  }

  /**
   * Called by atomix on role change.
   *
   * @param newRole the new role of the raft partition
   */
  @Override
  public void onNewRole(final Role newRole, final long newTerm) {
    actor.run(() -> onRoleChange(newRole, newTerm));
  }

  private void onRoleChange(final Role newRole, final long newTerm) {
    switch (newRole) {
      case LEADER:
        if (raftRole != Role.LEADER) {
          leaderTransition(newTerm);
        }
        break;
      case INACTIVE:
        inactiveTransition();
        break;
      case PASSIVE:
      case PROMOTABLE:
      case CANDIDATE:
      case FOLLOWER:
      default:
        if (raftRole == null || raftRole == Role.LEADER) {
          followerTransition(newTerm);
        }
        break;
    }

    LOG.debug("Partition role transitioning from {} to {}", raftRole, newRole);
    raftRole = newRole;
  }

  private void leaderTransition(final long newTerm) {
    onTransitionTo(this::transitionToLeader)
        .onComplete(
            (success, error) -> {
              if (error == null) {
                partitionListeners.forEach(
                    l -> l.onBecomingLeader(partitionId, newTerm, logStream));
              } else {
                LOG.error("Failed to install leader partition {}", partitionId, error);
                // TODO https://github.com/zeebe-io/zeebe/issues/3499
                onFailure();
              }
            });
  }

  private void followerTransition(final long newTerm) {
    onTransitionTo(this::transitionToFollower)
        .onComplete(
            (success, error) -> {
              if (error == null) {
                partitionListeners.forEach(
                    l -> l.onBecomingFollower(partitionId, newTerm, logStream));
              } else {
                LOG.error("Failed to install follower partition {}", partitionId, error);
                // TODO https://github.com/zeebe-io/zeebe/issues/3499
                // we should probably retry here
                onFailure();
              }
            });
  }

  private void inactiveTransition() {
    closePartition()
        .onComplete(
            (v, t) -> {
              if (t != null) {
                LOG.error("Failed to close partition when transition to inactive role");
              }
            });
    updateHealthStatus(HealthStatus.UNHEALTHY);
  }

  private ActorFuture<Void> onTransitionTo(
      final Consumer<CompletableActorFuture<Void>> roleTransition) {
    final CompletableActorFuture<Void> nextTransitionFuture = new CompletableActorFuture<>();
    if (transitionFuture != null && !transitionFuture.isDone()) {
      // wait until previous transition is complete
      transitionFuture.onComplete((value, error) -> roleTransition.accept(nextTransitionFuture));
    } else {
      roleTransition.accept(nextTransitionFuture);
    }
    transitionFuture = nextTransitionFuture;
    return transitionFuture;
  }

  private void transitionToFollower(final CompletableActorFuture<Void> transitionComplete) {
    LOG.debug("Removing leader partition services for partition {}", atomixRaftPartition.id());
    closePartition()
        .onComplete(
            (nothing, error) -> {
              if (error != null) {
                LOG.error("Unexpected exception on removing leader partition!", error);
                // TODO https://github.com/zeebe-io/zeebe/issues/3499
                // we should probably retry here - step down makes no sense
                transitionComplete.completeExceptionally(error);
                return;
              }

              installFollowerPartition().onComplete(transitionComplete);
            });
  }

  private ActorFuture<Void> installFollowerPartition() {
    LOG.debug("Installing follower partition service for partition {}", atomixRaftPartition.id());

    final CompletableActorFuture<Void> installFuture = new CompletableActorFuture<>();
    basePartitionInstallation()
        .onComplete(
            (deletionService, errorOnInstallation) -> {
              if (errorOnInstallation == null) {
                snapshotController.consumeReplicatedSnapshots();
                installFuture.complete(null);
              } else {
                LOG.error("Unexpected error on install deletion service.", errorOnInstallation);
                installFuture.completeExceptionally(errorOnInstallation);
              }
            });
    return installFuture;
  }

  private void transitionToLeader(final CompletableActorFuture<Void> transitionComplete) {
    LOG.debug("Removing follower partition service for partition {}", atomixRaftPartition.id());
    closePartition()
        .onComplete(
            (nothing, error) -> {
              if (error != null) {
                LOG.error("Unexpected exception on removing follower partition!", error);
                // TODO https://github.com/zeebe-io/zeebe/issues/3499
                transitionComplete.completeExceptionally(error);
                return;
              }

              installLeaderPartition().onComplete(transitionComplete);
            });
  }

  private ActorFuture<Void> installLeaderPartition() {
    LOG.debug("Installing leader partition service for partition {}", atomixRaftPartition.id());
    final var installFuture = new CompletableActorFuture<Void>();

    basePartitionInstallation()
        .onComplete(
            (success, errorOnBaseInstallation) -> {
              if (errorOnBaseInstallation == null) {
                try {
                  snapshotController.recover();
                  zeebeDb = snapshotController.openDb();
                } catch (final Exception e) {
                  // TODO https://github.com/zeebe-io/zeebe/issues/3499
                  onFailure();
                  LOG.error("Failed to recover from snapshot", e);
                  installFuture.completeExceptionally(
                      new IllegalStateException(
                          String.format(
                              "Unexpected error occurred while recovering snapshot controller during leader partition install for partition %d",
                              partitionId),
                          e));
                  return;
                }

                installProcessingPartition(installFuture);
              } else {
                LOG.error("Unexpected error on base installation.", errorOnBaseInstallation);
                installFuture.completeExceptionally(errorOnBaseInstallation);
              }
            });

    return installFuture;
  }

  private ActorFuture<Void> basePartitionInstallation() {
    final ActorFuture installFuture = new CompletableActorFuture();
    openLogStream()
        .onComplete(
            (log, error) -> {
              if (error == null) {
                this.logStream = log;
                if (deferredCommitPosition > 0) {
                  logStream.setCommitPosition(deferredCommitPosition);
                  deferredCommitPosition = -1;
                }
                criticalComponentsHealthMonitor.registerComponent("logstream", logStream);
                installStorageServices()
                    .onComplete(
                        (deletionService, errorInstall) -> {
                          if (errorInstall == null) {
                            installFuture.complete(deletionService);
                          } else {
                            installFuture.completeExceptionally(errorInstall);
                          }
                        });
              } else {
                LOG.error("Failed to install log stream for partition {}", partitionId, error);
                installFuture.completeExceptionally(error);
                onFailure();
              }
            });
    return installFuture;
  }

  private ActorFuture<Void> installStorageServices() {
    snapshotStorage = createSnapshotStorage();
    snapshotController = createSnapshotController();

    final LogCompactor logCompactor = new AtomixLogCompactor(atomixRaftPartition.getServer());
    final LogDeletionService deletionService =
        new LogDeletionService(localBroker.getNodeId(), partitionId, logCompactor, snapshotStorage);
    closeables.add(deletionService);

    return scheduler.submitActor(deletionService);
  }

  private StateSnapshotController createSnapshotController() {
    stateReplication =
        shouldReplicateSnapshots()
            ? new StateReplication(messagingService, partitionId, localBroker.getNodeId())
            : new NoneSnapshotReplication();

    return new StateSnapshotController(
        DefaultZeebeDbFactory.DEFAULT_DB_FACTORY, snapshotStorage, stateReplication);
  }

  private SnapshotStorage createSnapshotStorage() {
    final var reader =
        new AtomixLogStorageReader(
            zeebeIndexMapping, atomixRaftPartition.getServer().openReader(-1, Mode.COMMITS));
    final var runtimeDirectory = atomixRaftPartition.dataDirectory().toPath().resolve("runtime");
    return new AtomixSnapshotStorage(
        runtimeDirectory,
        atomixRaftPartition.getServer().getSnapshotStore(),
        new AtomixRecordEntrySupplierImpl(reader),
        new SnapshotMetrics(partitionId));
  }

  private boolean shouldReplicateSnapshots() {
    return brokerCfg.getCluster().getReplicationFactor() > 1;
  }

  private void installProcessingPartition(final CompletableActorFuture<Void> installFuture) {
    final StreamProcessor streamProcessor = createStreamProcessor(zeebeDb);
    closeables.add(streamProcessor);
    streamProcessor
        .openAsync()
        .onComplete(
            (value, processorFail) -> {
              if (processorFail == null) {
                final DataCfg dataCfg = brokerCfg.getData();
                installSnapshotDirector(streamProcessor, dataCfg)
                    .onComplete(
                        (nonResult, errorOnInstallSnapshotDirector) -> {
                          if (errorOnInstallSnapshotDirector == null) {
                            installExporter(zeebeDb).onComplete(installFuture);
                          } else {
                            // TODO https://github.com/zeebe-io/zeebe/issues/3499
                            LOG.error(
                                "Unexpected error on installing async snapshot director.",
                                errorOnInstallSnapshotDirector);
                            installFuture.completeExceptionally(errorOnInstallSnapshotDirector);
                          }
                        });
              } else {
                LOG.error("Unexpected error on stream processor installation!", processorFail);
                // TODO https://github.com/zeebe-io/zeebe/issues/3499
                installFuture.completeExceptionally(processorFail);
              }
            });
  }

  private StreamProcessor createStreamProcessor(final ZeebeDb zeebeDb) {
    return StreamProcessor.builder()
        .logStream(logStream)
        .actorScheduler(scheduler)
        .zeebeDb(zeebeDb)
        .nodeId(localBroker.getNodeId())
        .commandResponseWriter(commandApiService.newCommandResponseWriter())
        .onProcessedListener(commandApiService.getOnProcessedListener(partitionId))
        .streamProcessorFactory(
            (processingContext) -> {
              final ActorControl actor = processingContext.getActor();
              final ZeebeState zeebeState = processingContext.getZeebeState();
              return typedRecordProcessorsFactory.createTypedStreamProcessor(
                  actor, zeebeState, processingContext);
            })
        .build();
  }

  private ActorFuture<Void> installSnapshotDirector(
      final StreamProcessor streamProcessor, final DataCfg dataCfg) {
    final Duration snapshotPeriod = dataCfg.getSnapshotPeriod();
    final var asyncSnapshotDirector =
        new AsyncSnapshotDirector(
            localBroker.getNodeId(),
            streamProcessor,
            snapshotController,
            logStream,
            snapshotPeriod);
    closeables.add(asyncSnapshotDirector);
    return scheduler.submitActor(asyncSnapshotDirector);
  }

  private ActorFuture<Void> installExporter(final ZeebeDb zeebeDb) {
    final var exporterDescriptors = exporterRepository.getExporters().values();

    if (exporterDescriptors.isEmpty()) {
      return CompletableActorFuture.completed(null);
    }

    final ExporterDirectorContext context =
        new ExporterDirectorContext()
            .id(EXPORTER_PROCESSOR_ID)
            .name(
                buildActorName(localBroker.getNodeId(), String.format(EXPORTER_NAME, partitionId)))
            .logStream(logStream)
            .zeebeDb(zeebeDb)
            .descriptors(exporterDescriptors);

    final var exporterDirector = new ExporterDirector(context);
    closeables.add(exporterDirector);

    return exporterDirector.startAsync(scheduler);
  }

  private CompletableActorFuture<Void> closePartition() {
    Collections.reverse(closeables);
    final var closeActorsFuture = new CompletableActorFuture<Void>();
    stepByStepClosing(closeActorsFuture, closeables);

    final var closingPartitionFuture = new CompletableActorFuture<Void>();
    closeActorsFuture.onComplete(
        (v, t) -> {
          if (t == null) {
            tearDownBaseInstallation(closingPartitionFuture);
          } else {
            closingPartitionFuture.completeExceptionally(t);
          }
        });

    return closingPartitionFuture;
  }

  private void tearDownBaseInstallation(final CompletableActorFuture<Void> closeFuture) {
    closeStateReplication();
    closeSnapshotController();
    closeSnapshotStorage();

    closeLogStream()
        .onComplete(
            (closed, error) -> {
              if (error == null) {
                closeFuture.complete(null);
              } else {
                LOG.error(
                    "Unexpected error on closing logstream for partition {}", partitionId, error);
                closeFuture.completeExceptionally(error);
              }
            });
  }

  private ActorFuture<Void> closeLogStream() {
    if (logStream == null) {
      return CompletableActorFuture.completed(null);
    }

    criticalComponentsHealthMonitor.removeComponent("logstream");
    final LogStream logStreamToClose = logStream;
    logStream = null;
    return logStreamToClose.closeAsync();
  }

  private void closeSnapshotStorage() {
    if (snapshotStorage == null) {
      return;
    }

    try {
      snapshotStorage.close();
    } catch (final Exception e) {
      LOG.error(
          "Unexpected error occurred closing snapshot storage for partition {}", partitionId, e);
    } finally {
      snapshotStorage = null;
    }
  }

  private void closeSnapshotController() {
    if (snapshotController == null) {
      return;
    }

    try {
      snapshotController.close();
      zeebeDb = null;
    } catch (final Exception e) {
      LOG.error(
          "Unexpected error occurred while closing the state snapshot controller for partition {}.",
          partitionId,
          e);
    } finally {
      snapshotController = null;
      zeebeDb = null;
    }
  }

  private void closeStateReplication() {
    if (stateReplication == null) {
      return;
    }

    try {
      stateReplication.close();
    } catch (final Exception e) {
      LOG.error("Unexpected error closing state replication for partition {}", partitionId, e);
    } finally {
      stateReplication = null;
    }
  }

  private void stepByStepClosing(
      final CompletableActorFuture<Void> closingFuture, final List<Actor> actorsToClose) {
    if (actorsToClose.isEmpty()) {
      closingFuture.complete(null);
      return;
    }

    final Actor actor = actorsToClose.remove(0);
    LOG.debug("Closing {}", actor.getName());
    actor
        .closeAsync()
        .onComplete(
            (v, t) -> {
              if (t == null) {
                LOG.debug("Closed {} successfully", actor.getName());
                stepByStepClosing(closingFuture, actorsToClose);
              } else {
                LOG.debug("Unexpected exception on closing {}", actor.getName(), t);
                closingFuture.completeExceptionally(t);
              }
            });
  }

  @Override
  public <T extends RaftLogEntry> void onCommit(final Indexed<T> indexed) {
    if (indexed.type() == ZeebeEntry.class) {
      actor.run(
          () -> {
            final long commitPosition = indexed.<ZeebeEntry>cast().entry().highestPosition();
            if (this.logStream == null) {
              this.deferredCommitPosition = commitPosition;
              return;
            }
            this.logStream.setCommitPosition(commitPosition);
          });
    }
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  public void onActorStarting() {
    atomixLogStorage = AtomixLogStorage.ofPartition(zeebeIndexMapping, atomixRaftPartition);
    atomixRaftPartition.getServer().addCommitListener(this);
    atomixRaftPartition.addRoleChangeListener(this);
    onRoleChange(atomixRaftPartition.getRole(), atomixRaftPartition.term());
    onRecovered();
  }

  @Override
  protected void onActorStarted() {
    criticalComponentsHealthMonitor.startMonitoring();
    criticalComponentsHealthMonitor.addFailureListener(this);
  }

  @Override
  public void close() {
    // this is called from outside so it is safe to call join
    final var closeFuture = new CompletableActorFuture<Void>();
    actor.call(
        () ->
            closePartition()
                .onComplete(
                    (v, t) -> {
                      atomixRaftPartition.removeRoleChangeListener(this);
                      atomixRaftPartition.getServer().removeCommitListener(this);

                      if (t == null) {
                        closeFuture.complete(null);
                      } else {
                        closeFuture.completeExceptionally(t);
                      }
                    }));
    closeFuture.join();

    super.close();
  }

  private ActorFuture<LogStream> openLogStream() {
    return LogStream.builder()
        .withLogStorage(atomixLogStorage)
        .withLogName(atomixRaftPartition.name())
        .withNodeId(localBroker.getNodeId())
        .withPartitionId(atomixRaftPartition.id().id())
        .withMaxFragmentSize(maxFragmentSize)
        .withActorScheduler(scheduler)
        .buildAsync();
  }

  @Override
  public void onFailure() {
    actor.run(() -> updateHealthStatus(HealthStatus.UNHEALTHY));
  }

  @Override
  public void onRecovered() {
    actor.run(() -> updateHealthStatus(HealthStatus.HEALTHY));
  }

  private void updateHealthStatus(final HealthStatus newStatus) {
    if (healthStatus != newStatus) {
      healthStatus = newStatus;
      switch (newStatus) {
        case HEALTHY:
          healthMetrics.setHealthy();
          if (failureListener != null) {
            failureListener.onRecovered();
          }
          break;
        case UNHEALTHY:
          healthMetrics.setUnhealthy();
          if (failureListener != null) {
            failureListener.onFailure();
          }
          break;
        default:
          LOG.warn("Unknown health status {}", newStatus);
          break;
      }
    }
  }

  @Override
  public HealthStatus getHealthStatus() {
    return healthStatus;
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    actor.run(() -> this.failureListener = failureListener);
  }
}
