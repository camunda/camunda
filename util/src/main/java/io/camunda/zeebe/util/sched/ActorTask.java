/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

import static io.zeebe.util.sched.ActorThread.ensureCalledFromActorThread;

import io.zeebe.util.Loggers;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A task executed by the scheduler. For each actor (instance), exactly one task is created. Each
 * invocation of one of the actor's methods is an {@link ActorJob}.
 */
@SuppressWarnings("restriction")
public class ActorTask {

  private static final Logger LOG = LoggerFactory.getLogger(ActorTask.class);

  public final CompletableActorFuture<Void> closeFuture = new CompletableActorFuture<>();
  final Actor actor;
  ActorJob currentJob;
  boolean shouldYield;
  final AtomicReference<TaskSchedulingState> schedulingState = new AtomicReference<>();
  final AtomicLong stateCount = new AtomicLong(0);
  private final CompletableActorFuture<Void> jobClosingTaskFuture = new CompletableActorFuture<>();
  private final CompletableActorFuture<Void> startingFuture = new CompletableActorFuture<>();
  private final CompletableActorFuture<Void> jobStartingTaskFuture = new CompletableActorFuture<>();
  private ActorExecutor actorExecutor;
  private ActorThreadGroup actorThreadGroup;
  private Deque<ActorJob> fastLaneJobs = new ClosedQueue();
  private volatile ActorLifecyclePhase lifecyclePhase = ActorLifecyclePhase.CLOSED;
  private List<ActorSubscription> subscriptions = new ArrayList<>();
  /**
   * the priority class of the task. Only set if the task is scheduled as non-blocking, CPU-bound
   */
  private int priority = ActorPriority.REGULAR.getPriorityClass();
  /**
   * jobs that are submitted to this task externally. A job is submitted "internally" if it is
   * submitted from a job within the same actor while the task is in RUNNING state.
   */
  private volatile Queue<ActorJob> submittedJobs = new ClosedQueue();

  public ActorTask(final Actor actor) {
    this.actor = actor;
  }

  /** called when the task is initially scheduled. */
  public ActorFuture<Void> onTaskScheduled(
      final ActorExecutor actorExecutor, final ActorThreadGroup actorThreadGroup) {
    this.actorExecutor = actorExecutor;
    this.actorThreadGroup = actorThreadGroup;
    // reset previous state to allow re-scheduling
    closeFuture.close();
    closeFuture.setAwaitingResult();

    jobClosingTaskFuture.close();
    jobClosingTaskFuture.setAwaitingResult();

    startingFuture.close();
    startingFuture.setAwaitingResult();

    jobStartingTaskFuture.close();
    jobStartingTaskFuture.setAwaitingResult();

    submittedJobs = new ManyToOneConcurrentLinkedQueue<>();
    fastLaneJobs = new ArrayDeque<>();
    lifecyclePhase = ActorLifecyclePhase.STARTING;

    // create initial job to invoke on start callback
    final ActorJob j = new ActorJob();
    j.setRunnable(actor::onActorStarting);
    j.setResultFuture(jobStartingTaskFuture);
    j.setAutoCompleting(true);
    j.onJobAddedToTask(this);

    currentJob = j;
    return startingFuture;
  }

  /** Used to externally submit a job. */
  public void submit(final ActorJob job) {
    // get reference to jobs queue
    final Queue<ActorJob> submittedJobs = this.submittedJobs;

    // add job to queue
    if (submittedJobs.offer(job)) {
      if (submittedJobs != this.submittedJobs) {
        // jobs queue was replaced (see onClosed method)
        // in case the job was offer after the original queue was drained
        // we have to manually fail the job to make sure does not get lost
        failJob(job);
      } else {
        // wakeup task if waiting
        tryWakeup();
      }
    } else {
      job.failFuture("Was not able to submit job to the actors queue.");
    }
  }

