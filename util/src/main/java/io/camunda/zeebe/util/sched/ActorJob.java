/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched;

import io.camunda.zeebe.util.Loggers;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import io.camunda.zeebe.util.sched.ActorTask.TaskSchedulingState;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.concurrent.Callable;
import org.slf4j.Logger;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class ActorJob {
  private static final Logger LOG = Loggers.ACTOR_LOGGER;
  private static final FatalErrorHandler FATAL_ERROR_HANDLER = FatalErrorHandler.withLogger(LOG);

  TaskSchedulingState schedulingState;
  Actor actor;
  ActorTask task;
  ActorThread actorThread;
  private Callable<?> callable;
  private Runnable runnable;
  private Object invocationResult;
  private ActorFuture resultFuture;
  private ActorSubscription subscription;

  public void onJobAddedToTask(final ActorTask task) {
    actor = task.actor;
    this.task = task;
    schedulingState = TaskSchedulingState.QUEUED;
  }

  void execute(final ActorThread runner) {
    actorThread = runner;
    try {
      invoke();

      if (resultFuture != null) {
        resultFuture.complete(invocationResult);
        resultFuture = null;
      }

    } catch (final Throwable e) {
      FATAL_ERROR_HANDLER.handleError(e);
      task.onFailure(e);
    } finally {
      actorThread = null;

      // in any case, success or exception, decide if the job should be resubmitted
      if (isTriggeredBySubscription() || runnable == null) {
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
      // only tasks triggered by a subscription can "yield"; everything else just executes once
      if (!isTriggeredBySubscription()) {
        final Runnable r = runnable;
        runnable = null;
        r.run();
      } else {
        runnable.run();
      }
    }
  }

  public void setRunnable(final Runnable runnable) {
    this.runnable = runnable;
  }

  public ActorFuture setCallable(final Callable<?> callable) {
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

    resultFuture = null;
    subscription = null;
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

  public void setSubscription(final ActorSubscription subscription) {
    this.subscription = subscription;
    task.addSubscription(subscription);
  }

  public ActorTask getTask() {
    return task;
  }

  public Actor getActor() {
    return actor;
  }

  public void setResultFuture(final ActorFuture resultFuture) {
    assert !resultFuture.isDone();
    this.resultFuture = resultFuture;
  }

  public void failFuture(final String reason) {
    failFuture(new RuntimeException(reason));
  }

  public void failFuture(final Throwable cause) {
    if (resultFuture != null) {
      resultFuture.completeExceptionally(cause);
    }
  }
}
