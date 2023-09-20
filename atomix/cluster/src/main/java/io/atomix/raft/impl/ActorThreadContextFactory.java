/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.impl;

import io.atomix.raft.RaftThreadContextFactory;
import io.atomix.utils.concurrent.ActorThreadContext;
import io.atomix.utils.concurrent.ThreadContext;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

public class ActorThreadContextFactory implements RaftThreadContextFactory {
  private final ActorSchedulingService actorSchedulingService;

  public ActorThreadContextFactory(final ActorSchedulingService actorSchedulingService) {
    this.actorSchedulingService = actorSchedulingService;
  }

  @Override
  public ThreadContext createContext(
      final ThreadFactory factory, final Consumer<Throwable> unCaughtExceptionHandler) {
    final var actor = new ActorThreadContext.ThreadContextActor();
    actorSchedulingService.submitActor(actor);
    return new ActorThreadContext(actor);
  }
}
