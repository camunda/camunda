/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.utils.concurrent;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorThread;
import java.time.Duration;

public class ActorThreadContext implements ThreadContext {
  private final ThreadContextActor actor;

  public ActorThreadContext(final ThreadContextActor actor) {
    this.actor = actor;
  }

  @Override
  public void checkThread() {
    actor.checkThread();
  }

  @Override
  public Scheduled schedule(
      final Duration initialDelay, final Duration interval, final Runnable callback) {
    final var scheduled = new ScheduledTask(interval, this, callback);
    actor.schedule(initialDelay, scheduled);
    return scheduled;
  }

  @Override
  public void execute(final Runnable runnable) {
    actor.execute(runnable);
  }

  public static final class ThreadContextActor extends Actor {
    void execute(final Runnable runnable) {
      actor.submit(runnable);
    }

    public void checkThread() {
      final var thread = ActorThread.ensureCalledFromActorThread("checkThread");
      if (thread.getCurrentJob().getActor() != this) {
        throw new IllegalStateException("Not called from actor thread");
      }
    }
  }

  private static class ScheduledTask implements Scheduled, Runnable {
    private final ActorThreadContext context;
    private final Duration interval;
    private final Runnable callback;
    private volatile boolean cancelled;

    private ScheduledTask(
        final Duration interval, final ActorThreadContext context, final Runnable callback) {
      this.context = context;
      this.interval = interval;
      this.callback = callback;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }

    @Override
    public boolean isDone() {
      return false;
    }

    @Override
    public void run() {
      if (cancelled) {
        return;
      }
      callback.run();
      context.actor.schedule(interval, this);
    }
  }
}
