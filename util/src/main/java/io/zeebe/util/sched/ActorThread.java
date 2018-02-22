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

import java.util.concurrent.*;

import io.zeebe.util.sched.ZbActorScheduler.ActorSchedulerBuilder;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.clock.DefaultActorClock;
import io.zeebe.util.sched.metrics.ActorRunnerMetrics;
import org.agrona.UnsafeAccess;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.slf4j.MDC;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class ActorThread extends Thread
{
    static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

    private final ActorRunnerMetrics metrics;

    private volatile ActorThreadState state;

    private static final long STATE_OFFSET;

    private final CompletableFuture<Void> terminationFuture = new CompletableFuture<>();

    private final ActorClock clock;

    static
    {
        try
        {
            STATE_OFFSET = UNSAFE.objectFieldOffset(ActorThread.class.getDeclaredField("state"));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private final int threadId;

    private final PriorityScheduler priorityScheduler;

    /**
     * Multi-level queues, one for each priority
     */
    private final ActorTaskQueue[] taskQueues;

    private final ActorTimerQueue timerJobQueue;

    private final ActorJobPool jobPool = new ActorJobPool();

    protected ActorTaskRunnerIdleStrategy idleStrategy = new ActorTaskRunnerIdleStrategy();

    ActorTask currentTask;

    private ActorThread[] actorTaskRunners;

    public ActorThread(int id, ActorSchedulerBuilder builder)
    {
        setName("zb-non-blocking-task-runner-" + id);
        this.state = ActorThreadState.NEW;
        this.threadId = id;
        this.clock = builder.getActorClock() != null ? builder.getActorClock() : new DefaultActorClock();
        this.timerJobQueue = new ActorTimerQueue(this.clock);
        this.actorTaskRunners = builder.getActorThreads();

        final double[] priorityQuotas = builder.getPriorityQuotas();
        final int priorityCount = priorityQuotas.length;
        this.metrics = new ActorRunnerMetrics(String.format("thread-%d", id), builder.getCountersManager(), builder.getPriorityQuotas().length);
        this.priorityScheduler = new PriorityScheduler(this::getNextTaskByPriority, priorityQuotas);
        this.taskQueues = new ActorTaskQueue[priorityCount];
        for (int i = 0; i < taskQueues.length; i++)
        {
            taskQueues[i] = new ActorTaskQueue();
        }
    }

    @Override
    public void run()
    {
        idleStrategy.init();

        while (state == ActorThreadState.RUNNING)
        {
            try
            {
                doWork();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        state = ActorThreadState.TERMINATED;

        terminationFuture.complete(null);
    }

    private void doWork()
    {
        clock.update();

        timerJobQueue.processExpiredTimers(clock);

        currentTask = priorityScheduler.getNextTask(clock);

        if (currentTask != null)
        {
            executeCurrentTask();
        }
        else
        {
            idleStrategy.onIdle();
        }
    }

    private void executeCurrentTask()
    {
        MDC.put("actor-name", currentTask.getName());
        idleStrategy.onTaskExecuted();
        metrics.incrementTaskExecutionCount(currentTask.getPriority());

        final long nanoTimeBeforeTask = clock.getNanoTime();

        boolean resubmit = false;

        try
        {
            resubmit = currentTask.execute(this);
        }
        catch (Exception e)
        {
            // TODO: check interrupt state?
            // TODO: Handle Exception
            e.printStackTrace();

            // TODO: resubmit on exception?
//                resubmit = true;
        }
        finally
        {
            MDC.remove("actor-name");

            clock.update();
            final long taskExecutionTime = clock.getNanoTime() - nanoTimeBeforeTask;

            if (currentTask.isCollectTaskMetrics())
            {
                currentTask.reportExecutionTime(taskExecutionTime);
            }

            if (SHOULD_ENABLE_JUMBO_TASK_DETECTION)
            {
                if (TASK_MAX_EXECUTION_TIME_NANOS < taskExecutionTime)
                {
                    currentTask.warnMaxTaskExecutionTimeExceeded(taskExecutionTime);
                }
            }
        }

        if (resubmit)
        {
            submit(currentTask);
        }
    }

    protected class ActorTaskRunnerIdleStrategy
    {
        final BackoffIdleStrategy backoff = new BackoffIdleStrategy(100, 100, 1, TimeUnit.MILLISECONDS.toNanos(1));
        boolean isIdle;

        long idleTimeStart;
        long busyTimeStart;

        void init()
        {
            isIdle = true;
            idleTimeStart = System.nanoTime();
        }

        protected void onIdle()
        {
            if (!isIdle)
            {
                clock.update();
                idleTimeStart = clock.getNanoTime();
                metrics.recordRunnerBusyTime(idleTimeStart - busyTimeStart);
                isIdle = true;
            }

            backoff.idle();
        }


        protected void onTaskExecuted()
        {
            backoff.reset();

            if (isIdle)
            {
                busyTimeStart = clock.getNanoTime();
                metrics.recordRunnerIdleTime(busyTimeStart - idleTimeStart);
                isIdle = false;
            }
        }
    }

    /**
     * Attempts to acquire the next task for the specified priority by first
     * attempting to pop it from the local queue and if unavailable, attempting
     * to steal it from another thread's queue.
     *
     * @param priority
     *            the priority of the task to acquire
     * @return the acquired task or null if no task for the specified priority
     *         is available.
     */
    private ActorTask getNextTaskByPriority(int priority)
    {
        ActorTask nextTask = taskQueues[priority].pop();

        if (nextTask == null)
        {
            nextTask = trySteal(priority);
        }

        return nextTask;
    }

    /**
     * Work stealing: when this runner (aka. the "thief") has no more tasks to run, it attempts to take ("steal")
     * a task from another runner (aka. the "victim").
     *<p>
     * Work stealing is a mechanism for <em>load balancing</em>: it relies upon the assumption that there is more
     * work to do than there is resources (threads) to run it.
     */
    private ActorTask trySteal(int priority)
    {
        /*
         * This implementation uses a random offset into the runner array. The idea is to
         *
         * a) reduce probability for contention in situations where we have multiple runners
         *    (threads) trying to steal work at the same time: if they all started at the same
         *    offset, they would all look at the same runner as potential victim and contend
         *    on it's job queue
         *
         * b) to make sure a runner does not always look at the same other runner first and by
         *    this potentially increase the probability to find work on the first attempt
         *
         * However, the calculation of the random and the handling also needs additional compute time.
         * Experimental verification of the effectiveness of the optimization has not been conducted yet.
         * Also, the optimization only makes sense if the system uses at least 3 runners.
         */
        final int offset = ThreadLocalRandom.current().nextInt(actorTaskRunners.length);

        for (int i = offset; i < offset + actorTaskRunners.length; i++)
        {
            final int runnerId = i % actorTaskRunners.length;

            if (runnerId != this.threadId)
            {
                final ActorThread victim = actorTaskRunners[runnerId];
                final ActorTask stolenActor = victim.taskQueues[priority].trySteal();

                if (stolenActor != null)
                {
                    metrics.incrementTaskStealCount();
                    return stolenActor;
                }
            }
        }

        return null;
    }

    /**
     * Submits an actor task to this thread. Can be called by any thread.
     */
    public void submit(ActorTask task)
    {
        task.state = ActorState.QUEUED;
        taskQueues[task.getPriority()].append(task);
    }

    /**
     * Must be called from this thread, schedules a job to be run later.
     */
    public void scheduleTimer(TimerSubscription timer)
    {
        timerJobQueue.schedule(timer, clock);
    }

    /**
<<<<<<< HEAD:util/src/main/java/io/zeebe/util/sched/ActorTaskRunner.java
     * Must be called from this thread, remove a scheduled job.
     */
    public void removeTimer(TimerSubscription timer)
    {
        timerJobQueue.remove(timer);
    }

    /**
     * Returns the current {@link ActorTaskRunner} or null if the current thread is not
     * an {@link ActorTaskRunner} thread.
=======
     * Returns the current {@link ActorThread} or null if the current thread is not
     * an {@link ActorThread}.
>>>>>>> feat(priorities): initial implementation:util/src/main/java/io/zeebe/util/sched/ActorThread.java
     *
     * @return the current {@link ActorThread} or null
     */
    public static ActorThread current()
    {
        /*
         * Yes, we could work with a thread-local. Except thread locals are slow as f***
         * since they are kept in a map datastructure on the current thread.
         * This implementation takes advantage of the fact that ActorTaskRunner extends Thread
         * itself. If we can cast down, the current thread is the current ActorTaskRunner.
         */
        try
        {
            return (ActorThread) Thread.currentThread();
        }
        catch (ClassCastException e)
        {
            return null;
        }
    }

    public ActorJob newJob()
    {
        return jobPool.nextJob();
    }

    void recycleJob(ActorJob j)
    {
        jobPool.reclaim(j);
    }

    public int getRunnerId()
    {
        return threadId;
    }

    public ActorRunnerMetrics getMetrics()
    {
        return metrics;
    }

    @Override
    public void start()
    {
        if (UNSAFE.compareAndSwapObject(this, STATE_OFFSET, ActorThreadState.NEW, ActorThreadState.RUNNING))
        {
            super.start();
        }
        else
        {
            throw new IllegalStateException("Cannot start runner, not in state 'NEW'.");
        }
    }

    public CompletableFuture<Void> close()
    {
        if (UNSAFE.compareAndSwapObject(this, STATE_OFFSET, ActorThreadState.RUNNING, ActorThreadState.TERMINATING))
        {
            return terminationFuture;
        }
        else
        {
            throw new IllegalStateException("Cannot stop runner, not in state 'RUNNING'.");
        }
    }

    public enum ActorThreadState
    {
        NEW,
        RUNNING,
        TERMINATING,
        TERMINATED // runner is not reusable
    }

    public ActorJob getCurrentJob()
    {
        final ActorTask task = getCurrentTask();

        if (task != null)
        {
            return task.currentJob;
        }

        return null;
    }

    public ActorTask getCurrentTask()
    {
        return currentTask;
    }

    public ActorClock getClock()
    {
        return clock;
    }

}