  public boolean execute(final ActorThread runner) {
    schedulingState.set(TaskSchedulingState.ACTIVE);

    boolean resubmit = false;
    while (!resubmit && (currentJob != null || poll())) {
      currentJob.execute(runner);

      switch (currentJob.schedulingState) {
        case TERMINATED:
          final ActorJob terminatedJob = currentJob;
          currentJob = fastLaneJobs.poll();

          if (terminatedJob.isTriggeredBySubscription()) {
            final ActorSubscription subscription = terminatedJob.getSubscription();

            if (!subscription.isRecurring()) {
              removeSubscription(subscription);
            }

            subscription.onJobCompleted();
          } else {
            runner.recycleJob(terminatedJob);
          }

          break;

        case QUEUED:
          // the task is experiencing backpressure: do not retry it right now, instead re-enqueue
          // the actor task.
          // this allows other tasks which may be needed to unblock the backpressure to run
          resubmit = true;
          break;

        default:
          break;
      }

      if (shouldYield) {
        shouldYield = false;
        resubmit = currentJob != null;
        break;
      }
    }

    if (currentJob == null) {
      resubmit = onAllJobsDone();
    }

    return resubmit;
  }

  private boolean onAllJobsDone() {
    boolean resubmit = false;

    if (allPhaseSubscriptionsTriggered()) {
      switch (lifecyclePhase) {
        case STARTING:
          lifecyclePhase = ActorLifecyclePhase.STARTED;
          submitStartedJob();
          startingFuture.completeWith(jobStartingTaskFuture);
          resubmit = true;
          break;

        case CLOSING:
          lifecyclePhase = ActorLifecyclePhase.CLOSED;
          submitClosedJob();
          resubmit = true;
          break;

        case STARTED:
          resubmit = tryWait();
          break;

        case CLOSE_REQUESTED:
          lifecyclePhase = ActorLifecyclePhase.CLOSING;
          submitClosingJob();
          resubmit = true;
          break;

        case CLOSED:
          onClosed();
          closeFuture.completeWith(jobClosingTaskFuture);
          resubmit = false;
          break;

        case FAILED:
          onClosed();
          resubmit = false;
          break;

        default:
          throw new IllegalStateException(
              "Unexpected actor lifecycle phase " + lifecyclePhase.name());
      }
    } else {
      if (lifecyclePhase != ActorLifecyclePhase.CLOSED) {
        resubmit = tryWait();
      }
    }

    return resubmit;
  }

  private void submitStartedJob() {
    final ActorJob startedJob = ActorThread.current().newJob();
    startedJob.onJobAddedToTask(this);
    startedJob.setAutoCompleting(true);
    startedJob.setRunnable(actor::onActorStarted);
    currentJob = startedJob;
  }

  private void submitClosedJob() {
    final ActorJob closedJob = ActorThread.current().newJob();
    closedJob.onJobAddedToTask(this);
    closedJob.setAutoCompleting(true);
    closedJob.setRunnable(actor::onActorClosed);
    currentJob = closedJob;
  }

  private void submitClosingJob() {
    final ActorJob closeJob = ActorThread.current().newJob();
    closeJob.onJobAddedToTask(this);
    closeJob.setAutoCompleting(true);
    closeJob.setRunnable(actor::onActorClosing);
    closeJob.setResultFuture(jobClosingTaskFuture);
    currentJob = closeJob;
  }

  private void onClosed() {
    schedulingState.set(TaskSchedulingState.NOT_SCHEDULED);

    // we need to work on a copy - otherwise we would get a ConcurrentModificationException
    // since some subscriptions remove them self on cancel
    final var actorSubscriptions = new ArrayList<>(subscriptions);
    actorSubscriptions.forEach(ActorSubscription::cancel);
    subscriptions = new ArrayList<>();

    final Queue<ActorJob> activeJobsQueue = submittedJobs;
    submittedJobs = new ClosedQueue();

    ActorJob j;

    while ((j = activeJobsQueue.poll()) != null) {
      // cancel and discard jobs
      failJob(j);
    }
  }

