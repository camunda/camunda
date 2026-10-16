/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.backup.logarchive.leader.LogArchivePublisher;
import io.camunda.zeebe.backup.logarchive.leader.LogArchiver;
import io.camunda.zeebe.backup.logarchive.leader.LogArchiverRaftEntrySource;
import io.camunda.zeebe.backup.logarchive.leader.LogArchiverTimeline;
import io.camunda.zeebe.backup.logarchive.leader.SnapshotUploader;
import io.camunda.zeebe.backup.logarchive.processing.LogArchiveListener;
import io.camunda.zeebe.backup.logarchive.processing.LogArchiveProcessorConfig;
import io.camunda.zeebe.backup.logarchive.processing.LogArchiveRecordsProcessor;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.broker.system.configuration.backup.LogArchiveCfg;
import io.camunda.zeebe.broker.system.configuration.backup.LogArchiveStoreFactory;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Installs the log archive when {@code logArchive.enabled} (spec sections 6 and 8).
 *
 * <p>On every role it creates a fresh {@link LogArchiveRecordsProcessor}, which the following
 * {@link StreamProcessorTransitionStep} registers, so that followers replay log archive events. On
 * the leader it also starts the archiver, snapshot uploader and publisher and registers them on
 * that processor.
 *
 * <p>This step runs before the stream processor is created, so the listener and the publication are
 * registered on the processor before it can recover. The processor's {@code onRecovered} therefore
 * always reaches the leader components, which wait for it before doing anything.
 *
 * <p>Transitions close in reverse step order, so the stream processor is already closed when this
 * step unregisters and closes the leader components; no callbacks arrive while closing.
 */
