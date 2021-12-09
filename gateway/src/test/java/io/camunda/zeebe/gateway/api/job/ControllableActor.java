/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.job;

import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.ScheduledTimer;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ControllableActor extends Actor {

  protected static final int RETURN_CONTROL_UNDEFINED = -1;
  protected static final int RETURN_CONTROL_PRE_ACTION_EXECUTION = 0;
  protected static final int RETURN_CONTROL_POST_ACTION_EXECUTION = 1;
  protected static final int RETURN_CONTROL_BEFORE_NEXT_ACTION = 3;

  protected final String name;
  protected final Consumer<ActorControl> onActorStartedHandler;
  protected final ControllableActorControl actorControl;

  protected volatile boolean skipInitialJobs = true;
  protected volatile boolean ready = false;

  protected volatile int returnControlToTestThreadOn = RETURN_CONTROL_UNDEFINED;

  public ControllableActor() {
    this(null);
  }

  public ControllableActor(final String name) {
    this(name, null);
  }

  public ControllableActor(final String name, final Consumer<ActorControl> onActorStartedHandler) {
    this.name = name;
    this.onActorStartedHandler = onActorStartedHandler;
    actorControl = new ControllableActorControl(this, actor);
  }

  @Override
  public String getName() {
    if (name != null) {
      return name;
    } else {
      return super.getName();
    }
  }

  @Override
  protected void onActorStarted() {
    if (onActorStartedHandler != null) {
      onActorStartedHandler.accept(actorControl);
    }
  }

  @Override
  public void run(Runnable action) {
    actorControl.run(action);
  }

  @Override
  public <T> void runOnCompletion(ActorFuture<T> future, BiConsumer<T, Throwable> callback) {
    actorControl.runOnCompletion(future, callback);
  }

  public synchronized void submitControlPointAndContinueUntilItsExecution() {
    actorControl.submit(() -> returnControlToTestThreadAndWait());
  }

  public synchronized void continueAndWaitUntilDone() {
    returnControlToTestThreadOn = RETURN_CONTROL_UNDEFINED;
    notify();
  }

  public synchronized void continueAndWaitBeforeNextActorJob() {
    try {
      returnControlToTestThreadOn = RETURN_CONTROL_BEFORE_NEXT_ACTION;
      notify();
      wait();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      returnControlToTestThreadOn = RETURN_CONTROL_UNDEFINED;
    }
  }

  public synchronized void continueAndWaitBeforeExecutingNextAction() {
    try {
      returnControlToTestThreadOn = RETURN_CONTROL_PRE_ACTION_EXECUTION;
      notify();
      wait();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      returnControlToTestThreadOn = RETURN_CONTROL_UNDEFINED;
    }
  }

  public synchronized void continueAndWaitAfterExecutingNextAction() {
    try {
      returnControlToTestThreadOn = RETURN_CONTROL_POST_ACTION_EXECUTION;
      notify();
      wait();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      returnControlToTestThreadOn = RETURN_CONTROL_UNDEFINED;
    }
  }

  public synchronized void returnControlToTestThreadAndWait() {
    try {
      notify();
      wait();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  class ControllableActorControl extends ActorControl {

    final ActorControl delegate;

    public ControllableActorControl(Actor actor, ActorControl actorControl) {
      super(actor);
      delegate = actorControl;
    }

    @Override
    public void run(Runnable action) {
      delegate.run(new ControlledRunnable(action));
    }

    @Override
    public ScheduledTimer runDelayed(Duration delay, Runnable runnable) {
      return delegate.runDelayed(delay, new ControlledRunnable(runnable));
    }

    @Override
    public void submit(Runnable action) {
      delegate.submit(new ControlledRunnable(action));
    }
  }

  class ControlledRunnable implements Runnable {

    final Runnable action;
    final List<ActorJobPhase> phases;

    public ControlledRunnable(Runnable action) {
      this.action = action;
      phases = new ArrayList<>();
      phases.add(new PreActorJobPhase());
      phases.add(new ExecutionActorJobPhase());
      phases.add(new PostActorJobPhase());
    }

    @Override
    public void run() {
      phases.forEach((p) -> p.doWork(action));
    }
  }

  interface ActorJobPhase {
    void doWork(final Runnable action);
  }

  class PreActorJobPhase implements ActorJobPhase {
    @Override
    public void doWork(final Runnable action) {
      if (returnControlToTestThreadOn == RETURN_CONTROL_PRE_ACTION_EXECUTION) {
        returnControlToTestThreadAndWait();
      }
    }
  }

  class ExecutionActorJobPhase implements ActorJobPhase {
    @Override
    public void doWork(final Runnable action) {
      action.run();
    }
  }

  class PostActorJobPhase implements ActorJobPhase {
    @Override
    public void doWork(final Runnable action) {
      if (returnControlToTestThreadOn == RETURN_CONTROL_POST_ACTION_EXECUTION) {
        returnControlToTestThreadAndWait();
      }

      if (returnControlToTestThreadOn == RETURN_CONTROL_BEFORE_NEXT_ACTION) {
        ControllableActor.this.run(() -> returnControlToTestThreadAndWait());
      }
    }
  }
}
