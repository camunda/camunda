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

public class StreamProcessorAkkaActorWrapper {

  public Behavior<StreamProcessorCommands> start() {
    return Behaviors.receive(StreamProcessorCommands.class)
        .onMessage(GetLastProcessedPosition.class, msg ->  {
          msg.replyTo().tell(new GetLastProcessedPositionResponse(1L));
          return Behaviors.same();
        })
        .onMessage(GetLastWrittenPosition.class, msg ->  {
          msg.replyTo().tell(new GetLastWrittenPositionResponse(1L));
          return Behaviors.same();
        }).build();
  }

  record GetLastWrittenPositionResponse(long position) implements StreamProcessorResponse {}
  record GetLastProcessedPositionResponse(long position) implements StreamProcessorResponse {}

  record GetLastWrittenPosition(ActorRef<StreamProcessorResponse> replyTo) implements StreamProcessorCommands {}
  record GetLastProcessedPosition(ActorRef<StreamProcessorResponse> replyTo) implements StreamProcessorCommands {}

  public interface StreamProcessorCommands {}
  public interface StreamProcessorResponse {}
}
