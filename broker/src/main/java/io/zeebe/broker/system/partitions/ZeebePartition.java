/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.partitions;

import static io.zeebe.engine.state.DefaultZeebeDbFactory.DEFAULT_DB_METRIC_EXPORTER_FACTORY;

import io.atomix.raft.RaftCommitListener;
import io.atomix.raft.RaftRoleChangeListener;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.snapshot.PersistedSnapshotStore;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import io.atomix.storage.journal.JournalReader.Mode;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.exporter.jar.ExporterJarLoadException;
import io.zeebe.broker.exporter.repo.ExporterLoadException;
import io.zeebe.broker.exporter.repo.ExporterRepository;
import io.zeebe.broker.exporter.stream.ExporterDirector;
import io.zeebe.broker.exporter.stream.ExporterDirectorContext;
import io.zeebe.broker.logstreams.AtomixLogCompactor;
import io.zeebe.broker.logstreams.LogCompactor;
import io.zeebe.broker.logstreams.LogDeletionService;
import io.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.zeebe.broker.system.monitoring.HealthMetrics;
import io.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.zeebe.broker.system.partitions.impl.AtomixRecordEntrySupplierImpl;
import io.zeebe.broker.system.partitions.impl.NoneSnapshotReplication;
import io.zeebe.broker.system.partitions.impl.StateControllerImpl;
import io.zeebe.broker.system.partitions.impl.StateReplication;
import io.zeebe.broker.transport.commandapi.CommandApiService;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDBMetricExporter;
import io.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.storage.atomix.AtomixLogStorage;
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
import io.zeebe.util.sched.AsyncClosable;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public final class ZeebePartition extends Actor
    implements RaftCommitListener,
        RaftRoleChangeListener,
        HealthMonitorable,
        FailureListener,
        DiskSpaceUsageListener {

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
  private final List<AsyncClosable> managedResources = new ArrayList<>();
  private final int partitionId;
  private final int maxFragmentSize;
  private final BrokerInfo localBroker;
  private ActorFuture<Void> transitionFuture;
  private LogStream logStream;
  private Role raftRole;
  private SnapshotReplication stateReplication;
  private PersistedSnapshotStore persistedSnapshotStore;
  private StateControllerImpl snapshotController;
  private ZeebeDb zeebeDb;
  private final String actorName;
  private FailureListener failureListener;
  private final HealthMonitor criticalComponentsHealthMonitor;
  private final ZeebeIndexMapping zeebeIndexMapping;
  private final HealthMetrics healthMetrics;
  private AtomixLogStorage atomixLogStorage;
  private long deferredCommitPosition;
  private final RaftPartitionHealth raftPartitionHealth;
  private final ZeebePartitionHealth zeebePartitionHealth;
  private long term;
  private StreamProcessor streamProcessor;

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
    raftPartitionHealth = new RaftPartitionHealth(atomixRaftPartition, actor, this::onRaftFailed);
    zeebePartitionHealth = new ZeebePartitionHealth(partitionId);
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
    this.term = newTerm;
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
                final List<ActorFuture<Void>> listenerFutures =
                    partitionListeners.stream()
                        .map(l -> l.onBecomingLeader(partitionId, newTerm, logStream))
                        .collect(Collectors.toList());
                actor.runOnCompletion(
                    listenerFutures,
                    t -> {
                      // Compare with the current term in case a new role transition
                      // happened
                      if (t != null && this.term == newTerm) {
                        onInstallFailure();
                      }
                    });
                onRecoveredInternal();
              } else {
                LOG.error("Failed to install leader partition {}", partitionId, error);
                onInstallFailure();
              }
            });
  }

  private void followerTransition(final long newTerm) {
    onTransitionTo(this::transitionToFollower)
        .onComplete(
            (success, error) -> {
              if (error == null) {
                final List<ActorFuture<Void>> listenerFutures =
                    partitionListeners.stream()
                        .map(l -> l.onBecomingFollower(partitionId, newTerm))
                        .collect(Collectors.toList());
                actor.runOnCompletion(
                    listenerFutures,
                    t -> {
                      // Compare with the current term in case a new role transition
                      // happened
                      if (t != null && this.term == newTerm) {
                        onInstallFailure();
                      }
                    });
                onRecoveredInternal();
              } else {
                LOG.error("Failed to install follower partition {}", partitionId, error);
                onInstallFailure();
              }
            });
  }

  private ActorFuture<Void> inactiveTransition() {
    return onTransitionTo(this::transitionToInactive);
  }

  private void transitionToInactive(final CompletableActorFuture<Void> transitionComplete) {
    zeebePartitionHealth.setHealthStatus(HealthStatus.UNHEALTHY);
    closePartition()
        .onComplete(
            (nothing, error) -> {
              if (error != null) {
                LOG.error("Unexpected exception on transition to inactive role!", error);
                transitionComplete.completeExceptionally(error);
                return;
              }
              transitionComplete.complete(null);
            });
  }

  private CompletableFuture<Void> onRaftFailed() {
    final CompletableFuture<Void> inactiveTransitionFuture = new CompletableFuture<>();
    actor.run(
        () -> {
          final ActorFuture<Void> transitionComplete = inactiveTransition();
          transitionComplete.onComplete(
              (v, t) -> {
                if (t != null) {
                  inactiveTransitionFuture.completeExceptionally(t);
                  return;
                }
                inactiveTransitionFuture.complete(null);
              });
        });
    return inactiveTransitionFuture;
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
                transitionComplete.completeExceptionally(error);
                return;
              }

              installFollowerPartition().onComplete(transitionComplete);
            });
  }

  private ActorFuture<Void> installFollowerPartition() {
    LOG.debug("Installing follower partition service for partition {}", atomixRaftPartition.id());

    final CompletableActorFuture<Void> installFuture = new CompletableActorFuture<>();

    installStorageServices()
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
                registerSnapshotListenerForReplication();

                try {
                  snapshotController.recover();
                  zeebeDb = snapshotController.openDb();
                } catch (final Exception e) {
                  onInstallFailure();
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
    final var installFuture = new CompletableActorFuture<Void>();
    openLogStream()
        .onComplete(
            (log, error) -> {
              if (error == null) {
                managedResources.add(this::closeLogStream);
                this.logStream = log;
                if (deferredCommitPosition > 0) {
                  logStream.setCommitPosition(deferredCommitPosition);
                  deferredCommitPosition = -1;
                }

                registerHealthComponent(logStream.getLogName(), logStream);
                installStorageServices().onComplete(installFuture);
              } else {
                LOG.error("Failed to install log stream for partition {}", partitionId, error);
                installFuture.completeExceptionally(error);
                onInstallFailure();
              }
            });
    return installFuture;
  }

  private ActorFuture<Void> installStorageServices() {
    persistedSnapshotStore = atomixRaftPartition.getServer().getPersistedSnapshotStore();

    final var controller = createSnapshotController();
    managedResources.add(this::closeSnapshotController);
    snapshotController = controller;

    final LogCompactor logCompactor = new AtomixLogCompactor(atomixRaftPartition.getServer());
    final LogDeletionService deletionService =
        new LogDeletionService(
            localBroker.getNodeId(), partitionId, logCompactor, persistedSnapshotStore);
    managedResources.add(deletionService);

    return scheduler.submitActor(deletionService);
  }

  private void registerSnapshotListenerForReplication() {
    managedResources.add(this::removeSnapshotListenerForReplication);
    persistedSnapshotStore.addSnapshotListener(snapshotController);
  }

  private ActorFuture<Void> removeSnapshotListenerForReplication() {
    persistedSnapshotStore.removeSnapshotListener(snapshotController);
    return CompletableActorFuture.completed(null);
  }

  private StateControllerImpl createSnapshotController() {
    final var runtimeDirectory = atomixRaftPartition.dataDirectory().toPath().resolve("runtime");
    final RaftLogReader reader = createReader();
    managedResources.add(this::closeStateReplication);
    stateReplication =
        shouldReplicateSnapshots()
            ? new StateReplication(messagingService, partitionId, localBroker.getNodeId())
            : new NoneSnapshotReplication();

    return new StateControllerImpl(
        partitionId,
        DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
        persistedSnapshotStore,
        runtimeDirectory,
        stateReplication,
        new AtomixRecordEntrySupplierImpl(zeebeIndexMapping, reader),
        StatePositionSupplier::getHighestExportedPosition);
  }

  private RaftLogReader createReader() {
    final var reader = atomixRaftPartition.getServer().openReader(-1, Mode.COMMITS);
    managedResources.add(
        () -> {
          reader.close();
          return CompletableActorFuture.completed(null);
        });
    return reader;
  }

  private boolean shouldReplicateSnapshots() {
    return brokerCfg.getCluster().getReplicationFactor() > 1;
  }

  private void installProcessingPartition(final CompletableActorFuture<Void> installFuture) {
    streamProcessor = createStreamProcessor(zeebeDb);
    managedResources.add(streamProcessor);
    streamProcessor
        .openAsync()
        .onComplete(
            (value, processorFail) -> {
              if (processorFail == null) {
                registerHealthComponent(streamProcessor.getName(), streamProcessor);
                final DataCfg dataCfg = brokerCfg.getData();
                installSnapshotDirector(streamProcessor, dataCfg)
                    .onComplete(
                        (nonResult, errorOnInstallSnapshotDirector) -> {
                          if (errorOnInstallSnapshotDirector == null) {
                            final var metricExporter =
                                DEFAULT_DB_METRIC_EXPORTER_FACTORY.apply(
                                    Integer.toString(partitionId), zeebeDb);

                            installRocksDBMetricExporter(metricExporter);
                            installExporter(zeebeDb).onComplete(installFuture);
                          } else {
                            LOG.error(
                                "Unexpected error on installing async snapshot director.",
                                errorOnInstallSnapshotDirector);
                            installFuture.completeExceptionally(errorOnInstallSnapshotDirector);
                          }
                        });
              } else {
                LOG.error("Unexpected error on stream processor installation!", processorFail);
                installFuture.completeExceptionally(processorFail);
              }
            });
  }

  private void installRocksDBMetricExporter(final ZeebeRocksDBMetricExporter metricExporter) {
    final var metricsTimer =
        actor.runAtFixedRate(
            Duration.ofSeconds(5),
            () -> {
              if (zeebeDb != null) {
                metricExporter.exportMetrics();
              }
            });
    managedResources.add(
        () -> {
          metricsTimer.cancel();
          return CompletableActorFuture.completed(null);
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
    managedResources.add(asyncSnapshotDirector);
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
    managedResources.add(exporterDirector);

    return exporterDirector.startAsync(scheduler);
  }

  private CompletableActorFuture<Void> closePartition() {
    streamProcessor = null;
    Collections.reverse(managedResources);
    final var closeActorsFuture = new CompletableActorFuture<Void>();
    stepByStepClosing(closeActorsFuture, managedResources);

    final var closingPartitionFuture = new CompletableActorFuture<Void>();
    closeActorsFuture.onComplete(closingPartitionFuture);

    return closingPartitionFuture;
  }

  private ActorFuture<Void> closeLogStream() {
    if (logStream == null) {
      return CompletableActorFuture.completed(null);
    }

    final LogStream logStreamToClose = logStream;
    logStream = null;
    return logStreamToClose.closeAsync();
  }

  private ActorFuture<Void> closeSnapshotController() {
    try {
      if (snapshotController != null) {
        snapshotController.close();
        zeebeDb = null;
      }
    } catch (final Exception e) {
      LOG.error(
          "Unexpected error occurred while closing the state snapshot controller for partition {}.",
          partitionId,
          e);
    } finally {
      snapshotController = null;
      zeebeDb = null;
    }

    return CompletableActorFuture.completed(null);
  }

  private ActorFuture<Void> closeStateReplication() {
    try {
      if (stateReplication != null) {
        stateReplication.close();
      }
    } catch (final Exception e) {
      LOG.error("Unexpected error closing state replication for partition {}", partitionId, e);
    } finally {
      stateReplication = null;
    }

    return CompletableActorFuture.completed(null);
  }

  private void stepByStepClosing(
      final CompletableActorFuture<Void> closingFuture, final List<AsyncClosable> actorsToClose) {
    if (actorsToClose.isEmpty()) {
      closingFuture.complete(null);
      return;
    }

    final AsyncClosable asyncClosable = actorsToClose.remove(0);
    asyncClosable
        .closeAsync()
        .onComplete(
            (v, t) -> {
              if (t == null) {
                LOG.debug("Closed {} successfully", asyncClosable);
                stepByStepClosing(closingFuture, actorsToClose);
              } else {
                LOG.debug("Unexpected exception on closing.", t);
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
    criticalComponentsHealthMonitor.addFailureListener(this);
    onRoleChange(atomixRaftPartition.getRole(), atomixRaftPartition.term());
  }

  @Override
  protected void onActorStarted() {
    criticalComponentsHealthMonitor.startMonitoring();
    criticalComponentsHealthMonitor.registerComponent(
        raftPartitionHealth.getName(), raftPartitionHealth);
    // Add a component that keep track of health of ZeebePartition. This way
    // criticalComponentsHealthMonitor can monitor the health of ZeebePartition similar to other
    // components.
    criticalComponentsHealthMonitor.registerComponent(
        zeebePartitionHealth.getName(), zeebePartitionHealth);
  }

  @Override
  protected void onActorClosed() {
    criticalComponentsHealthMonitor.removeComponent(raftPartitionHealth.getName());
    raftPartitionHealth.close();
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

  @Override
  protected void handleFailure(final Exception failure) {
    LOG.warn("Uncaught exception in {}.", actorName, failure);
    // Most probably exception happened in the middle of installing leader or follower services
    // because this actor is not doing anything else
    onInstallFailure();
  }

  private ActorFuture<LogStream> openLogStream() {
    return LogStream.builder()
        .withLogStorage(atomixLogStorage)
        .withLogName("logstream-" + atomixRaftPartition.name())
        .withNodeId(localBroker.getNodeId())
        .withPartitionId(atomixRaftPartition.id().id())
        .withMaxFragmentSize(maxFragmentSize)
        .withActorScheduler(scheduler)
        .buildAsync();
  }

  @Override
  public void onFailure() {
    actor.run(
        () -> {
          healthMetrics.setUnhealthy();
          if (failureListener != null) {
            failureListener.onFailure();
          }
        });
  }

  @Override
  public void onRecovered() {
    actor.run(
        () -> {
          healthMetrics.setHealthy();
          if (failureListener != null) {
            failureListener.onRecovered();
          }
        });
  }

  private void onInstallFailure() {
    zeebePartitionHealth.setHealthStatus(HealthStatus.UNHEALTHY);
    if (atomixRaftPartition.getRole() == Role.LEADER) {
      LOG.info("Unexpected failures occurred when installing leader services, stepping down");
      atomixRaftPartition.stepDown();
    }
  }

  private void onRecoveredInternal() {
    zeebePartitionHealth.setHealthStatus(HealthStatus.HEALTHY);
  }

  @Override
  public HealthStatus getHealthStatus() {
    return criticalComponentsHealthMonitor.getHealthStatus();
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    actor.run(() -> this.failureListener = failureListener);
  }

  @Override
  public void onDiskSpaceNotAvailable() {
    actor.call(
        () -> {
          if (streamProcessor != null) {
            LOG.warn("Disk space usage is above threshold. Pausing stream processor.");
            streamProcessor.pauseProcessing();
          }
        });
  }

  @Override
  public void onDiskSpaceAvailable() {
    actor.call(
        () -> {
          if (streamProcessor != null) {
            LOG.info("Disk space usage is below threshold. Resuming stream processor.");
            streamProcessor.resumeProcessing();
          }
        });
  }

  private void registerHealthComponent(
      final String name, final HealthMonitorable healthMonitorable) {
    criticalComponentsHealthMonitor.registerComponent(name, healthMonitorable);

    managedResources.add(
        () -> {
          criticalComponentsHealthMonitor.removeComponent(name);
          return CompletableActorFuture.completed(null);
        });
  }
}
