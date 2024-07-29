/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static io.camunda.zeebe.scheduler.ActorThread.ensureCalledFromActorThread;

import io.camunda.zeebe.scheduler.ActorTask.ActorLifecyclePhase;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.AllCompletedFutureConsumer;
import io.camunda.zeebe.scheduler.future.FutureContinuationRunnable;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ActorControl implements ConcurrencyControl {
  final ActorTask task;
  private final Actor actor;

  public ActorControl(final Actor actor) {
    this.actor = actor;
    task = new ActorTask(actor);
  }

  private ActorControl(final ActorTask task) {
    actor = task.actor;
    this.task = task;
  }

  public static ActorControl current() {
    final ActorThread actorThread = ensureCalledFromActorThread("ActorControl#current");

    return new ActorControl(actorThread.currentTask);
  }

  /**
   * Conditional actions are called while the actor is in the following actor lifecycle phases:
   * {@link ActorLifecyclePhase#STARTED}
   *
   * @param conditionName
   * @param conditionAction
   * @return
   */
  public ActorCondition onCondition(final String conditionName, final Runnable conditionAction) {
    ensureCalledFromWithinActor("onCondition(...)");

    final ActorJob job = new ActorJob();
    job.setRunnable(conditionAction);
    job.onJobAddedToTask(task);

    final ActorConditionImpl condition = new ActorConditionImpl(conditionName, job);
    job.setSubscription(condition);

    return condition;
  }

  /**
   * Callables actions are called while the actor is in the following actor lifecycle phases: {@link
   * ActorLifecyclePhase#STARTED}
   *
   * @param action
   * @return
   */
  public ActorFuture<Void> call(final Runnable action) {
    final Callable<Void> c =
        () -> {
          action.run();
          return null;
        };

    return call(c);
  }

  /**
   * Scheduled a repeating timer
   *
   * <p>The runnable is executed while the actor is in the following actor lifecycle phases: {@link
   * ActorLifecyclePhase#STARTED}
   *
   * @param delay
   * @param runnable
   * @return
   */
  public ScheduledTimer runAtFixedRate(final Duration delay, final Runnable runnable) {
    ensureCalledFromWithinActor("runAtFixedRate(...)");
    return scheduleTimerSubscription(
        runnable,
        job -> new DelayedTimerSubscription(job, delay.toMillis(), TimeUnit.MILLISECONDS, true));
  }

  /**
   * Schedule a timer task at (or after) a timestamp.
   *
   * <p>The runnable is executed while the actor is in the following actor lifecycle phases: {@link
   * * ActorLifecyclePhase#STARTED}
   *
   * <p>This provides no guarantees that the timer task is run at the timestamp. It's likely that
   * the timer task is run shortly after the timestamp. We guarantee that the runnable won't run
   * before the timestamp.
   *
   * @param timestamp A unix epoch timestamp in milliseconds
   * @param runnable The runnable to run at (or after) the timestamp
   * @return A handle to the scheduled timer task
   */
  public ScheduledTimer runAt(final long timestamp, final Runnable runnable) {
    ensureCalledFromWithinActor("runAt(...)");
    return scheduleTimerSubscription(runnable, job -> new StampedTimerSubscription(job, timestamp));
  }

  private TimerSubscription scheduleTimerSubscription(
      final Runnable runnable, final Function<ActorJob, TimerSubscription> subscriptionFactory) {
    final ActorJob job = new ActorJob();
    job.setRunnable(runnable);
    job.onJobAddedToTask(task);

    final TimerSubscription timerSubscription = subscriptionFactory.apply(job);
    job.setSubscription(timerSubscription);

    timerSubscription.submit();

    return timerSubscription;
  }

  /**
   * Invoke the callback when the given future is completed (successfully or exceptionally). This
   * call does not block the actor. If close is requested the actor will not wait on this future, in
   * this case the callback is never called.
   *
   * <p>The callback is executed while the actor is in the following actor lifecycle phases: {@link
   * ActorLifecyclePhase#STARTED}
   *
   * @param future the future to wait on
   * @param callback the callback that handle the future's result. The throwable is <code>null
   *     </code> when the future is completed successfully.
   */
  @Override
  public <T> void runOnCompletion(
      final ActorFuture<T> future, final BiConsumer<T, Throwable> callback) {
    ensureCalledFromWithinActor("runOnCompletion(...)");

    final ActorLifecyclePhase lifecyclePhase = task.getLifecyclePhase();
    if (lifecyclePhase != ActorLifecyclePhase.CLOSE_REQUESTED
        && lifecyclePhase != ActorLifecyclePhase.CLOSED) {
      submitContinuationJob(
          future,
          callback,
          (job) -> new ActorFutureSubscription(future, job, lifecyclePhase.getValue()));
    }
  }

  /**
   * Runnables submitted by the actor itself are executed while the actor is in any of its lifecycle
   * phases.
   *
   * <p>Runnables submitted externally are executed while the actor is in the following actor
   * lifecycle phases: {@link ActorLifecyclePhase#STARTED}
   *
   * @param action
   */
  @Override
  public void run(final Runnable action) {
    scheduleRunnable(action);
  }

  /**
   * Callables actions are called while the actor is in the following actor lifecycle phases: {@link
   * ActorLifecyclePhase#STARTED}
   *
   * @param callable
   * @return
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> ActorFuture<T> call(final Callable<T> callable) {
    final ActorThread runner = ActorThread.current();
    if (runner != null && runner.getCurrentTask() == task) {
      throw new UnsupportedOperationException(
          "Incorrect usage of actor.call(...) cannot be called from current actor.");
    }

    final ActorJob job = new ActorJob();
    final ActorFuture<T> future = job.setCallable(callable);
    job.onJobAddedToTask(task);
    task.submit(job);

    return future;
  }

  /**
   * The runnable is executed while the actor is in the following actor lifecycle phases: {@link
   * ActorLifecyclePhase#STARTED}
   *
   * @param delay
   * @param runnable
   * @return
   */
  @Override
  public ScheduledTimer schedule(final Duration delay, final Runnable runnable) {
    ensureCalledFromWithinActor("runDelayed(...)");
    return scheduleTimerSubscription(
        runnable,
        job -> new DelayedTimerSubscription(job, delay.toMillis(), TimeUnit.MILLISECONDS, false));
  }

  /**
   * Like {@link #run(Runnable)} but submits the runnable to the end of the actor's queue such that
   * other actions may be executed before this. This method is useful in case an actor is in a
   * (potentially endless) loop, and it should be able to interrupt it.
   *
   * <p>The runnable is executed while the actor is in the following actor lifecycle phases: {@link
   * ActorLifecyclePhase#STARTED}
   *
   * @param action the action to run.
   */
  public void submit(final Runnable action) {
    final ActorThread currentThread = ActorThread.current();
    final ActorTask currentTask = currentThread == null ? null : currentThread.getCurrentTask();
    final ActorJob job;

    if (currentThread != null && currentTask == task) {
      job = currentThread.newJob();
    } else {
      job = new ActorJob();
    }

    job.setRunnable(action);
    job.onJobAddedToTask(task);
    task.submit(job);

    if (currentTask != null && currentTask == task) {
      yieldThread();
    }
  }

  /**
   * Invoke the callback when the given future is completed (successfully or exceptionally). This
   * call does not block the actor. If close is requested the actor will wait on this future and not
   * change the phase, in this case the callback will eventually be called.
   *
   * <p>The callback is executed while the actor is in the following actor lifecycle phases: {@link
   * ActorLifecyclePhase#STARTED}
   *
   * @param future the future to wait on
   * @param callback the callback that handle the future's result. The throwable is <code>null
   *     </code> when the future is completed successfully.
   */
  public <T> void runOnCompletionBlockingCurrentPhase(
      final ActorFuture<T> future, final BiConsumer<T, Throwable> callback) {
    ensureCalledFromWithinActor("runOnCompletionBlockingCurrentPhase(...)");

    final ActorLifecyclePhase lifecyclePhase = task.getLifecyclePhase();
    if (lifecyclePhase != ActorLifecyclePhase.CLOSED) {
      submitContinuationJob(
          future,
          callback,
          (job) ->
              new ActorFutureSubscription(
                  future,
                  job,
                  (task.getLifecyclePhase().getValue()
                      | ActorLifecyclePhase.CLOSE_REQUESTED.getValue())));
    }
  }

  private <T> void submitContinuationJob(
      final ActorFuture<T> future,
      final BiConsumer<T, Throwable> callback,
      final Function<ActorJob, ActorFutureSubscription> futureSubscriptionSupplier) {
    final ActorJob continuationJob = new ActorJob();
    continuationJob.setRunnable(new FutureContinuationRunnable<>(future, callback));
    continuationJob.onJobAddedToTask(task);

    final ActorFutureSubscription subscription = futureSubscriptionSupplier.apply(continuationJob);
    continuationJob.setSubscription(subscription);

    future.block(task);
  }

  /**
   * Invoke the callback when the given futures are completed (successfully or exceptionally). This
   * call does not block the actor.
   *
   * <p>The callback is executed while the actor is in the following actor lifecycle phases: {@link
   * ActorLifecyclePhase#STARTED}
   *
   * @param futures the futures to wait on
   * @param callback The throwable is <code>null</code> when all futures are completed successfully.
   *     Otherwise, it holds the exception of the last completed future.
   */
  @Override
  public <T> void runOnCompletion(
      final Collection<ActorFuture<T>> futures, final Consumer<Throwable> callback) {
    if (!futures.isEmpty()) {
      final BiConsumer<T, Throwable> futureConsumer =
          new AllCompletedFutureConsumer<>(futures.size(), callback);

      for (final ActorFuture<T> future : futures) {
        runOnCompletion(future, futureConsumer);
      }
    } else {
      callback.accept(null);
    }
  }

  /** can be called by the actor to yield the thread */
  public void yieldThread() {
    final ActorJob job = ensureCalledFromWithinActor("yieldThread()");
    job.getTask().yieldThread();
  }

  public ActorFuture<Void> close() {
    final ActorJob closeJob = new ActorJob();

    closeJob.onJobAddedToTask(task);
    closeJob.setRunnable(task::requestClose);

    task.submit(closeJob);

    return task.closeFuture;
  }

  private void scheduleRunnable(final Runnable runnable) {
    final ActorThread currentActorThread = ActorThread.current();

    if (currentActorThread != null && currentActorThread.getCurrentTask() == task) {
      final ActorJob newJob = currentActorThread.newJob();
      newJob.setRunnable(runnable);
      newJob.onJobAddedToTask(task);
      task.insertJob(newJob);
    } else {
      final ActorJob job = new ActorJob();
      job.setRunnable(runnable);
      job.onJobAddedToTask(task);
      task.submit(job);
    }
  }

  public boolean isClosing() {
    ensureCalledFromWithinActor("isClosing()");
    return task.isClosing();
  }

  public boolean isClosed() {
    // for that lifecycle phase needs to be volatile
    final ActorLifecyclePhase lifecyclePhase = task.getLifecyclePhase();
    return !(lifecyclePhase == ActorLifecyclePhase.STARTING
        || lifecyclePhase == ActorLifecyclePhase.STARTED);
  }

  public ActorLifecyclePhase getLifecyclePhase() {
    ensureCalledFromWithinActor("getLifecyclePhase()");
    return task.getLifecyclePhase();
  }

  public boolean isCalledFromWithinActor(final ActorJob job) {
    return job != null && job.getActor() == actor;
  }

  private ActorJob ensureCalledFromWithinActor(final String methodName) {
    final ActorJob currentJob = ensureCalledFromActorThread(methodName).getCurrentJob();
    if (!isCalledFromWithinActor(currentJob)) {
      throw new UnsupportedOperationException(
          "Incorrect usage of actor."
              + methodName
              + ": must only be called from within the actor itself.");
    }

    return currentJob;
  }

  /** Mark actor as failed. This sets the lifecycle phase to 'FAILED' and discards all jobs. */
  public void fail(final Throwable error) {
    ensureCalledFromWithinActor("fail()");
    task.fail(error);
  }
}
