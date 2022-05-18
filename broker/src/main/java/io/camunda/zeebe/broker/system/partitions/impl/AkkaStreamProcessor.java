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
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;

public class AkkaStreamProcessor {
  ActorContext<StreamProcessorCommands> ctx;
  final AkkaCompatActor compat;
  final StreamProcessor streamProcessor;

  public AkkaStreamProcessor(final AkkaCompatActor compat, final StreamProcessor streamProcessor) {
    this.compat = compat;
    this.streamProcessor = streamProcessor;
  }

  public Behavior<StreamProcessorCommands> create() {
    return Behaviors.setup((ctx) -> {
      this.ctx = ctx;
      return Behaviors.receive(StreamProcessorCommands.class)
          .onMessage(
              GetLastProcessedPosition.class,
              msg -> {
                 compat.onActor(
                     ctx,
                    streamProcessor::getLastProcessedPositionAsync,
                    GotLastProcessedPosition::new,
                    StreamProcessorCallFailed::new);
                return Behaviors.receive(StreamProcessorCommands.class)
                    .onMessage(
                        GotLastProcessedPosition.class,
                        (lastProcessed) -> {
                          ctx.getLog().info("Got last processed position: {}", lastProcessed);
                          msg.replyTo.tell(new LastProcessedPosition(lastProcessed.position));
                          return create();
                        })
                    .onMessage(
                        StreamProcessorCallFailed.class,
                        (failure) -> {
                          msg.replyTo.tell(new Failure(failure.failure));
                          return create();
                        })
                    .build();
              })
          .onMessage(
              GetLastWrittenPosition.class,
              msg -> {
                ctx.getLog().info("Getting last written position");
                compat.onActor(ctx,
                    streamProcessor::getLastWrittenPositionAsync,
                    GotLastWrittenPosition::new,
                    StreamProcessorCallFailed::new);
                return Behaviors.receive(StreamProcessorCommands.class)
                    .onMessage(
                        GotLastWrittenPosition.class,
                        (lastWritten) -> {
                          ctx.getLog().info("Got last written position {}", lastWritten);
                          msg.replyTo.tell(new LastWrittenPosition(lastWritten.position));
                          return create();
                        })
                    .onMessage(
                        StreamProcessorCallFailed.class,
                        (failure) -> {
                          msg.replyTo.tell(new Failure(failure.failure));
                          return create();
                        })
                    .build();
              })
          .build();
    });
  }

  record LastWrittenPosition(long position) implements StreamProcessorResponse {}

  record LastProcessedPosition(long position) implements StreamProcessorResponse {}

  record Failure(Throwable failure) implements StreamProcessorResponse {}

  record GetLastWrittenPosition(ActorRef<StreamProcessorResponse> replyTo)
      implements StreamProcessorCommands {}

  record GetLastProcessedPosition(ActorRef<StreamProcessorResponse> replyTo)
      implements StreamProcessorCommands {}

  record GotLastProcessedPosition(long position) implements StreamProcessorCommands {}

  record GotLastWrittenPosition(long position) implements StreamProcessorCommands {}

  record StreamProcessorCallFailed(Throwable failure) implements StreamProcessorCommands {}

  public interface StreamProcessorCommands {}

  public interface StreamProcessorResponse {}
}