  private void failJob(final ActorJob job) {
    try {
      job.failFuture("Actor is closed");
    } catch (final IllegalStateException e) {
      // job is already completed or failed, ignore
    }
  }

  public void requestClose() {
    if (lifecyclePhase == ActorLifecyclePhase.STARTED) {
      lifecyclePhase = ActorLifecyclePhase.CLOSE_REQUESTED;

      discardNextJobs();

      actor.onActorCloseRequested();
    }
  }

  public void onFailure(final Exception failure) {
    switch (lifecyclePhase) {
      case STARTING:
        Loggers.ACTOR_LOGGER.error(
            "Actor failed in phase 'STARTING'. Discard all jobs and stop immediatly.", failure);

        lifecyclePhase = ActorLifecyclePhase.FAILED;
        discardNextJobs();
        startingFuture.completeExceptionally(failure);
        break;

      case CLOSING:
        Loggers.ACTOR_LOGGER.error(
            "Actor failed in phase 'CLOSING'. Discard all jobs and stop immediatly.", failure);

        lifecyclePhase = ActorLifecyclePhase.FAILED;
        discardNextJobs();
        closeFuture.completeExceptionally(failure);
        break;

      default:
        actor.handleFailure(failure);
        currentJob.failFuture(failure);
    }
  }

  private void discardNextJobs() {
    // discard next jobs
    ActorJob next;
    while ((next = fastLaneJobs.poll()) != null) {
      LOG.debug("Discard job {} from fastLane of Actor {}.", next, actor.getName());
      failJob(next);
    }
  }

  boolean casStateCount(final long expectedCount) {
    return stateCount.compareAndSet(expectedCount, expectedCount + 1);
  }

  boolean casState(final TaskSchedulingState expectedState, final TaskSchedulingState newState) {
    return schedulingState.compareAndSet(expectedState, newState);
  }

  public boolean claim(final long stateCount) {
    if (casStateCount(stateCount)) {
      return true;
    }

    return false;
  }

  /**
   * used to transition from the {@link TaskSchedulingState#ACTIVE} to the {@link
   * TaskSchedulingState#WAITING} state
   */
  boolean tryWait() {
    // take copy of subscriptions list: once we set the state to WAITING, the task could be woken up
    // by another
    // thread. That thread could modify the subscriptions array.
    final List<ActorSubscription> subscriptionsRef = new ArrayList<>(subscriptions);

    // first set state to waiting
    schedulingState.set(TaskSchedulingState.WAITING);

    /*
     * Accounts for the situation where a job is appended while in state active.
     * In that case the submitting thread does not continue the task since it is not
     * yet in state waiting. After transitioning to waiting we check if we need to wake
     * up right away.
     */
    if ((lifecyclePhase == ActorLifecyclePhase.STARTED && !submittedJobs.isEmpty())
        || pollSubscriptionsWithoutAddingJobs(subscriptionsRef)) {
      // could be that another thread already woke up this task
      return casState(TaskSchedulingState.WAITING, TaskSchedulingState.WAKING_UP);
    }

    return false;
  }

  public boolean tryWakeup() {
    boolean didWakeup = false;

    if (casState(TaskSchedulingState.WAITING, TaskSchedulingState.WAKING_UP)) {
      resubmit();
      didWakeup = true;
    }

    return didWakeup;
  }

  private boolean poll() {
    boolean result = false;

    result |= pollSubmittedJobs();
    result |= pollSubscriptions();

    return result;
  }

  private boolean pollSubscriptions() {
    boolean hasJobs = false;

    for (final ActorSubscription subscription : subscriptions) {
      if (pollSubscription(subscription)) {
        final ActorJob job = subscription.getJob();
        job.schedulingState = TaskSchedulingState.QUEUED;

        if (currentJob == null) {
          currentJob = job;
        } else {
          fastLaneJobs.offer(job);
        }

        hasJobs = true;
      }
    }
    return hasJobs;
  }

