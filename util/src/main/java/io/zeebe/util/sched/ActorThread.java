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

import static io.zeebe.util.sched.metrics.SchedulerMetrics.SHOULD_ENABLE_JUMBO_TASK_DETECTION;
import static io.zeebe.util.sched.metrics.SchedulerMetrics.TASK_MAX_EXECUTION_TIME_NANOS;

import io.zeebe.util.BoundedArrayQueue;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.clock.DefaultActorClock;
import io.zeebe.util.sched.metrics.ActorThreadMetrics;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import org.agrona.UnsafeAccess;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;
import org.slf4j.MDC;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class ActorThread extends Thread implements Consumer<Runnable> {
  static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

  private final ActorThreadMetrics metrics;

  private volatile ActorThreadState state;

  private static final long STATE_OFFSET;

  private final CompletableFuture<Void> terminationFuture = new CompletableFuture<>();

  public final ManyToManyConcurrentArrayQueue<Runnable> submittedCallbacks =
      new ManyToManyConcurrentArrayQueue<>(1024 * 24);

  private final ActorClock clock;

  static {
    try {
      STATE_OFFSET = UNSAFE.objectFieldOffset(ActorThread.class.getDeclaredField("state"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private final int threadId;

  private final TaskScheduler taskScheduler;

  protected final ActorTimerQueue timerJobQueue;

  private final BoundedArrayQueue<ActorJob> jobs = new BoundedArrayQueue<>(2048);
  private final ActorThreadGroup actorThreadGroup;

  protected ActorTaskRunnerIdleStrategy idleStrategy = new ActorTaskRunnerIdleStrategy();

  ActorTask currentTask;

  public ActorThread(
      String name,
      int id,
      ActorThreadGroup threadGroup,
      TaskScheduler taskScheduler,
      ActorClock clock,
      ActorThreadMetrics metrics,
      ActorTimerQueue timerQueue) {
    setName(name);
    this.state = ActorThreadState.NEW;
    this.threadId = id;
    this.clock = clock != null ? clock : new DefaultActorClock();
    this.timerJobQueue = timerQueue != null ? timerQueue : new ActorTimerQueue(this.clock);
    this.actorThreadGroup = threadGroup;
    this.metrics = metrics;
    this.taskScheduler = taskScheduler;
  }

  @Override
  public void run() {
    idleStrategy.init();

    while (state == ActorThreadState.RUNNING) {
      try {
        doWork();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    state = ActorThreadState.TERMINATED;
    metrics.close();

    terminationFuture.complete(null);
  }

  private void doWork() {
    submittedCallbacks.drain(this);

    if (clock.update()) {
      timerJobQueue.processExpiredTimers(clock);
    }

    currentTask = taskScheduler.getNextTask(clock);

    if (currentTask != null) {
      try {
        executeCurrentTask();
      } finally {
        taskScheduler.onTaskReleased(currentTask);
      }
    } else {
      idleStrategy.onIdle();
    }
  }

  private void executeCurrentTask() {
    MDC.put("actor-name", currentTask.getName());
    idleStrategy.onTaskExecuted();
    metrics.incrementTaskExecutionCount();

    final long nanoTimeBeforeTask = clock.getNanoTime();

    boolean resubmit = false;

    try {
      resubmit = currentTask.execute(this);
    } catch (Exception e) {
      // TODO: check interrupt state?
      // TODO: Handle Exception
      e.printStackTrace();

      // TODO: resubmit on exception?
      //                resubmit = true;
    } finally {
      MDC.remove("actor-name");

      clock.update();
      final long taskExecutionTime = clock.getNanoTime() - nanoTimeBeforeTask;

      // FIXME: if the task was woken up concurrently, we do not own it anymore and cannot report
      // metrics
      if (currentTask.isCollectTaskMetrics()) {
        currentTask.reportExecutionTime(taskExecutionTime);
      }

      if (SHOULD_ENABLE_JUMBO_TASK_DETECTION) {
        if (TASK_MAX_EXECUTION_TIME_NANOS < taskExecutionTime) {
          currentTask.warnMaxTaskExecutionTimeExceeded(taskExecutionTime);
        }
      }
    }

    if (resubmit) {
      currentTask.resubmit();
    }
  }

  public void hintWorkAvailable() {
    idleStrategy.hintWorkAvailable();
  }

  protected class ActorTaskRunnerIdleStrategy {
    final BackoffIdleStrategy backoff =
        new BackoffIdleStrategy(100, 100, 1, TimeUnit.MILLISECONDS.toNanos(1));
    boolean isIdle;

    long idleTimeStart;
    long busyTimeStart;

    void init() {
      isIdle = true;
      idleTimeStart = System.nanoTime();
    }

    public void hintWorkAvailable() {
      LockSupport.unpark(ActorThread.this);
    }

    protected void onIdle() {
      if (!isIdle) {
        clock.update();
        idleTimeStart = clock.getNanoTime();
        metrics.recordRunnerBusyTime(idleTimeStart - busyTimeStart);
        isIdle = true;
      }

      backoff.idle();
    }

    protected void onTaskExecuted() {
      backoff.reset();

      if (isIdle) {
        busyTimeStart = clock.getNanoTime();
        metrics.recordRunnerIdleTime(busyTimeStart - idleTimeStart);
        isIdle = false;
      }
    }
  }

  /** Must be called from this thread, schedules a job to be run later. */
  public void scheduleTimer(TimerSubscription timer) {
    timerJobQueue.schedule(timer, clock);
  }

  /** Must be called from this thread, remove a scheduled job. */
  public void removeTimer(TimerSubscription timer) {
    timerJobQueue.remove(timer);
  }

  /**
   * Returns the current {@link ActorThread} or null if the current thread is not an {@link
   * ActorThread}.
   *
   * @return the current {@link ActorThread} or null
   */
  public static ActorThread current() {
    /*
     * Yes, we could work with a thread-local. Except thread locals are slow as f***
     * since they are kept in a map datastructure on the current thread.
     * This implementation takes advantage of the fact that ActorTaskRunner extends Thread
     * itself. If we can cast down, the current thread is the current ActorTaskRunner.
     */
    try {
      return (ActorThread) Thread.currentThread();
    } catch (ClassCastException e) {
      return null;
    }
  }

  public ActorJob newJob() {
    ActorJob job = jobs.poll();

    if (job == null) {
      job = new ActorJob();
    }

    return job;
  }

  void recycleJob(ActorJob j) {
    j.reset();
    jobs.offer(j);
  }

  public int getRunnerId() {
    return threadId;
  }

  public ActorThreadMetrics getMetrics() {
    return metrics;
  }

  @Override
  public void start() {
    if (UNSAFE.compareAndSwapObject(
        this, STATE_OFFSET, ActorThreadState.NEW, ActorThreadState.RUNNING)) {
      super.start();
    } else {
      throw new IllegalStateException("Cannot start runner, not in state 'NEW'.");
    }
  }

  public CompletableFuture<Void> close() {
    if (UNSAFE.compareAndSwapObject(
        this, STATE_OFFSET, ActorThreadState.RUNNING, ActorThreadState.TERMINATING)) {
      return terminationFuture;
    } else {
      throw new IllegalStateException("Cannot stop runner, not in state 'RUNNING'.");
    }
  }

  public enum ActorThreadState {
    NEW,
    RUNNING,
    TERMINATING,
    TERMINATED // runner is not reusable
  }

  public ActorJob getCurrentJob() {
    final ActorTask task = getCurrentTask();

    if (task != null) {
      return task.currentJob;
    }

    return null;
  }

  public ActorTask getCurrentTask() {
    return currentTask;
  }

  public ActorClock getClock() {
    return clock;
  }

  public ActorThreadGroup getActorThreadGroup() {
    return actorThreadGroup;
  }

  @Override
  public void accept(Runnable t) {
    t.run();
  }
}
