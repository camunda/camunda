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
import akka.actor.typed.javadsl.Behaviors;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.TransientSnapshot;

public class AkkaStateController {
  final AkkaCompatActor compat;
  final StateController stateController;

  private AkkaStateController(final AkkaCompatActor compat, final StateController stateController) {
    this.compat = compat;
    this.stateController = stateController;
  }

  public Behavior<StateControllerCommand> create() {
    return Behaviors.setup((ctx) -> Behaviors.receive(StateControllerCommand.class)
        .onMessage(TakeTransientSnapshot.class, this::takeTransientSnapshot)
        .onMessage(PersistTransientSnapshot.class, this::persistTransientSnapshot)
        .build());
  }

  private Behavior<StateControllerCommand> takeTransientSnapshot(final TakeTransientSnapshot msg) {
    return compat.onActor(
        () -> stateController.takeTransientSnapshot(msg.processedPosition),
        InternalTookTransientSnapshot::new,
        InternalFailure::new,
        Behaviors.receive(StateControllerCommand.class)
            .onMessage(InternalTookTransientSnapshot.class, (took) -> finishTransientSnapshot(msg.replyTo, took))
            .onMessage(InternalFailure.class, (failure) -> failTransientSnapshot(msg.replyTo, failure))
            .build());
  }

  private Behavior<StateControllerCommand> persistTransientSnapshot(final PersistTransientSnapshot msg) {
    return compat.onActor(
        msg.transientSnapshot::persist,
        InternalPersistedTransientSnapshot::new,
        InternalFailure::new,
        Behaviors.receive(StateControllerCommand.class)
            .onMessage(InternalPersistedTransientSnapshot.class, (p) -> finishPersistingSnapshot(msg.replyTo, p))
            .onMessage(InternalFailure.class, (f) -> failPersistingSnapshot(msg.replyTo, f))
            .build()
    );
  }

  private Behavior<StateControllerCommand> failPersistingSnapshot(final ActorRef<StateControllerResponse> replyTo, final InternalFailure f) {
    replyTo.tell(new PersistedSnapshotFailed(f.error));
    return create();
  }

  private Behavior<StateControllerCommand> finishPersistingSnapshot(final ActorRef<StateControllerResponse> replyTo, final InternalPersistedTransientSnapshot p) {
    replyTo.tell(new PersistedSnapshotReply(p.persistedSnapshot));
    return create();
  }


  private Behavior<StateControllerCommand> failTransientSnapshot(
      final ActorRef<StateControllerResponse> replyTo, final InternalFailure failure) {
    replyTo.tell(new TransientSnapshotFailed(failure.error));
    return create();
  }

  private Behavior<StateControllerCommand> finishTransientSnapshot(final ActorRef<StateControllerResponse> replyTo, final InternalTookTransientSnapshot msg) {
    if (msg.transientSnapshot == null) {
      replyTo.tell(new TransientSnapshotSkipped());
    } else {
      replyTo.tell(new TransientSnapshotReply(msg.transientSnapshot));
    }
    return create();
  }

  record TakeTransientSnapshot(long processedPosition, ActorRef<StateControllerResponse> replyTo) implements StateControllerCommand {}
  record PersistTransientSnapshot(ActorRef<StateControllerResponse> replyTo,
                                  TransientSnapshot transientSnapshot) implements StateControllerCommand {}

  record InternalTookTransientSnapshot(TransientSnapshot transientSnapshot) implements StateControllerCommand {}
  record InternalPersistedTransientSnapshot(PersistedSnapshot persistedSnapshot) implements StateControllerCommand {}

  record InternalFailure(Throwable error) implements StateControllerCommand {}

  record TransientSnapshotSkipped() implements StateControllerResponse {}
  record TransientSnapshotFailed(Throwable error) implements StateControllerResponse {}
  record PersistedSnapshotFailed(Throwable error) implements StateControllerResponse {}

  record PersistedSnapshotReply(PersistedSnapshot persistedSnapshot) implements StateControllerResponse {}
  record TransientSnapshotReply(TransientSnapshot transientSnapshot) implements StateControllerResponse {}
  interface StateControllerCommand {}
  interface StateControllerResponse {}

}
