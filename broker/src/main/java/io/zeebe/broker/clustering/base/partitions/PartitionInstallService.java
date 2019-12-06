/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.base.partitions;

import static io.zeebe.broker.Broker.actorNamePattern;

import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.protocols.raft.RaftCommitListener;
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
import io.zeebe.broker.logstreams.delete.FollowerLogStreamDeletionService;
import io.zeebe.broker.logstreams.delete.LeaderLogStreamDeletionService;
import io.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.broker.transport.commandapi.CommandApiService;
import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.processor.AsyncSnapshotDirector;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.state.NoneSnapshotReplication;
import io.zeebe.logstreams.state.SnapshotDeletionListener;
import io.zeebe.logstreams.state.SnapshotReplication;
import io.zeebe.logstreams.state.SnapshotStorage;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.storage.atomix.AtomixLogStorageReader;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.util.DurationUtil;
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

public class PartitionInstallService extends Actor implements RaftCommitListener, Consumer<Role> {

  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;
  private static final int EXPORTER_PROCESSOR_ID = 1003;
  private static final String EXPORTER_NAME = "exporter-%d";

  private final ClusterEventService clusterEventService;
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

  public PartitionInstallService(
      final BrokerInfo localBroker,
      final RaftPartition atomixRaftPartition,
      List<PartitionListener> partitionListeners,
      final ClusterEventService clusterEventService,
      final ActorScheduler actorScheduler,
      final BrokerCfg brokerCfg,
      final CommandApiService commandApiService,
      final TypedRecordProcessorsFactory typedRecordProcessorsFactory) {
    this.localBroker = localBroker;
    this.atomixRaftPartition = atomixRaftPartition;
    this.clusterEventService = clusterEventService;
    this.brokerCfg = brokerCfg;
    this.typedRecordProcessorsFactory = typedRecordProcessorsFactory;
    this.commandApiService = commandApiService;
    this.partitionListeners = Collections.unmodifiableList(partitionListeners);
    this.partitionId = atomixRaftPartition.id().id();
    this.scheduler = actorScheduler;
    this.maxFragmentSize = (int) brokerCfg.getNetwork().getMaxMessageSize().toBytes();

    // load and validate exporters
    for (final ExporterCfg exporterCfg : brokerCfg.getExporters()) {
      try {
        exporterRepository.load(exporterCfg);
      } catch (ExporterLoadException | ExporterJarLoadException e) {
        throw new IllegalStateException(
            "Failed to load exporter with configuration: " + exporterCfg, e);
      }
    }
  }

  /**
   * Called by atomix on role change.
   *
   * @param newRole the new role of the raft partition
   */
  @Override
  public void accept(Role newRole) {
    actor.run(() -> onRoleChange(newRole));
  }

  private void onRoleChange(Role newRole) {
    switch (newRole) {
      case LEADER:
        if (raftRole != Role.LEADER) {
          onTransitionTo(this::transitionToLeader)
              .onComplete(
                  (success, error) -> {
                    if (error == null) {
                      partitionListeners.forEach(
                          l ->
                              l.onBecomingLeader(
                                  partitionId, atomixRaftPartition.term(), logStream));
                    } else {
                      LOG.error("Failed to install leader partition {}", partitionId, error);
                      // todo we should step down installation failed!
                    }
                  });
        }
        break;
      case INACTIVE:
      case PASSIVE:
      case PROMOTABLE:
      case CANDIDATE:
      case FOLLOWER:
      default:
        if (raftRole == null || raftRole == Role.LEADER) {

          onTransitionTo(this::transitionToFollower)
              .onComplete(
                  (success, error) -> {
                    if (error == null) {
                      partitionListeners.forEach(
                          l -> {
                            l.onBecomingFollower(
                                partitionId, atomixRaftPartition.term(), logStream);
                          });
                    } else {
                      LOG.error("Failed to install follower partition {}", partitionId, error);
                      // todo we should step down installation failed!
                    }
                  });
          //
          //          // on follower the other way around - since we need remove actors/resources
          //          partitionListeners.forEach(
          //              l -> {
          //                l.onBecomingFollower(partitionId, atomixRaftPartition.term(),
          // logStream);
          //              });
          //          onTransitionTo(this::transitionToFollower);
        }
        break;
    }

    LOG.debug("Partition role transitioning from {} to {}", raftRole, newRole);
    raftRole = newRole;
  }

  private CompletableActorFuture<Void> closePartition() {

    Collections.reverse(closeables);
    final var closeActorsFuture = new CompletableActorFuture<Void>();
    stepByStepClosing(closeActorsFuture, closeables);

    final var closingPartitionFuture = new CompletableActorFuture<Void>();
    closeActorsFuture.onComplete(
        (v, t) -> {
          if (t == null) {
            tearDownBaseInstallation();
            closingPartitionFuture.complete(null);
          } else {
            closingPartitionFuture.completeExceptionally(t);
          }
        });

    return closingPartitionFuture;
  }

