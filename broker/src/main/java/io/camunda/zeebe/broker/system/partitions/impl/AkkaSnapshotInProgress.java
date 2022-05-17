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
import io.camunda.zeebe.broker.system.partitions.impl.AkkaSnapshotDirector.SnapshotDirectorCommands;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaSnapshotDirector.WaitForCommitPosition;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStateController.PersistTransientSnapshot;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStateController.PersistedSnapshotReply;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStateController.StateControllerCommand;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStateController.StateControllerResponse;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStateController.TakeTransientSnapshot;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStateController.TransientSnapshotReply;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStreamProcessor.GetLastProcessedPosition;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStreamProcessor.LastProcessedPosition;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStreamProcessor.LastWrittenPosition;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStreamProcessor.StreamProcessorCommands;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStreamProcessor.StreamProcessorResponse;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import java.time.Duration;

public class AkkaSnapshotInProgress {
  private final ActorContext<SnapshotInProgressCommands> ctx;
  private final ActorRef<SnapshotResponse> replyTo;
  private final ActorRef<StreamProcessorCommands> streamProcessor;
  private final ActorRef<SnapshotDirectorCommands> snapshotDirector;
  private final ActorRef<StateControllerCommand> stateController;

  private AkkaSnapshotInProgress(final ActorContext<SnapshotInProgressCommands> ctx,
      final ActorRef<SnapshotResponse> replyTo,
      final ActorRef<StreamProcessorCommands> streamProcessor,
      final ActorRef<SnapshotDirectorCommands> snapshotDirector,
      final ActorRef<StateControllerCommand> stateController) {
    this.ctx = ctx;
    this.replyTo = replyTo;
    this.streamProcessor = streamProcessor;
    this.snapshotDirector = snapshotDirector;
    this.stateController = stateController;
  }

  public static Behavior<SnapshotInProgressCommands> create(
      final ActorRef<SnapshotResponse> replyTo,
      final ActorRef<StreamProcessorCommands> streamProcessor,
      final ActorRef<SnapshotDirectorCommands> snapshotDirector,
      final ActorRef<StateControllerCommand> stateController) {
    return Behaviors.setup((ctx) -> new AkkaSnapshotInProgress(ctx, replyTo, streamProcessor,
        snapshotDirector, stateController).getLastProcessedPosition());
  }

  private Behavior<SnapshotInProgressCommands> getLastProcessedPosition() {
    ctx.ask(StreamProcessorResponse.class, streamProcessor, Duration.ofSeconds(10),
        GetLastProcessedPosition::new, (response, error) -> {
          if (error != null) {
            throw new SnapshotFailure(error);
          } else if (response instanceof LastProcessedPosition lastProcessedPosition) {
            return new GotLastProcessedPosition(lastProcessedPosition.position());
          } else {
            throw new IllegalStateException();
          }
        });
    return Behaviors.receive(SnapshotInProgressCommands.class)
        .onMessage(GotLastProcessedPosition.class, this::takeTransientSnapshot)
        .build();
  }

  private Behavior<SnapshotInProgressCommands> takeTransientSnapshot(final GotLastProcessedPosition lastProcessed) {
    ctx.ask(StateControllerResponse.class, stateController, Duration.ofSeconds(10), (ref) -> new TakeTransientSnapshot(lastProcessed.position, ref), (response, error) -> {
      if (error != null) {
        throw new SnapshotFailure(error);
      } else if (response instanceof TransientSnapshotReply transientSnapshotTaken) {
        return new TransientSnapshotTaken(transientSnapshotTaken.transientSnapshot());
      } else {
        throw new IllegalStateException();
      }
    });
    return Behaviors.receive(SnapshotInProgressCommands.class)
        .onMessage(
            TransientSnapshotTaken.class,
            (msg) -> getLastWrittenPosition(msg.transientSnapshot))
        .build();
  }

  private Behavior<SnapshotInProgressCommands> getLastWrittenPosition(
      final TransientSnapshot transientSnapshot) {

    ctx.ask(StreamProcessorResponse.class, streamProcessor, Duration.ofSeconds(10),
        GetLastProcessedPosition::new, (response, error) -> {
          if (error != null) {
            throw new SnapshotFailure(error);
          } else if (response instanceof LastWrittenPosition lastWrittenPosition) {
            return new GotLastWrittenPosition(lastWrittenPosition.position());
          } else {
            throw new IllegalStateException();
          }
        });

    return Behaviors.receive(SnapshotInProgressCommands.class)
        .onMessage(
            GotLastWrittenPosition.class,
            (msg) -> waitForCommit(transientSnapshot, msg.position))
        .build();
  }

  private Behavior<SnapshotInProgressCommands> waitForCommit(
      final TransientSnapshot transientSnapshot,
      final long lastWrittenPosition) {
    snapshotDirector.tell(new WaitForCommitPosition(ctx.getSelf(), lastWrittenPosition));
    Behaviors.receive(SnapshotInProgressCommands.class)
        .onMessageEquals(new CommitPositionReached(lastWrittenPosition), () -> persist(transientSnapshot));
    return Behaviors.same();
  }

  private Behavior<SnapshotInProgressCommands> persist(final TransientSnapshot transientSnapshot) {
    ctx.ask(StateControllerResponse.class, stateController, Duration.ofSeconds(10),
        (ref) -> new PersistTransientSnapshot(ref, transientSnapshot), (result, error) -> {
      if (error != null) {
        throw new SnapshotFailure(error);
      } else if(result instanceof PersistedSnapshotReply reply){
        return new SnapshotPersisted(reply.persistedSnapshot());
      } else {
        throw new IllegalStateException();
      }
    });

    return Behaviors.receive(SnapshotInProgressCommands.class)
            .onMessage(SnapshotPersisted.class, this::finish).build();
  }

  private Behavior<SnapshotInProgressCommands> finish(final SnapshotPersisted msg) {
    replyTo.tell(new SnapshotSucceeded(msg.snapshot));
    return Behaviors.stopped();
  }


  record GotLastProcessedPosition(long position) implements SnapshotInProgressCommands {}

  record GotLastWrittenPosition(long position) implements SnapshotInProgressCommands {}

  record CommitPositionReached(long position) implements SnapshotInProgressCommands {}

  record TransientSnapshotTaken(TransientSnapshot transientSnapshot) implements
      SnapshotInProgressCommands {}

  record SnapshotPersisted(PersistedSnapshot snapshot) implements SnapshotInProgressCommands {}

  static final class SnapshotFailure extends RuntimeException {
    public SnapshotFailure(final Throwable error) {
      super(error);
    }
  }

  record SnapshotSkipped() implements SnapshotResponse {}
  record SnapshotSucceeded(PersistedSnapshot persistedSnapshot) implements SnapshotResponse {}

  interface SnapshotInProgressCommands {}

  interface SnapshotResponse {}

}
