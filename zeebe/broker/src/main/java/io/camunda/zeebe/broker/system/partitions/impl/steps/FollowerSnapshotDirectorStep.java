/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.zeebe.broker.logstreams.state.DbPositionSupplier;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.AtomixRecordEntrySupplierImpl;
import io.camunda.zeebe.broker.system.partitions.impl.FollowerSnapshotDirector;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Runs a {@link FollowerSnapshotDirector} while the partition isn't the leader, so a follower can
 * compact its log while the partition's state lives in a relational database. The leader takes its
 * snapshots with the usual snapshot director instead.
 *
 * <p>Unlike the other steps, it keeps the director itself: nothing else on the partition needs it.
 * The director keeps running across follower terms, because it only reads the shared state and this
 * follower's committed log, which don't change with the term.
 */
@NullMarked
public final class FollowerSnapshotDirectorStep implements PartitionTransitionStep {

  private final Supplier<Optional<ZeebeDb<ZbColumnFamilies>>> sharedState;
  private final ConstructableSnapshotStore snapshotStore;
  private @Nullable FollowerSnapshotDirector director;

  /**
   * @param sharedState opens the partition's shared state for reading, or returns empty if the
   *     partition has no state yet
   * @param snapshotStore the partition's snapshot store
   */
  public FollowerSnapshotDirectorStep(
      final Supplier<Optional<ZeebeDb<ZbColumnFamilies>>> sharedState,
      final ConstructableSnapshotStore snapshotStore) {
    this.sharedState = sharedState;
    this.snapshotStore = snapshotStore;
  }

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final var runningDirector = director;
    if (runningDirector == null || !stopsDirector(targetRole)) {
      return CompletableActorFuture.completed();
    }

    context.getComponentHealthMonitor().removeComponent(runningDirector);
    final var closed = runningDirector.closeAsync();
    closed.onComplete(
        (ok, error) -> {
          if (error == null) {
            director = null;
          }
        });
    return closed;
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (director != null || stopsDirector(targetRole)) {
      return CompletableActorFuture.completed();
    }

    final var server = context.getRaftPartition().getServer();
    final var dataCfg = context.getBrokerCfg().getData();
    final var continuousBackup = dataCfg.getBackup().isContinuous();
    final var newDirector =
        new FollowerSnapshotDirector(
            context.partitionId(),
            sharedState,
            () -> lastCommittedPosition(server),
            new AtomixRecordEntrySupplierImpl(server),
            snapshotStore,
            server::flushLog,
            db -> new DbPositionSupplier(db, continuousBackup),
            dataCfg.getSnapshotPeriod());
    return context
        .getActorSchedulingService()
        .submitActor(newDirector, SchedulingHints.ioBound())
        .thenAccept(
            ignored -> {
              director = newDirector;
              context.getComponentHealthMonitor().registerComponent(newDirector);
            });
  }

  @Override
  public String getName() {
    return "FollowerSnapshotDirector";
  }

  private static boolean stopsDirector(final Role targetRole) {
    return targetRole == Role.LEADER || targetRole == Role.INACTIVE;
  }

  /**
   * @return the position of the last record in the committed part of the log, or -1 if it holds no
   *     records
   */
  private static long lastCommittedPosition(final RaftPartitionServer server) {
    try (final var reader = server.openReader()) {
      reader.seekToAsqn(Long.MAX_VALUE);
      if (reader.hasNext()) {
        final var entry = reader.next();
        if (entry.isApplicationEntry()) {
          return entry.getApplicationEntry().highestPosition();
        }
      }
      return -1;
    }
  }
}
