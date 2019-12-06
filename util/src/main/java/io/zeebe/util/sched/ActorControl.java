/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched;

import io.zeebe.util.sched.ActorTask.ActorLifecyclePhase;
import io.zeebe.util.sched.channel.ChannelConsumerCondition;
import io.zeebe.util.sched.channel.ChannelSubscription;
import io.zeebe.util.sched.channel.ConsumableChannel;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.AllCompletedFutureConsumer;
import io.zeebe.util.sched.future.FirstSuccessfullyCompletedFutureConsumer;
import io.zeebe.util.sched.future.FutureContinuationRunnable;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ActorControl {
  final ActorTask task;
  private final Actor actor;

  public ActorControl(Actor actor) {
    this.actor = actor;
    this.task = new ActorTask(actor);
  }

  private ActorControl(ActorTask task) {
    this.actor = task.actor;
    this.task = task;
  }

  public static ActorControl current() {
    final ActorThread actorThread =
        ActorControl.ensureCalledFromActorThread("ActorControl#current");

    return new ActorControl(actorThread.currentTask);
  }

  /**
   * changes the actor's scheduling hints. For example, this makes it possible to transform a
   * cpu-bound actor into an io-bound actor and vice versa.
   *
   * @param hints the changed scheduling hints
   */
  public void setSchedulingHints(int hints) {
    ensureCalledFromWithinActor("resubmit(...)");
    task.setUpdatedSchedulingHints(hints);
  }

  /**
   * Consumers are called while the actor is in the following actor lifecycle phases: {@link
   * ActorLifecyclePhase#STARTED}
   *
   * @param channel
   * @param consumer
   */
  public ChannelSubscription consume(ConsumableChannel channel, Runnable consumer) {
    ensureCalledFromWithinActor("consume(...)");

    final ActorJob job = new ActorJob();
    job.setRunnable(consumer);
    job.setAutoCompleting(false);
    job.onJobAddedToTask(task);

    final ChannelConsumerCondition subscription = new ChannelConsumerCondition(job, channel);
    job.setSubscription(subscription);

    channel.registerConsumer(subscription);

    return subscription;
  }

  public void pollBlocking(Runnable condition, Runnable action) {
    ensureCalledFromWithinActor("pollBlocking(...)");

    final ActorJob job = new ActorJob();
    job.setRunnable(action);
    job.onJobAddedToTask(task);

    final BlockingPollSubscription subscription =
        new BlockingPollSubscription(job, condition, task.getActorExecutor(), true);
    job.setSubscription(subscription);

    subscription.submit();
  }

  /**
   * Conditional actions are called while the actor is in the following actor lifecycle phases:
   * {@link ActorLifecyclePhase#STARTED}
   *
   * @param conditionName
   * @param conditionAction
   * @return
   */
  public ActorCondition onCondition(String conditionName, Runnable conditionAction) {
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
   * @param callable
   * @return
   */
  @SuppressWarnings("unchecked")
  public <T> ActorFuture<T> call(Callable<T> callable) {
    final ActorThread runner = ActorThread.current();
    if (runner != null && runner.getCurrentTask() == task) {
      throw new UnsupportedOperationException(
          "Incorrect usage of actor.call(...) cannot be called from current actor.");
    }

    final ActorJob job = new ActorJob();
    final ActorFuture<T> future = job.setCallable(callable);
    job.onJobAddedToTask(task);
    job.setAutoCompleting(true);
    task.submit(job);

    return future;
  }

  /**
   * Callables actions are called while the actor is in the following actor lifecycle phases: {@link
   * ActorLifecyclePhase#STARTED}
   *
   * @param action
   * @return
   */
  public ActorFuture<Void> call(Runnable action) {
    final Callable<Void> c =
        () -> {
          action.run();
          return null;
        };

    return call(c);
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
  public void run(Runnable action) {
    scheduleRunnable(action, true);
  }

  /**
   * run a blocking task
   *
   * <p>The provided runnable is executed in any of the actor's lifecycle phases.
   */
  public void runBlocking(Runnable runnable) {
    ensureCalledFromWithinActor("runBlocking(...)");

    final ActorJob noop = new ActorJob();
    noop.onJobAddedToTask(task);
    noop.setAutoCompleting(true);
    noop.setRunnable(
        () -> {
          // noop
        });

    final BlockingPollSubscription subscription =
        new BlockingPollSubscription(noop, runnable, task.getActorExecutor(), false);
    noop.setSubscription(subscription);

    subscription.submit();
  }

  /**
   * The provided runnable is executed in any of the actor's lifecycle phases. The provided
   * completionConsumer is only executed while the actor is in one of the following lifecycle phases
   * {@link ActorLifecyclePhase#STARTED}.
   *
   * @param runnable
   * @param completionConsumer
   */
  public void runBlocking(Runnable runnable, Consumer<Throwable> completionConsumer) {
    final RunnableAdapter<Void> adapter = RunnableAdapter.wrapRunnable(runnable);
    ensureCalledFromWithinActor("runBlocking(...)");

    final ActorJob noop = new ActorJob();
    noop.onJobAddedToTask(task);
    noop.setAutoCompleting(true);
    noop.setRunnable(adapter.wrapConsumer(completionConsumer));

    final BlockingPollSubscription subscription =
        new BlockingPollSubscription(noop, adapter, task.getActorExecutor(), false);
    noop.setSubscription(subscription);

    subscription.submit();
  }

  /**
   * Run the provided runnable repeatedly until it calls {@link #done()}. To be used for jobs which
   * may experience backpressure.
   */
  public void runUntilDone(Runnable runnable) {
    ensureCalledFromWithinActor("runUntilDone(...)");
    scheduleRunnable(runnable, false);
  }

  /**
   * The runnable is is executed while the actor is in the following actor lifecycle phases: {@link
   * ActorLifecyclePhase#STARTED}
   *
   * @param delay
   * @param runnable
   * @return
   */
  public ScheduledTimer runDelayed(Duration delay, Runnable runnable) {
    ensureCalledFromWithinActor("runDelayed(...)");
    return scheduleTimer(delay, false, runnable);
  }

  /**
   * Like {@link #run(Runnable)} but submits the runnable to the end end of the actor's queue such
   * that other other actions may be executed before this. This method is useful in case an actor is
   * in a (potentially endless) loop and it should be able to interrupt it.
   *
   * <p>The runnable is is executed while the actor is in the following actor lifecycle phases:
   * {@link ActorLifecyclePhase#STARTED}
   *
   * @param action the action to run.
   */
  public void submit(Runnable action) {
    final ActorThread currentActorRunner = ensureCalledFromActorThread("run(...)");
    final ActorTask currentTask = currentActorRunner.getCurrentTask();

    final ActorJob job;
    if (currentTask == this.task) {
      job = currentActorRunner.newJob();
    } else {
      job = new ActorJob();
    }

    job.setRunnable(action);
    job.setAutoCompleting(true);
    job.onJobAddedToTask(task);
    task.submit(job);

    if (currentTask == this.task) {
      yield();
    }
  }

  /**
   * Scheduled a repeating timer
   *
   * <p>The runnable is is executed while the actor is in the following actor lifecycle phases:
   * {@link ActorLifecyclePhase#STARTED}
   *
   * @param delay
   * @param runnable
   * @return
   */
  public ScheduledTimer runAtFixedRate(Duration delay, Runnable runnable) {
    ensureCalledFromWithinActor("runAtFixedRate(...)");
    return scheduleTimer(delay, true, runnable);
  }

  private TimerSubscription scheduleTimer(Duration delay, boolean isRecurring, Runnable runnable) {
    final ActorJob job = new ActorJob();
    job.setRunnable(runnable);
    job.onJobAddedToTask(task);

    final TimerSubscription timerSubscription =
        new TimerSubscription(job, delay.toNanos(), TimeUnit.NANOSECONDS, isRecurring);
    job.setSubscription(timerSubscription);

    timerSubscription.submit();

    return timerSubscription;
  }

  /**
   * Invoke the callback when the given future is completed (successfully or exceptionally). This
   * call does not block the actor. If close is requested the actor will not wait on this future, in
   * this case the callback is never called.
   *
   * <p>The callback is is executed while the actor is in the following actor lifecycle phases:
   * {@link ActorLifecyclePhase#STARTED}
   *
   * @param future the future to wait on
   * @param callback the callback that handle the future's result. The throwable is <code>null
   *     </code> when the future is completed successfully.
   */
  public <T> void runOnCompletion(ActorFuture<T> future, BiConsumer<T, Throwable> callback) {
    ensureCalledFromWithinActor("runOnCompletion(...)");

    final ActorLifecyclePhase lifecyclePhase = task.getLifecyclePhase();
    if (lifecyclePhase != ActorLifecyclePhase.CLOSE_REQUESTED
        && lifecyclePhase != ActorLifecyclePhase.CLOSED) {
      this.submitContinuationJob(
          future,
          callback,
          (job) -> new ActorFutureSubscription(future, job, lifecyclePhase.getValue()));
    }
  }

  /**
   * Invoke the callback when the given future is completed (successfully or exceptionally). This
   * call does not block the actor. If close is requested the actor will wait on this future and not
   * change the phase, in this case the callback will eventually called.
   *
   * <p>The callback is is executed while the actor is in the following actor lifecycle phases:
   * {@link ActorLifecyclePhase#STARTED}
   *
   * @param future the future to wait on
   * @param callback the callback that handle the future's result. The throwable is <code>null
   *     </code> when the future is completed successfully.
   */
  public <T> void runOnCompletionBlockingCurrentPhase(
      ActorFuture<T> future, BiConsumer<T, Throwable> callback) {
    ensureCalledFromWithinActor("runOnCompletionBlockingCurrentPhase(...)");

    final ActorLifecyclePhase lifecyclePhase = task.getLifecyclePhase();
    if (lifecyclePhase != ActorLifecyclePhase.CLOSED) {
      this.submitContinuationJob(
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
      ActorFuture<T> future,
      BiConsumer<T, Throwable> callback,
      Function<ActorJob, ActorFutureSubscription> futureSubscriptionSupplier) {
    final ActorJob continuationJob = new ActorJob();
    continuationJob.setRunnable(new FutureContinuationRunnable<>(future, callback));
    continuationJob.setAutoCompleting(true);
    continuationJob.onJobAddedToTask(task);

    final ActorFutureSubscription subscription = futureSubscriptionSupplier.apply(continuationJob);
    continuationJob.setSubscription(subscription);

    future.block(task);
  }

  /**
   * Invoke the callback when the given futures are completed (successfully or exceptionally). This
   * call does not block the actor.
   *
   * <p>The callback is is executed while the actor is in the following actor lifecycle phases:
   * {@link ActorLifecyclePhase#STARTED}
   *
   * @param futures the futures to wait on
   * @param callback The throwable is <code>null</code> when all futures are completed successfully.
   *     Otherwise, it holds the exception of the last completed future.
   */
  public <T> void runOnCompletion(
      Collection<ActorFuture<T>> futures, Consumer<Throwable> callback) {
    if (!futures.isEmpty()) {
      final BiConsumer<T, Throwable> futureConsumer =
          new AllCompletedFutureConsumer<>(futures.size(), callback);

      for (ActorFuture<T> future : futures) {
        runOnCompletion(future, futureConsumer);
      }
    } else {
      callback.accept(null);
    }
  }

  /**
   * Invoke the callback when the first future is completed successfully, or when all futures are
   * completed exceptionally. This call does not block the actor.
   *
   * <p>The callback is is executed while the actor is in the following actor lifecycle phases:
   * {@link ActorLifecyclePhase#STARTED}
   *
   * @param futures the futures to wait on
   * @param callback the callback that handle the future's result. The throwable is <code>null
   *     </code> when the first future is completed successfully. Otherwise, it holds the exception
   *     of the last completed future.
   */
  public <T> void runOnFirstCompletion(
      Collection<ActorFuture<T>> futures, BiConsumer<T, Throwable> callback) {
    runOnFirstCompletion(futures, callback, null);
  }

  /**
   * Invoke the callback when the first future is completed successfully, or when all futures are
   * completed exceptionally. This call does not block the actor.
   *
   * <p>The callback is is executed while the actor is in the following actor lifecycle phases:
   * {@link ActorLifecyclePhase#STARTED}
   *
   * @param futures the futures to wait on
   * @param callback the callback that handle the future's result. The throwable is <code>null
   *     </code> when the first future is completed successfully. Otherwise, it holds the exception
   *     of the last completed future.
   * @param closer the callback that is invoked when a future is completed after the first future is
   *     completed
   */
  public <T> void runOnFirstCompletion(
      Collection<ActorFuture<T>> futures, BiConsumer<T, Throwable> callback, Consumer<T> closer) {
    final BiConsumer<T, Throwable> futureConsumer =
        new FirstSuccessfullyCompletedFutureConsumer<>(futures.size(), callback, closer);

    for (ActorFuture<T> future : futures) {
      runOnCompletion(future, futureConsumer);
    }
  }

  /** can be called by the actor to yield the thread */
  public void yield() {
    final ActorJob job = ensureCalledFromWithinActor("yield()");
    job.getTask().yield();
  }

  public ActorFuture<Void> close() {
    final ActorJob closeJob = new ActorJob();

    closeJob.onJobAddedToTask(task);
    closeJob.setAutoCompleting(true);
    closeJob.setRunnable(task::requestClose);

    task.submit(closeJob);

    return task.closeFuture;
  }

  private void scheduleRunnable(Runnable runnable, boolean autocompleting) {
    final ActorThread currentActorThread = ActorThread.current();

    if (currentActorThread != null && currentActorThread.getCurrentTask() == this.task) {
      final ActorJob newJob = currentActorThread.newJob();
      newJob.setRunnable(runnable);
      newJob.setAutoCompleting(autocompleting);
      newJob.onJobAddedToTask(task);
      task.insertJob(newJob);
    } else {
      final ActorJob job = new ActorJob();
      job.setRunnable(runnable);
      job.setAutoCompleting(autocompleting);
      job.onJobAddedToTask(task);
      task.submit(job);
    }
  }

  public void done() {
    final ActorJob job = ensureCalledFromWithinActor("done()");
    job.markDone();
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

  public void setPriority(ActorPriority priority) {
    ensureCalledFromActorThread("setPriority()");
    task.setPriority(priority.getPriorityClass());
  }

  public ActorLifecyclePhase getLifecyclePhase() {
    ensureCalledFromWithinActor("getLifecyclePhase()");
    return task.getLifecyclePhase();
  }

  public boolean isCalledFromWithinActor(ActorJob job) {
    return job != null && job.getActor() == actor;
  }

  private ActorJob ensureCalledFromWithinActor(String methodName) {
    final ActorJob currentJob = ensureCalledFromActorThread(methodName).getCurrentJob();
    if (!isCalledFromWithinActor(currentJob)) {
      throw new UnsupportedOperationException(
          "Incorrect usage of actor."
              + methodName
              + ": must only be called from within the actor itself.");
    }

    return currentJob;
  }

  private static ActorThread ensureCalledFromActorThread(String methodName) {
    final ActorThread thread = ActorThread.current();

    if (thread == null) {
      throw new UnsupportedOperationException(
          "Incorrect usage of actor. " + methodName + ": must be called from actor thread");
    }

    return thread;
  }
}
