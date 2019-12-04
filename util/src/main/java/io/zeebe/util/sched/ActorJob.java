/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched;

import io.zeebe.util.sched.ActorTask.TaskSchedulingState;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.concurrent.Callable;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"unchecked", "rawtypes"})
public class ActorJob {
  TaskSchedulingState schedulingState;

  Actor actor;
  ActorTask task;
  ActorThread actorThread;
  private Callable<?> callable;
  private Runnable runnable;
  private Object invocationResult;
  private boolean isAutoCompleting;
  private boolean isDoneCalled;
  private ActorFuture resultFuture;
  private ActorSubscription subscription;

  public void onJobAddedToTask(ActorTask task) {
    this.actor = task.actor;
    this.task = task;
    this.schedulingState = TaskSchedulingState.QUEUED;
  }

  void execute(ActorThread runner) {
    this.actorThread = runner;
    try {
      invoke();

      if (resultFuture != null) {
        resultFuture.complete(invocationResult);
        resultFuture = null;
      }

    } catch (Throwable e) {
      LoggerFactory.getLogger(ActorJob.class).error("FAILED!", e);
      task.onFailure(e);
    } finally {
      this.actorThread = null;

      // in any case, success or exception, decide if the job should be resubmitted
      if (isTriggeredBySubscription() || (isAutoCompleting && runnable == null) || isDoneCalled) {
        schedulingState = TaskSchedulingState.TERMINATED;
      } else {
        schedulingState = TaskSchedulingState.QUEUED;
      }
    }
  }

  private void invoke() throws Exception {
    if (callable != null) {
      invocationResult = callable.call();
    } else {
      if (!isTriggeredBySubscription()) {
        // TODO: preempt after fixed number of iterations
        while (runnable != null && !task.shouldYield && !isDoneCalled) {
          final Runnable r = this.runnable;

          if (isAutoCompleting) {
            this.runnable = null;
          }

          r.run();
        }
      } else {
        runnable.run();
      }
    }
  }

  public void setRunnable(Runnable runnable) {
    this.runnable = runnable;
  }

  public ActorFuture setCallable(Callable<?> callable) {
    this.callable = callable;
    setResultFuture(new CompletableActorFuture<>());
    return resultFuture;
  }

  /** used to recycle the job object */
  void reset() {
    schedulingState = TaskSchedulingState.NOT_SCHEDULED;

    actor = null;

    task = null;
    actorThread = null;

    callable = null;
    runnable = null;
    invocationResult = null;
    isAutoCompleting = true;
    isDoneCalled = false;

    resultFuture = null;
    subscription = null;
  }

  public void markDone() {
    if (isAutoCompleting) {
      throw new UnsupportedOperationException(
          "Incorrect use of actor.done(). Can only be called in methods submitted using actor.runUntilDone(Runnable r)");
    }

    isDoneCalled = true;
  }

  public void setAutoCompleting(boolean isAutoCompleting) {
    this.isAutoCompleting = isAutoCompleting;
  }

  @Override
  public String toString() {
    String toString = "";

    if (runnable != null) {
      toString += runnable.getClass().getName();
    }
    if (callable != null) {
      toString += callable.getClass().getName();
    }

    toString += " " + schedulingState;

    return toString;
  }

  public boolean isTriggeredBySubscription() {
    return subscription != null;
  }

  public ActorSubscription getSubscription() {
    return subscription;
  }

  public void setSubscription(ActorSubscription subscription) {
    this.subscription = subscription;
    task.addSubscription(subscription);
  }

  public ActorTask getTask() {
    return task;
  }

  public Actor getActor() {
    return actor;
  }

  public void setResultFuture(ActorFuture resultFuture) {
    assert !resultFuture.isDone();
    this.resultFuture = resultFuture;
  }

  public void failFuture(String reason) {
    failFuture(new RuntimeException(reason));
  }

  public void failFuture(Throwable cause) {
    if (this.resultFuture != null) {
      resultFuture.completeExceptionally(cause);
    }
  }
}
