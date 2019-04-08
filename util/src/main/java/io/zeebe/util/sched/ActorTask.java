/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.util.sched;

import static org.agrona.UnsafeAccess.UNSAFE;

import io.zeebe.util.Loggers;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.metrics.TaskMetrics;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

/**
 * A task executed by the scheduler. For each actor (instance), exactly one task is created. Each
 * invocation of one of the actor's methods is an {@link ActorJob}.
 */
@SuppressWarnings("restriction")
public class ActorTask {
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

    ActorLifecyclePhase(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
  }

  private static final long STATE_COUNT_OFFSET;
  private static final long SCHEDULING_STATE_OFFSET;

  static {
    try {
      STATE_COUNT_OFFSET = UNSAFE.objectFieldOffset(ActorTask.class.getDeclaredField("stateCount"));
      SCHEDULING_STATE_OFFSET =
          UNSAFE.objectFieldOffset(ActorTask.class.getDeclaredField("schedulingState"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public final CompletableActorFuture<Void> closeFuture = new CompletableActorFuture<>();
  private final CompletableActorFuture<Void> jobClosingTaskFuture = new CompletableActorFuture<>();

  private final CompletableActorFuture<Void> startingFuture = new CompletableActorFuture<>();
  private final CompletableActorFuture<Void> jobStartingTaskFuture = new CompletableActorFuture<>();

  final Actor actor;

  private ActorExecutor actorExecutor;
  private ActorThreadGroup actorThreadGroup;

  /**
   * jobs that are submitted to this task externally. A job is submitted "internally" if it is
   * submitted from a job within the same actor while the task is in RUNNING state.
   */
  private volatile Queue<ActorJob> submittedJobs = new ClosedQueue();

  private Deque<ActorJob> fastLaneJobs = new ClosedQueue();

  private ActorLifecyclePhase lifecyclePhase = ActorLifecyclePhase.CLOSED;

  volatile TaskSchedulingState schedulingState = null;

  volatile long stateCount = 0;

  ActorJob currentJob;

  private ActorSubscription[] subscriptions = new ActorSubscription[0];

  boolean shouldYield;

  private TaskMetrics taskMetrics;

  private boolean isCollectTaskMetrics;

  private boolean isJumbo = false;

  /**
   * the priority class of the task. Only set if the task is scheduled as non-blocking, CPU-bound
   */
  private int priority = ActorPriority.REGULAR.getPriorityClass();

  public ActorTask(Actor actor) {
    this.actor = actor;
  }

  /** called when the task is initially scheduled. */
  public ActorFuture<Void> onTaskScheduled(
      ActorExecutor actorExecutor, ActorThreadGroup actorThreadGroup, TaskMetrics taskMetrics) {
    this.actorExecutor = actorExecutor;
    this.actorThreadGroup = actorThreadGroup;
    // reset previous state to allow re-scheduling
    this.closeFuture.close();
    this.closeFuture.setAwaitingResult();

    jobClosingTaskFuture.close();
    jobClosingTaskFuture.setAwaitingResult();

    startingFuture.close();
    startingFuture.setAwaitingResult();

    jobStartingTaskFuture.close();
    jobStartingTaskFuture.setAwaitingResult();

    this.isJumbo = false;
    this.submittedJobs = new ManyToOneConcurrentLinkedQueue<>();
    this.fastLaneJobs = new ArrayDeque<>();
    this.lifecyclePhase = ActorLifecyclePhase.STARTING;

    this.isCollectTaskMetrics = taskMetrics != null;
    this.taskMetrics = taskMetrics;

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
  public void submit(ActorJob job) {
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

  public boolean execute(ActorThread runner) {
    schedulingState = TaskSchedulingState.ACTIVE;

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
    schedulingState = TaskSchedulingState.NOT_SCHEDULED;

    for (int i = 0; i < subscriptions.length; i++) {
      subscriptions[i].cancel();
    }

    subscriptions = new ActorSubscription[0];

    final Queue<ActorJob> activeJobsQueue = submittedJobs;
    submittedJobs = new ClosedQueue();

    ActorJob j;

    while ((j = activeJobsQueue.poll()) != null) {
      // cancel and discard jobs
      failJob(j);
    }

    if (taskMetrics != null) {
      taskMetrics.close();
    }
  }

  private void failJob(ActorJob job) {
    try {
      job.failFuture("Actor is closed");
    } catch (IllegalStateException e) {
      // job is already completed or failed, ignore
    }
  }

  public void requestClose() {
    if (lifecyclePhase == ActorLifecyclePhase.STARTED) {
      this.lifecyclePhase = ActorLifecyclePhase.CLOSE_REQUESTED;

      discardNextJobs();

      actor.onActorCloseRequested();
    }
  }

  public void onFailure(Throwable failure) {
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
        Loggers.ACTOR_LOGGER.error(
            "Actor failed in phase '{}'. Continue with next job.", lifecyclePhase, failure);

        currentJob.failFuture(failure);
    }
  }

  private void discardNextJobs() {
    // discard next jobs
    ActorJob next;
    while ((next = fastLaneJobs.poll()) != null) {
      failJob(next);
    }
  }

  boolean casStateCount(long expectedCount) {
    return UNSAFE.compareAndSwapLong(this, STATE_COUNT_OFFSET, expectedCount, expectedCount + 1);
  }

  boolean casState(TaskSchedulingState expectedState, TaskSchedulingState newState) {
    return UNSAFE.compareAndSwapObject(this, SCHEDULING_STATE_OFFSET, expectedState, newState);
  }

  public boolean claim(long stateCount) {
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
    final ActorSubscription[] subscriptionsCopy = this.subscriptions;

    // first set state to waiting
    schedulingState = TaskSchedulingState.WAITING;

    /*
     * Accounts for the situation where a job is appended while in state active.
     * In that case the submitting thread does not continue the task since it is not
     * yet in state waiting. After transitioning to waiting we check if we need to wake
     * up right away.
     */
    if ((lifecyclePhase == ActorLifecyclePhase.STARTED && !submittedJobs.isEmpty())
        || pollSubscriptionsWithoutAddingJobs(subscriptionsCopy)) {
      // could be that another thread already woke up this task
      if (casState(TaskSchedulingState.WAITING, TaskSchedulingState.WAKING_UP)) {
        return true;
      }
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

    for (int i = 0; i < subscriptions.length; i++) {
      final ActorSubscription subscription = subscriptions[i];

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

  private boolean pollSubscriptionsWithoutAddingJobs(ActorSubscription[] subscriptions) {
    boolean result = false;

    for (int i = 0; i < subscriptions.length && !result; i++) {
      result |= pollSubscription(subscriptions[i]);
    }

    return result;
  }

  private boolean allPhaseSubscriptionsTriggered() {
    boolean allTriggered = true;

    for (int i = 0; i < subscriptions.length && allTriggered; i++) {
      final ActorSubscription subscription = subscriptions[i];
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
    return schedulingState;
  }

  @Override
  public String toString() {
    return actor.getName() + " " + schedulingState + " phase: " + lifecyclePhase;
  }

  public void yield() {
    shouldYield = true;
  }

  public TaskMetrics getMetrics() {
    return taskMetrics;
  }

  public boolean isCollectTaskMetrics() {
    return isCollectTaskMetrics;
  }

  public void reportExecutionTime(long t) {
    taskMetrics.reportExecutionTime(t);
  }

  public void warnMaxTaskExecutionTimeExceeded(long taskExecutionTime) {
    if (!isJumbo) {
      isJumbo = true;

      System.err.println(
          String.format(
              "%s reported running for %dµs. Jumbo task detected! "
                  + "Will not print further warnings for this task.",
              actor.getName(), TimeUnit.NANOSECONDS.toMicros(taskExecutionTime)));
    }
  }

  public boolean isHasWarnedJumbo() {
    return isJumbo;
  }

  public long getStateCount() {
    return stateCount;
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

  public void setPriority(int priority) {
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

  // subscription helpers

  public void addSubscription(ActorSubscription subscription) {
    final ActorSubscription[] arrayCopy = Arrays.copyOf(subscriptions, subscriptions.length + 1);
    arrayCopy[arrayCopy.length - 1] = subscription;
    subscriptions = arrayCopy;
  }

  private void removeSubscription(ActorSubscription subscription) {
    final int length = subscriptions.length;

    int index = -1;
    for (int i = 0; i < subscriptions.length; i++) {
      if (subscriptions[i] == subscription) {
        index = i;
      }
    }

    assert index >= 0 : "Subscription not registered";

    final ActorSubscription[] newSubscriptions = new ActorSubscription[length - 1];
    System.arraycopy(subscriptions, 0, newSubscriptions, 0, index);
    if (index < length - 1) {
      System.arraycopy(subscriptions, index + 1, newSubscriptions, index, length - index - 1);
    }

    this.subscriptions = newSubscriptions;
  }

  public void onSubscriptionCancelled(ActorSubscription subscription) {
    if (lifecyclePhase != ActorLifecyclePhase.CLOSED) {
      removeSubscription(subscription);
    }
  }

  public void setUpdatedSchedulingHints(int hints) {
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

  public void insertJob(ActorJob job) {
    fastLaneJobs.addFirst(job);
  }
}
