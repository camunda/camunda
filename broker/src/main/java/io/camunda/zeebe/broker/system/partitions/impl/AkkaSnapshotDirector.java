/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import io.atomix.raft.RaftCommittedEntryListener;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.camunda.zeebe.broker.system.partitions.SnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorMode;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import java.time.Duration;
import java.util.concurrent.Future;

public final class AkkaSnapshotDirector implements RaftCommittedEntryListener, SnapshotDirector {
  private final AkkaCompatActor compat;
  private final StreamProcessor streamProcessor;
  private final StreamProcessorMode streamProcessorMode;
  private final StateController stateController;
  private long commitPosition = -1;
  private final Behavior<SnapshotDirectorCommands> transitionToTransientSnapshot =
      Behaviors.receive(SnapshotDirectorCommands.class)
          .onMessage(OnCommit.class, this::updateCommitPosition)
          .onMessage(
              StartTransientSnapshot.class, (msg1) -> transientSnapshot(msg1.lastProcessedPosition))
          .build();
  private ActorContext<SnapshotDirectorCommands> ctx;

  public AkkaSnapshotDirector(
      final AkkaCompatActor compat,
      final StreamProcessor streamProcessor,
      final StreamProcessorMode streamProcessorMode,
      final StateController stateController) {
    this.compat = compat;
    this.streamProcessor = streamProcessor;
    this.streamProcessorMode = streamProcessorMode;
    this.stateController = stateController;
  }

  public Behavior<SnapshotDirectorCommands> create() {
    return Behaviors.setup(
        ctx ->
            Behaviors.withTimers(
                (timers) -> {
                  this.ctx = ctx;
                  timers.startTimerAtFixedRate(
                      "ScheduledSnapshotTimer", new StartSnapshot(), Duration.ofMinutes(5));
                  return Behaviors.receive(SnapshotDirectorCommands.class)
                      .onMessage(StartSnapshot.class, this::startingSnapshot)
                      .onMessage(OnCommit.class, this::updateCommitPosition)
                      .build();
                }));
  }

  private Behavior<SnapshotDirectorCommands> updateCommitPosition(final OnCommit msg) {
    commitPosition = msg.commitPosition;
    return Behaviors.same();
  }

  private Behavior<SnapshotDirectorCommands> startingSnapshot(final StartSnapshot msg) {
    return Behaviors.setup(
        (ctx) -> {
          compat.waitOnActor(
              streamProcessor::getLastWrittenPositionAsync,
              ctx.getSelf(),
              (lastProcessedPosition, error) -> {
                if (error != null) {
                  return new StartTransientSnapshot(lastProcessedPosition);
                } else {
                  // TODO: Handle error
                  return null;
                }
              });
          return transitionToTransientSnapshot;
        });
  }

  private Behavior<SnapshotDirectorCommands> transientSnapshot(final Long lastProcessedPos) {
    return Behaviors.setup(
        (ctx) -> {
          compat.waitOnActor(
              () -> stateController.takeTransientSnapshot(lastProcessedPos),
              ctx.getSelf(),
              (transientSnapshot, error) -> {
                if (error == null) {
                  return new WaitForLastWrittenPosition(transientSnapshot);
                } else {
                  // TODO: handle error
                  return null;
                }
              });
          return Behaviors.receive(SnapshotDirectorCommands.class)
              .onMessage(OnCommit.class, this::updateCommitPosition)
              .onMessage(
                  WaitForLastWrittenPosition.class,
                  (msg) -> getLastWrittenPosition(msg.transientSnapshot))
              .build();
        });
  }

  private Behavior<SnapshotDirectorCommands> getLastWrittenPosition(
      final TransientSnapshot transientSnapshot) {
    return Behaviors.setup(
        (ctx) -> {
          compat.waitOnActor(
              streamProcessor::getLastWrittenPositionAsync,
              ctx.getSelf(),
              (lastWrittenPosition, error) -> {
                if (error == null) {
                  return new WaitForCommitPosition(transientSnapshot, lastWrittenPosition);
                } else {
                  // TODO: handle error
                  return null;
                }
              });
          return Behaviors.receive(SnapshotDirectorCommands.class)
              .onMessage(OnCommit.class, this::updateCommitPosition)
              .onMessage(
                  WaitForCommitPosition.class,
                  (msg) ->
                      validateCommitPosition(
                          msg.transientSnapshot, msg.lastWrittenPosition, commitPosition))
              .build();
        });
  }

  private Behavior<SnapshotDirectorCommands> validateCommitPosition(
      final TransientSnapshot transientSnapshot,
      final long lastWrittenPosition,
      final long commitPosition) {
    this.commitPosition = commitPosition;
    if (streamProcessorMode == StreamProcessorMode.REPLAY || commitPosition >= lastWrittenPosition) {
      return Behaviors.setup(
          (ctx) -> {
            compat.waitOnActor(
                transientSnapshot::persist,
                ctx.getSelf(),
                (persisted, error) -> {
                  if (error == null) {
                    return new SnapshotPersisted(persisted);
                  } else {
                    // TODO: handle error
                    return null;
                  }
                });
            return Behaviors.receive(SnapshotDirectorCommands.class)
                .onMessage(SnapshotPersisted.class, (msg) -> create())
                .build();
          });
    } else {
      return Behaviors.receive(SnapshotDirectorCommands.class)
          .onMessage(
              OnCommit.class,
              (msg) ->
                  validateCommitPosition(
                      transientSnapshot, lastWrittenPosition, msg.commitPosition))
          .build();
    }
  }

  @Override
  public void onCommit(final IndexedRaftLogEntry indexedRaftLogEntry) {
    if (indexedRaftLogEntry.isApplicationEntry()) {
      ctx.getSelf().tell(new OnCommit(indexedRaftLogEntry.getApplicationEntry().highestPosition()));
    }
  }

  @Override
  public Future<PersistedSnapshot> forceSnapshot() {
    ctx.getSelf().tell(new StartSnapshot());
    return null;
  }

  @Override
  public void close() throws Exception {
    ctx.stop(ctx.getSelf());
  }

  record StartSnapshot() implements SnapshotDirectorCommands {}

  record OnCommit(long commitPosition) implements SnapshotDirectorCommands {}

  record StartTransientSnapshot(long lastProcessedPosition) implements SnapshotDirectorCommands {}

  record WaitForLastWrittenPosition(TransientSnapshot transientSnapshot)
      implements SnapshotDirectorCommands {}

  record WaitForCommitPosition(TransientSnapshot transientSnapshot, long lastWrittenPosition)
      implements SnapshotDirectorCommands {}

  record SnapshotPersisted(PersistedSnapshot persistedSnapshot)
      implements SnapshotDirectorCommands {}

  interface SnapshotDirectorCommands {}
}