public final class LogArchiveTransitionStep implements PartitionTransitionStep {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveTransitionStep.class);

  // steps are created per partition, so this belongs to exactly one partition
  private @Nullable LeaderComponents leaderComponents;

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (shouldInstallOnTransition(targetRole, context.getCurrentRole())
        || targetRole == Role.INACTIVE) {
      final var processor = context.getLogArchiveProcessor();
      if (processor != null) {
        processor.setListener(null);
        processor.setPublication(null);
        context.setLogArchiveProcessor(null);
      }
      return closeLeaderComponents(context);
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final LogArchiveCfg cfg = context.getBrokerCfg().getData().getBackup().getLogArchive();
    if (!cfg.isEnabled()) {
      return CompletableActorFuture.completed(null);
    }
    if (!shouldInstallOnTransition(targetRole, context.getCurrentRole())
        && (context.getLogArchiveProcessor() != null || targetRole == Role.INACTIVE)) {
      return CompletableActorFuture.completed(null);
    }

    final var processor =
        new LogArchiveRecordsProcessor(
            new LogArchiveProcessorConfig(
                context.getPartitionId(), cfg.getChunkSize(), cfg.getRootEvery()));
    context.setLogArchiveProcessor(processor);
    if (targetRole != Role.LEADER) {
      return CompletableActorFuture.completed(null);
    }
    return installLeaderComponents(context, term, cfg, processor);
  }

  @Override
  public String getName() {
    return "LogArchive";
  }

  private ActorFuture<Void> installLeaderComponents(
      final PartitionTransitionContext context,
      final long term,
      final LogArchiveCfg cfg,
      final LogArchiveRecordsProcessor processor) {
    final ActorFuture<Void> installed = context.getConcurrencyControl().createFuture();
    final int partitionId = context.getPartitionId();
    final LogArchiveStore store;
    try {
      store = LogArchiveStoreFactory.createStore(context.getBrokerCfg().getData().getBackup());
    } catch (final Exception e) {
      installed.completeExceptionally("Failed to create the log archive store", e);
      return installed;
    }

    final var logStream = context.getLogStream();
    final var partitionDirectory = context.getRaftPartition().dataDirectory().toPath();
    final var archiver =
        new LogArchiver(
            partitionId,
            term,
            store,
            logStream.newLogStreamWriter(),
            new LogArchiverRaftEntrySource(context.getRaftPartition().getServer().openReader()),
            () -> LogArchiverTimeline.resolve(partitionDirectory, cfg.getTimeline()),
            cfg.getCutMaxBytes().toBytes(),
            cfg.getCutInterval());
    final var uploader =
        new SnapshotUploader(
            partitionId,
            store,
            context.getPersistedSnapshotStore(),
            logStream.newLogStreamWriter(),
            cfg.getSnapshotUploadInterval());
    final var publisher =
        new LogArchivePublisher(partitionId, store, logStream.newLogStreamWriter());
    final var components = new LeaderComponents(store, archiver, uploader, publisher);
    leaderComponents = components;

    final ActorFuture<Void> submitted = context.getConcurrencyControl().createFuture();
    submitAll(context, components.actors(), 0, submitted);
    submitted.onComplete(
        (ignored, error) -> {
          if (error != null) {
            LOG.warn("Partition {}: failed to start the log archive", partitionId, error);
            installed.completeExceptionally(error);
            return;
          }
          // registered before this step completes, i.e. before the stream processor exists
          processor.setListener(new FanOutListener(List.of(archiver, uploader, publisher)));
          processor.setPublication(publisher);
          context.getLogStorage().addCommitListener(archiver);
          LOG.info("Partition {}: started the log archive in term {}", partitionId, term);
          installed.complete(null);
        },
        context.getConcurrencyControl());
    return installed;
  }

  private static void submitAll(
      final PartitionTransitionContext context,
      final List<Actor> actors,
      final int next,
      final ActorFuture<Void> done) {
    if (next == actors.size()) {
      done.complete(null);
      return;
    }
    context
        .getActorSchedulingService()
        .submitActor(actors.get(next), SchedulingHints.ioBound())
        .onComplete(
            (ignored, error) -> {
              if (error != null) {
                done.completeExceptionally(error);
              } else {
                submitAll(context, actors, next + 1, done);
              }
            },
            context.getConcurrencyControl());
  }

  private ActorFuture<Void> closeLeaderComponents(final PartitionTransitionContext context) {
    final var components = leaderComponents;
    if (components == null) {
      return CompletableActorFuture.completed(null);
    }
    leaderComponents = null;
    final var logStorage = context.getLogStorage();
    if (logStorage != null) {
      logStorage.removeCommitListener(components.archiver());
    }

    final ActorFuture<Void> closed = context.getConcurrencyControl().createFuture();
    closeAll(context, components.actors(), 0, closed);
    final ActorFuture<Void> storeClosed = context.getConcurrencyControl().createFuture();
    closed.onComplete(
        (ignored, error) -> {
          if (error != null) {
            LOG.warn("Failed to close the log archive components", error);
          }
          // the store is shared by all components, so it is closed after all of them
          components
              .store()
              .closeAsync()
              .whenComplete(
                  (nothing, storeError) -> {
                    if (storeError != null) {
                      LOG.warn("Failed to close the log archive store", storeError);
                    }
                    context.getConcurrencyControl().run(() -> storeClosed.complete(null));
                  });
        },
        context.getConcurrencyControl());
    return storeClosed;
  }

  private static void closeAll(
      final PartitionTransitionContext context,
      final List<Actor> actors,
      final int next,
      final ActorFuture<Void> done) {
    if (next == actors.size()) {
      done.complete(null);
      return;
    }
    actors
        .get(next)
        .closeAsync()
        .onComplete(
            (ignored, error) -> {
              if (error != null) {
                LOG.warn("Failed to close {}", actors.get(next).getName(), error);
              }
              closeAll(context, actors, next + 1, done);
            },
            context.getConcurrencyControl());
  }

  private static boolean shouldInstallOnTransition(final Role newRole, final Role currentRole) {
    return newRole == Role.LEADER
        || (newRole == Role.FOLLOWER && currentRole != Role.CANDIDATE)
        || (newRole == Role.CANDIDATE && currentRole != Role.FOLLOWER);
  }

  private record LeaderComponents(
      LogArchiveStore store,
      LogArchiver archiver,
      SnapshotUploader uploader,
      LogArchivePublisher publisher) {

    List<Actor> actors() {
      return List.of(archiver, uploader, publisher);
    }
  }

  /**
   * Forwards every notification to all leader components, in order. Each of them hops to its own
   * actor, so forwarding is cheap and keeps the processor's order per component.
   */
  static final class FanOutListener implements LogArchiveListener {

    private final List<LogArchiveListener> delegates;

    FanOutListener(final List<? extends LogArchiveListener> delegates) {
      this.delegates = new ArrayList<>(delegates);
    }

    @Override
    public void onTimelineStarted(final String timelineId) {
      delegates.forEach(d -> d.onTimelineStarted(timelineId));
    }

    @Override
    public void onLogArchived(final long archivedIndex, final long archivedPosition) {
      delegates.forEach(d -> d.onLogArchived(archivedIndex, archivedPosition));
    }

    @Override
    public void onArchiveLogRejected(final long archivedIndex) {
      delegates.forEach(d -> d.onArchiveLogRejected(archivedIndex));
    }

    @Override
    public void onSnapshotArchived(final String snapshotId) {
      delegates.forEach(d -> d.onSnapshotArchived(snapshotId));
    }

    @Override
    public void onSnapshotRejected(final String snapshotId) {
      delegates.forEach(d -> d.onSnapshotRejected(snapshotId));
    }

    @Override
    public void onIndexSealed(final long chunkNo) {
      delegates.forEach(d -> d.onIndexSealed(chunkNo));
    }

    @Override
    public void onPublished(final long publishedSeq) {
      delegates.forEach(d -> d.onPublished(publishedSeq));
    }

    @Override
    public void onCommandRejected(
        final LogArchiveIntent command, final RejectionType type, final String reason) {
      delegates.forEach(d -> d.onCommandRejected(command, type, reason));
    }

    @Override
    public void onRecovered(final LogArchiveMeta meta) {
      // each delegate gets its own copy, so none of them can observe another one's changes
      delegates.forEach(d -> d.onRecovered(meta.copy()));
    }

    @Override
    public void onNewestArchivedSnapshotRecovered(
        final String snapshotId, final long processedPosition) {
      delegates.forEach(d -> d.onNewestArchivedSnapshotRecovered(snapshotId, processedPosition));
    }
  }
}
