/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import io.atomix.raft.RaftCommittedEntryListener;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.camunda.zeebe.broker.system.partitions.SnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.broker.system.partitions.impl.StreamProcessorAkkaActorWrapper.GetLastProcessedPosition;
import io.camunda.zeebe.broker.system.partitions.impl.StreamProcessorAkkaActorWrapper.GetLastProcessedPositionResponse;
import io.camunda.zeebe.broker.system.partitions.impl.StreamProcessorAkkaActorWrapper.StreamProcessorCommands;
import io.camunda.zeebe.broker.system.partitions.impl.StreamProcessorAkkaActorWrapper.StreamProcessorResponse;
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
  private ActorContext<SnapshotDirectorCommands> ctx;

  private ActorRef<StreamProcessorCommands> streamProcessorActor;

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
                      "ScheduledSnapshotTimer", new StartSnapshot(), Duration.ofSeconds(15));
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
    final var adaptedCtx = ctx.messageAdapter(StreamProcessorResponse.class, (response) -> {
      if (response instanceof GetLastProcessedPositionResponse r) {
        return new StartTransientSnapshot(r.position());
      }
      return null;
    });

    streamProcessorActor.tell(new GetLastProcessedPosition(adaptedCtx));
    return Behaviors.receive(SnapshotDirectorCommands.class)
        .onMessage(OnCommit.class, this::updateCommitPosition)
        .onMessage(
            StartTransientSnapshot.class,
            (msg1) -> transientSnapshot(msg1.lastProcessedPosition))
        .onMessage(
            FailSnapshot.class,
            (failure) -> {
              ctx.getLog().error("Failed taking snapshot", failure.e);
              return create();
            })
        .build();
  }

  private Behavior<SnapshotDirectorCommands> transientSnapshot(final Long lastProcessedPos) {
    return compat.onActor(
        () -> stateController.takeTransientSnapshot(lastProcessedPos),
        TransientSnapshotTaken::new,
        FailSnapshot::new,
        Behaviors.receive(SnapshotDirectorCommands.class)
            .onMessage(OnCommit.class, this::updateCommitPosition)
            .onMessage(
                TransientSnapshotTaken.class,
                (msg) -> getLastWrittenPosition(msg.transientSnapshot))
            .onMessage(
                FailSnapshot.class,
                (msg) -> {
                  ctx.getLog().error("Failed to take snapshot", msg.e);
                  return create();
                })
            .onMessage(
                SkipSnapshot.class,
                (msg) -> {
                  ctx.getLog().info("Skipping snapshot");
                  return create();
                })
            .build());
  }

  private Behavior<SnapshotDirectorCommands> getLastWrittenPosition(
      final TransientSnapshot transientSnapshot) {
    return compat.onActor(
        streamProcessor::getLastWrittenPositionAsync,
        (pos) -> new GotLastWrittenPosition(transientSnapshot, pos),
        FailSnapshot::new,
        Behaviors.receive(SnapshotDirectorCommands.class)
            .onMessage(OnCommit.class, this::updateCommitPosition)
            .onMessage(
                GotLastWrittenPosition.class,
                (msg) ->
                    validateCommitPosition(
                        msg.transientSnapshot, msg.lastWrittenPosition, commitPosition))
            .build());
  }

  private Behavior<SnapshotDirectorCommands> validateCommitPosition(
      final TransientSnapshot transientSnapshot,
      final long lastWrittenPosition,
      final long commitPosition) {
    this.commitPosition = commitPosition;
    if (commitPosition >= lastWrittenPosition) {
      return compat.onActor(
          transientSnapshot::persist,
          SnapshotPersisted::new,
          FailSnapshot::new,
          Behaviors.receive(SnapshotDirectorCommands.class)
              .onMessage(SnapshotPersisted.class, (msg) -> create())
              .build());
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
      ctx.getLog()
          .trace(
              "Notified about new commit position: {}",
              indexedRaftLogEntry.getApplicationEntry().highestPosition());
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

  record SkipSnapshot() implements SnapshotDirectorCommands {}

  record FailSnapshot(Throwable e) implements SnapshotDirectorCommands {}

  record OnCommit(long commitPosition) implements SnapshotDirectorCommands {}

  record StartTransientSnapshot(long lastProcessedPosition) implements SnapshotDirectorCommands {}

  record TransientSnapshotTaken(TransientSnapshot transientSnapshot)
      implements SnapshotDirectorCommands {}

  record GotLastWrittenPosition(TransientSnapshot transientSnapshot, long lastWrittenPosition)
      implements SnapshotDirectorCommands {}

  record SnapshotPersisted(PersistedSnapshot persistedSnapshot)
      implements SnapshotDirectorCommands {}

  record State(long commitPosition) {}

  public interface SnapshotDirectorCommands extends StreamProcessorResponse {}
}
