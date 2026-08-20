/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.zeebe.scheduler.ActorMetrics.SubscriptionType;
import io.camunda.zeebe.scheduler.ActorTask.TaskSchedulingState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Loggers;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.Async;
import org.slf4j.Logger;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class ActorJob {
  private static final Logger LOG = Loggers.ACTOR_LOGGER;
  private static final FatalErrorHandler FATAL_ERROR_HANDLER = FatalErrorHandler.withLogger(LOG);

  TaskSchedulingState schedulingState;
  ActorTask task;
  private Callable<?> callable;
  private Runnable runnable;
  private ActorFuture resultFuture;
  private ActorSubscription subscription;
  private long scheduledAt = -1;

  public void onJobAddedToTask(final ActorTask task) {
    scheduledAt = System.nanoTime();
    this.task = task;
    schedulingState = TaskSchedulingState.QUEUED;
  }

  @Async.Execute
  void execute(final ActorThread runner) {
    observeSchedulingLatency(runner.getActorMetrics());
    try {
      invoke();
    } catch (final Throwable e) {
      FATAL_ERROR_HANDLER.handleError(e);
      task.onFailure(e);
    } finally {
      // in any case, success or exception, decide if the job should be resubmitted
      if (isTriggeredBySubscription() || runnable == null) {
        schedulingState = TaskSchedulingState.TERMINATED;
      } else {
        schedulingState = TaskSchedulingState.QUEUED;
        scheduledAt = System.nanoTime();
      }
    }
  }

  private void observeSchedulingLatency(final ActorMetrics metrics) {
    if (metrics.isEnabled()) {
      final var now = System.nanoTime();
      if (subscription instanceof final ActorFutureSubscription s
          && s.getFuture() instanceof final CompletableActorFuture<?> f) {
        final var subscriptionCompleted = f.getCompletedAt();
        metrics.observeJobSchedulingLatency(now - subscriptionCompleted, SubscriptionType.FUTURE);
      } else if (subscription instanceof final TimerSubscription s) {
        final var timerExpired = s.getTimerExpiredAt();
        metrics.observeJobSchedulingLatency(now - timerExpired, SubscriptionType.TIMER);
      } else if (subscription == null && scheduledAt != -1) {
        metrics.observeJobSchedulingLatency(now - scheduledAt, SubscriptionType.NONE);
      }
    }
  }

  private void invoke() throws Exception {
    final Object invocationResult;
    if (callable != null) {
      invocationResult = callable.call();
    } else {
      invocationResult = null;
      // only tasks triggered by a subscription can "yield"; everything else just executes once
      if (!isTriggeredBySubscription()) {
        final Runnable r = runnable;
        runnable = null;
        r.run();
      } else {
        runnable.run();
      }
    }
    if (resultFuture != null) {
      resultFuture.complete(invocationResult);
      resultFuture = null;
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
    scheduledAt = -1;

    task = null;

    callable = null;
    runnable = null;

    resultFuture = null;
    subscription = null;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("ActorJob{");
    sb.append("schedulingState=").append(schedulingState);
    sb.append(", task=").append(task);
    if (callable != null) {
      sb.append(", callable=").append(callable);
    }
    if (runnable != null) {
      sb.append(", runnable=").append(runnable);
    }
    if (resultFuture != null) {
      sb.append(", resultFuture=").append(resultFuture);
    }
    if (subscription != null) {
      sb.append(", subscription=").append(subscription);
    }
    if (scheduledAt != -1) {
      sb.append(", scheduledAt=").append(scheduledAt);
    }
    sb.append('}');
    return sb.toString();
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
    return task.actor;
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
