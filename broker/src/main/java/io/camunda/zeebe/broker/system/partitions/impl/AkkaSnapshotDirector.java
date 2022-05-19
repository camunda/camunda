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
import akka.actor.typed.ChildFailed;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import io.atomix.raft.RaftCommittedEntryListener;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.camunda.zeebe.broker.system.partitions.SnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaSnapshotInProgress.CommitPositionReached;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaSnapshotInProgress.SnapshotInProgressCommands;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaSnapshotInProgress.SnapshotResponse;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaSnapshotInProgress.SnapshotSkipped;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaSnapshotInProgress.SnapshotSucceeded;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStateController.StateControllerCommand;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStreamProcessor.StreamProcessorCommands;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorMode;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public final class AkkaSnapshotDirector implements RaftCommittedEntryListener, SnapshotDirector {
  private volatile long commitPosition = -1;
  private ActorContext<SnapshotDirectorCommands> ctx;

  private ActorRef<StreamProcessorCommands> streamProcessorActor;
  private ActorRef<StateControllerCommand> stateControllerActor;
  private final StreamProcessorMode streamProcessorMode;

  public AkkaSnapshotDirector(
      final StreamProcessorMode mode) {
    streamProcessorMode = mode;
  }

  public Behavior<SnapshotDirectorCommands> create(
      final AkkaCompatActor compat,
      final StreamProcessor streamProcessor,
      final StateController stateController) {
    return Behaviors.setup(
        ctx ->
            Behaviors.withTimers(
                (timers) -> {
                  timers.startTimerAtFixedRate(
                      "ScheduledSnapshotTimer",
                      new StartSnapshot(new CompletableFuture<>()),
                      Duration.ofSeconds(15));
                  this.ctx = ctx;
                  streamProcessorActor =
                      ctx.spawn(
                          new AkkaStreamProcessor(compat, streamProcessor).create(),
                          "StreamProcessor");
                  stateControllerActor =
                      ctx.spawn(
                          new AkkaStateController(compat, stateController).create(),
                          "StateController");
                  return idle();
                }));
  }

  public Behavior<SnapshotDirectorCommands> idle() {
    return Behaviors.receive(SnapshotDirectorCommands.class)
        .onMessage(StartSnapshot.class, this::startingSnapshot)
        .onMessage(OnCommit.class, this::updateCommitPosition)
        .build();
  }

  private Behavior<SnapshotDirectorCommands> updateCommitPosition(final OnCommit msg) {
    ctx.getLog().info("Updating commit position: {}", msg);
    commitPosition = msg.commitPosition;
    return Behaviors.same();
  }

  private Behavior<SnapshotDirectorCommands> startingSnapshot(final StartSnapshot msg) {
    final var responseAdapter =
        ctx.messageAdapter(
            SnapshotResponse.class,
            (response) -> {
              if (response instanceof SnapshotSkipped) {
                return new SkipSnapshot();
              } else if (response instanceof SnapshotSucceeded withSnapshot) {
                return new CompleteSnapshot(withSnapshot.persistedSnapshot());
              }
              return null;
            });
    final var inProgress =
        ctx.spawn(
            AkkaSnapshotInProgress.create(
                responseAdapter,
                streamProcessorMode,
                streamProcessorActor,
                ctx.getSelf(),
                stateControllerActor),
            "SnapshotInProgress");
    ctx.watch(inProgress);
    return waitingForSnapshot(inProgress, msg.future);
  }

  private Behavior<SnapshotDirectorCommands> waitingForSnapshot(
      final ActorRef<SnapshotInProgressCommands> inProgress, final CompletableFuture<PersistedSnapshot> future) {
    return Behaviors.receive(SnapshotDirectorCommands.class)
        .onMessage(
            CompleteSnapshot.class,
            (msg) -> {
              ctx.getLog().info("Completed snapshot!");
              ctx.unwatch(inProgress);
              future.complete(msg.persistedSnapshot);
              return idle();
            })
        .onMessage(
            WaitForCommitPosition.class,
            (msg) -> {
              if (streamProcessorMode == StreamProcessorMode.REPLAY) {
                msg.replyTo.tell(new CommitPositionReached(msg.commitPosition));
                return Behaviors.same();
              } else {
                return waitingForCommit(inProgress, future, msg);
              }
            }
        )
        .onMessage(
            SkipSnapshot.class,
            (msg) -> {
              ctx.getLog().info("Skipped snapshot");
              ctx.unwatch(inProgress);
              future.complete(null);
              return idle();
            })
        .onSignal(
            ChildFailed.class,
            (failed) -> {
              future.completeExceptionally(failed.cause());
              return idle();
            })
        .build();
  }

  private Behavior<SnapshotDirectorCommands> waitingForCommit(
      final ActorRef<SnapshotInProgressCommands> inProgress, final CompletableFuture<PersistedSnapshot> future, final WaitForCommitPosition wait) {
    ctx.getLog().info("Waiting for commit {} >= {}", commitPosition, wait.commitPosition);
    if (commitPosition >= wait.commitPosition) {
      ctx.getLog().info("Reached commit position,  notifying in progress ");
      wait.replyTo.tell(new CommitPositionReached(commitPosition));
      return waitingForSnapshot(inProgress, future);
    }
    return Behaviors.receive(SnapshotDirectorCommands.class)
        .onMessage(OnCommit.class, (commit) -> {
          commitPosition = commit.commitPosition;
          return waitingForCommit(inProgress, future, wait);
        })
        .build();
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
    final var f = new CompletableFuture<PersistedSnapshot>();
    ctx.getSelf().tell(new StartSnapshot(f));
    return null;
  }

  @Override
  public void close() throws Exception {
    ctx.getSelf().tell(new Shutdown());
    ctx.stop(ctx.getSelf());
  }

  record StartSnapshot(CompletableFuture<PersistedSnapshot> future)
      implements SnapshotDirectorCommands {}

  record CompleteSnapshot(PersistedSnapshot persistedSnapshot)
      implements SnapshotDirectorCommands {}

  record SkipSnapshot() implements SnapshotDirectorCommands {}

  record OnCommit(long commitPosition) implements SnapshotDirectorCommands {}

  record WaitForCommitPosition(ActorRef<SnapshotInProgressCommands> replyTo, long commitPosition)
      implements SnapshotDirectorCommands {}

  record Shutdown() implements SnapshotDirectorCommands {}

  public interface SnapshotDirectorCommands {}
}