  private boolean pollSubscription(final ActorSubscription subscription) {
    return subscription.triggersInPhase(lifecyclePhase) && subscription.poll();
  }

  private boolean pollSubscriptionsWithoutAddingJobs(final List<ActorSubscription> subscriptions) {
    boolean result = false;

    for (int i = 0; i < subscriptions.size() && !result; i++) {
      result |= pollSubscription(subscriptions.get(i));
    }

    return result;
  }

  private boolean allPhaseSubscriptionsTriggered() {
    boolean allTriggered = true;

    for (int i = 0; i < subscriptions.size() && allTriggered; i++) {
      final ActorSubscription subscription = subscriptions.get(i);
      allTriggered &= !subscription.triggersInPhase(lifecyclePhase);
    }

    return allTriggered;
  }

  private boolean pollSubmittedJobs() {
    boolean hasJobs = false;

    while (lifecyclePhase == ActorLifecyclePhase.STARTED && !submittedJobs.isEmpty()) {
      final ActorJob job = submittedJobs.poll();
      if (job != null) {
        if (currentJob == null) {
          currentJob = job;
        } else {
          fastLaneJobs.offer(job);
        }

        hasJobs = true;
      }
    }

    return hasJobs;
  }

  public TaskSchedulingState getState() {
    return schedulingState.get();
  }

  @Override
  public String toString() {
    return actor.getName() + " " + schedulingState.get() + " phase: " + lifecyclePhase;
  }

  public void yield() {
    shouldYield = true;
  }

  public long getStateCount() {
    return stateCount.get();
  }

  public ActorThreadGroup getActorThreadGroup() {
    return actorThreadGroup;
  }

  public String getName() {
    return actor.getName();
  }

  public Actor getActor() {
    return actor;
  }

  public boolean isClosing() {
    return lifecyclePhase == ActorLifecyclePhase.CLOSING;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(final int priority) {
    this.priority = priority;
  }

  public ActorExecutor getActorExecutor() {
    return actorExecutor;
  }

  public ActorLifecyclePhase getLifecyclePhase() {
    return lifecyclePhase;
  }

  public CompletableActorFuture<Void> getStartingFuture() {
    return startingFuture;
  }

  public void addSubscription(final ActorSubscription subscription) {
    ensureCalledFromActorThread("addSubscription(ActorSubscription)");
    subscriptions.add(subscription);
  }

  private void removeSubscription(final ActorSubscription subscription) {
    ensureCalledFromActorThread("removeSubscription(ActorSubscription)");
    subscriptions.remove(subscription);
  }

  // subscription helpers

  public void onSubscriptionCancelled(final ActorSubscription subscription) {
    if (lifecyclePhase != ActorLifecyclePhase.CLOSED) {
      removeSubscription(subscription);
    }
  }

  public void setUpdatedSchedulingHints(final int hints) {
    if (SchedulingHints.isCpuBound(hints)) {
      priority = SchedulingHints.getPriority(hints);
      actorThreadGroup = actorExecutor.getCpuBoundThreads();
    } else {
      actorThreadGroup = actorExecutor.getIoBoundThreads();
    }
  }

  public void resubmit() {
    actorThreadGroup.submit(this);
  }

  public void insertJob(final ActorJob job) {
    fastLaneJobs.addFirst(job);
  }

  public void fail() {
    lifecyclePhase = ActorLifecyclePhase.FAILED;
    discardNextJobs();
    actor.onActorFailed();
  }

  /** Describes an actor's scheduling state */
  public enum TaskSchedulingState {
    NOT_SCHEDULED,
    ACTIVE,
    QUEUED,
    WAITING,
    WAKING_UP,
    TERMINATED
  }

  /** An actor task's lifecycle phases */
  public enum ActorLifecyclePhase {
    STARTING(1),
    STARTED(2),
    CLOSE_REQUESTED(4),
    CLOSING(8),
    CLOSED(16),
    FAILED(32);

    private final int value;

    ActorLifecyclePhase(final int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
  }
}
