/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import io.camunda.zeebe.engine.processing.streamprocessor.AkkaStreamProcessor.Command;
import io.camunda.zeebe.engine.processing.streamprocessor.AkkaStreamProcessor.RecordAvailable;

public class AkkaStreamReplaying {
  AkkaStreamProcessor streamProcessor;
  ActorContext<Command> ctx;

  public Behavior<Command> startReplay() {
    // already recovered here
    return Behaviors.setup(
        (ctx) -> {
          this.ctx = ctx;
          return replayNextEvent();
        });
  }

  private Behavior<Command> replayNextEvent() {
    // read event from logstream
    final var hasEvent = false;
    final var leaderRecovery = true;
    final StreamProcessorMode mode = StreamProcessorMode.PROCESSING;
    if (hasEvent) {
      // do ReplayStateMachine::replayEvent
      ctx.getSelf().tell(new ProcessNextEvent());
    } else {
      // this is for the leader recovery
      if (mode == StreamProcessorMode.PROCESSING) {
        // ctx.getSelf().tell(new ReplayCompleted());
        return streamProcessor.onReplayCompleted();
      } else {
        return streamProcessor
            .common()
            .onMessage(RecordAvailable.class, (msg) -> replayNextEvent())
            .build();
      }
    }
    // do ReplayStateMachine::replayEvent
    ctx.getSelf().tell(new ProcessNextEvent());
    return streamProcessor
        .common()
        .onMessage(ProcessNextEvent.class, (msg) -> replayNextEvent())
        .build();
  }

  record ProcessNextEvent() implements Command {}

  record ReplayCompleted() implements Command {}
  ;
}