  private void stepByStepClosing(
      final CompletableActorFuture<Void> closingFuture, List<Actor> actorsToClose) {
    if (actorsToClose.isEmpty()) {
      closingFuture.complete(null);
      return;
    }

    final Actor actor = actorsToClose.remove(0);
    actor
        .closeAsync()
        .onComplete(
            (v, t) -> {
              if (t == null) {
                stepByStepClosing(closingFuture, actorsToClose);
              } else {
                closingFuture.completeExceptionally(t);
              }
            });
  }

  private ActorFuture<Void> onTransitionTo(Consumer<CompletableActorFuture<Void>> roleTransition) {
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
    removeLeaderPartitionService()
        .onComplete(
            (nothing, error) -> {
              if (error != null) {
                LOG.error("Unexpected exception on removing leader partition!", error);
                // todo step down
                transitionComplete.completeExceptionally(error);
                return;
              }

              installFollowerPartition().onComplete(transitionComplete);
            });
  }

  private ActorFuture<Void> removeLeaderPartitionService() {
    LOG.debug("Removing leader partition services for partition {}", atomixRaftPartition.id());
    return closePartition();
  }

  private void transitionToLeader(final CompletableActorFuture<Void> transitionComplete) {
    removeFollowerPartitionService()
        .onComplete(
            (nothing, error) -> {
              if (error != null) {
                LOG.error("Unexpected exception on removing follower partition!", error);
                // todo step down
                transitionComplete.completeExceptionally(error);
                return;
              }

              installLeaderPartition().onComplete(transitionComplete);
            });
  }

  private ActorFuture<Void> removeFollowerPartitionService() {
    LOG.debug("Removing follower partition service for partition {}", atomixRaftPartition.id());
    return closePartition();
  }

  private ActorFuture<Void> installLeaderPartition() {
    LOG.debug("Installing leader partition service for partition {}", atomixRaftPartition.id());
    final var installFuture = new CompletableActorFuture<Void>();

    // Open logStreamAppender
    logStream
        .openAppender()
        .onComplete(
            (appender, throwable) -> {
              if (throwable != null) {
                LOG.error("Problem on opening the log appender!", throwable);
                // todo would be nice to step down now
                installFuture.completeExceptionally(throwable);
                return;
              }

              basePartitionInstallation();

              final ZeebeDb zeebeDb;
              try {
                snapshotController.recover();
                zeebeDb = snapshotController.openDb();
              } catch (final Exception e) {
                throw new IllegalStateException(
                    String.format(
                        "Unexpected error occurred while recovering snapshot controller during leader partition install for partition %d",
                        partitionId),
                    e);
              }
              //    partition =
              //    new LeaderPartition(brokerCfg, this.atomixRaftPartition, clusterEventService,
              // logStream);

              logStream
                  .getLogStorageAsync()
                  .onComplete(
                      (logStorage, errorOnReceiveLogStorage) -> {
                        if (errorOnReceiveLogStorage == null) {
                          logStream
                              .newLogStreamBatchWriter()
                              .onComplete(
                                  (batchWriter, errorOnReceiveWriteBuffer) -> {
                                    if (errorOnReceiveWriteBuffer == null) {
                                      final StreamProcessor streamProcessor =
                                          createStreamProcessor(zeebeDb, logStorage, batchWriter);
                                      streamProcessor
                                          .openAsync()
                                          .onComplete(
                                              (value, processorFail) -> {
                                                if (processorFail != null) {
                                                  LOG.error(
                                                      "Failed to install stream processor!",
                                                      processorFail);
                                                  // todo step down
                                                  installFuture.completeExceptionally(
                                                      processorFail);
                                                  return;
                                                }
                                                closeables.add(streamProcessor);

                                                final DataCfg dataCfg = brokerCfg.getData();
                                                installSnapshotDirector(streamProcessor, dataCfg);
                                                installExporter(zeebeDb, logStorage, dataCfg);

                                                installFuture.complete(null);
                                              });
                                    } else {
                                      installFuture.completeExceptionally(
                                          errorOnReceiveWriteBuffer);
                                    }
                                  });
                        } else {
                          installFuture.completeExceptionally(errorOnReceiveLogStorage);
                        }
                      });
            });

    return installFuture;
  }

  private void installExporter(ZeebeDb zeebeDb, LogStorage logStorage, DataCfg dataCfg) {
    final ExporterDirectorContext context =
        new ExporterDirectorContext()
            .id(EXPORTER_PROCESSOR_ID)
            .name(String.format(EXPORTER_NAME, partitionId))
            .logStream(logStream)
            .logStorage(logStorage)
            .zeebeDb(zeebeDb)
            .maxSnapshots(dataCfg.getMaxSnapshots())
            .descriptors(exporterRepository.getExporters().values())
            .logStreamReader(new BufferedLogStreamReader())
            .snapshotPeriod(DurationUtil.parse(dataCfg.getSnapshotPeriod()));

    final var exporterDirector = new ExporterDirector(context);
    closeables.add(exporterDirector);
    exporterDirector.startAsync(scheduler);
    // todo verify if this works with no exporters
    final LeaderLogStreamDeletionService leaderDeletionService =
        new LeaderLogStreamDeletionService(logStream, exporterDirector);
    snapshotStorage.addDeletionListener(leaderDeletionService);
  }

