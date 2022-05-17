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
import io.camunda.zeebe.broker.system.partitions.impl.AkkaSnapshotInProgress.SnapshotInProgressCommands;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStateController.StateControllerCommand;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStreamProcessor.StreamProcessorCommands;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorMode;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import java.time.Duration;
import java.util.concurrent.Future;

public final class AkkaSnapshotDirector implements RaftCommittedEntryListener, SnapshotDirector {
  private final AkkaCompatActor compat;
  private final StreamProcessorMode streamProcessorMode;
  private final StateController stateController;
  private long commitPosition = -1;
  private ActorContext<SnapshotDirectorCommands> ctx;

  private final ActorRef<StreamProcessorCommands> streamProcessorActor;
  private final ActorRef<StateControllerCommand> stateControllerActor;

  public AkkaSnapshotDirector(
      final AkkaCompatActor compat,
      final ActorRef<StreamProcessorCommands> streamProcessorActor,
      final ActorRef<StateControllerCommand> stateControllerActor,
      final StreamProcessorMode streamProcessorMode,
      final StateController stateController) {
    this.compat = compat;
    this.streamProcessorActor = streamProcessorActor;
    this.stateControllerActor = stateControllerActor;
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
    ctx.spawn(AkkaSnapshotInProgress.create(streamProcessorActor, ctx.getSelf(), stateControllerActor), "SnapshotInProgress");
    return Behaviors.same();
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

  record OnCommit(long commitPosition) implements SnapshotDirectorCommands {}

  record WaitForCommitPosition(ActorRef<SnapshotInProgressCommands> replyTo, long commitPosition) implements SnapshotDirectorCommands {}

  public interface SnapshotDirectorCommands {}
  public interface SnapshotDirectorResponse {}
}
