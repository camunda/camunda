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
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;

public class AkkaStreamProcessor {
  final AkkaCompatActor compat;
  final StreamProcessor streamProcessor;

  public AkkaStreamProcessor(final AkkaCompatActor compat, final StreamProcessor streamProcessor) {
    this.compat = compat;
    this.streamProcessor = streamProcessor;
  }


  public Behavior<StreamProcessorCommands> start() {
    return Behaviors.receive(StreamProcessorCommands.class)
        .onMessage(GetLastProcessedPosition.class, msg ->  {
          msg.replyTo().tell(new LastProcessedPosition(1L));
          return Behaviors.same();
        })
        .onMessage(GetLastWrittenPosition.class, msg ->  {
          msg.replyTo().tell(new LastWrittenPosition(1L));
          return Behaviors.same();
        }).build();
  }

  record LastWrittenPosition(long position) implements StreamProcessorResponse {}
  record LastProcessedPosition(long position) implements StreamProcessorResponse {}

  record GetLastWrittenPosition(ActorRef<StreamProcessorResponse> replyTo) implements StreamProcessorCommands {}
  record GetLastProcessedPosition(ActorRef<StreamProcessorResponse> replyTo) implements StreamProcessorCommands {}

  public interface StreamProcessorCommands {}
  public interface StreamProcessorResponse {}
}