  private void installSnapshotDirector(StreamProcessor streamProcessor, DataCfg dataCfg) {
    final Duration snapshotPeriod = DurationUtil.parse(dataCfg.getSnapshotPeriod());
    final var asyncSnapshotDirector =
        new AsyncSnapshotDirector(streamProcessor, snapshotController, logStream, snapshotPeriod);
    closeables.add(asyncSnapshotDirector);
    scheduler.submitActor(asyncSnapshotDirector);
  }

  private StreamProcessor createStreamProcessor(
      ZeebeDb zeebeDb, LogStorage logStorage, LogStreamBatchWriter batchWriter) {
    return StreamProcessor.builder()
        .logStream(logStream)
        // for the reader
        .logStorage(logStorage)
        .batchWriter(batchWriter)
        .maxFragmentSize(batchWriter.getMaxFragmentLength())
        .actorScheduler(scheduler)
        .zeebeDb(zeebeDb)
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

  private ActorFuture<Void> installFollowerPartition() {
    LOG.debug("Installing follower partition service for partition {}", atomixRaftPartition.id());
    basePartitionInstallation();

    final SnapshotDeletionListener deletionService;
    final StatePositionSupplier positionSupplier = new StatePositionSupplier(partitionId, LOG);
    deletionService = new FollowerLogStreamDeletionService(logStream, positionSupplier);
    snapshotController.consumeReplicatedSnapshots();

    snapshotStorage.addDeletionListener(deletionService);

    return CompletableActorFuture.completed(null);
  }

  private void basePartitionInstallation() {
    snapshotStorage = createSnapshotStorage();
    snapshotController = createSnapshotController();
  }

  private void tearDownBaseInstallation() {
    if (stateReplication == null) {
      return;
    }

    try {
      stateReplication.close();
    } catch (final Exception e) {
      LOG.error("Unexpected error closing state replication for partition {}", partitionId, e);
    }

    if (snapshotController == null) {
      return;
    }

    try {
      snapshotController.close();
    } catch (final Exception e) {
      LOG.error(
          "Unexpected error occurred while closing the state snapshot controller for partition {}.",
          partitionId,
          e);
    }

    if (snapshotStorage == null) {
      return;
    }

    try {
      snapshotStorage.close();
    } catch (final Exception e) {
      LOG.error(
          "Unexpected error occurred closing snapshot storage for partition {}", partitionId, e);
    }
  }

  private StateSnapshotController createSnapshotController() {
    stateReplication =
        shouldReplicateSnapshots()
            ? new StateReplication(clusterEventService, partitionId)
            : new NoneSnapshotReplication();

    return new StateSnapshotController(
        DefaultZeebeDbFactory.DEFAULT_DB_FACTORY, snapshotStorage, stateReplication);
  }

  private SnapshotStorage createSnapshotStorage() {
    final var reader =
        new AtomixLogStorageReader(atomixRaftPartition.getServer().openReader(-1, Mode.COMMITS));
    final var runtimeDirectory = atomixRaftPartition.dataDirectory().toPath().resolve("runtime");
    return new AtomixSnapshotStorage(
        runtimeDirectory,
        atomixRaftPartition.getServer().getSnapshotStore(),
        new AtomixRecordEntrySupplierImpl(reader),
        brokerCfg.getData().getMaxSnapshots());
  }

  private boolean shouldReplicateSnapshots() {
    return brokerCfg.getCluster().getReplicationFactor() > 1;
  }

  @Override
  public <T extends RaftLogEntry> void onCommit(final Indexed<T> indexed) {
    if (indexed.type() == ZeebeEntry.class) {
      this.logStream.setCommitPosition(indexed.<ZeebeEntry>cast().entry().highestPosition());
    }
  }

  @Override
  public String getName() {
    return actorNamePattern(localBroker, "PartitionInstallService");
  }

  @Override
  protected void onActorStarting() {

    LogStreams.createAtomixLogStream(atomixRaftPartition)
        .withMaxFragmentSize(maxFragmentSize)
        .withActorScheduler(scheduler)
        .buildAsync()
        .onComplete(
            (log, error) -> {
              if (error == null) {
                this.logStream = log;
                atomixRaftPartition.getServer().addCommitListener(this);
                atomixRaftPartition.addRoleChangeListener(this);
                onRoleChange(atomixRaftPartition.getRole());
              } else {
                LOG.error(
                    "Failed to install log stream service for partition {}",
                    atomixRaftPartition.id().id(),
                    error);
                actor.close();
              }
            });
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
                      if (t == null) {
                        logStream.closeAsync().onComplete(closeFuture);
                      } else {
                        closeFuture.completeExceptionally(t);
                      }
                    }));
    closeFuture.join();

    super.close();
  }
}
