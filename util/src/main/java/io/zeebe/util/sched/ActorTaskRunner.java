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

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.clock.DefaultActorClock;
import io.zeebe.util.sched.metrics.ActorRunnerMetrics;
import org.agrona.UnsafeAccess;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.slf4j.MDC;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class ActorTaskRunner extends Thread
{
    static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

    private final ActorRunnerMetrics metrics;

    private volatile TaskRunnerState state;

    private static final long STATE_OFFSET;

    private final CompletableFuture<Void> terminationFuture = new CompletableFuture<Void>();

    private final Random localRandom = new Random();

    private final ActorClock clock;

    static
    {
        try
        {
            STATE_OFFSET = UNSAFE.objectFieldOffset(ActorTaskRunner.class.getDeclaredField("state"));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private final int runnerId;

    private final ZbActorScheduler scheduler;

    private final ActorTaskQueue taskQueue;

    private final ActorTimerQueue timerJobQueue;

    private final ActorJobPool jobPool = new ActorJobPool();

    protected ActorTaskRunnerIdleStrategy idleStrategy = new ActorTaskRunnerIdleStrategy();

    ActorTask currentTask;

    public ActorTaskRunner(ZbActorScheduler scheduler, int runnerId, ActorRunnerMetrics metrics, ActorClock clock)
    {
        setName("zb-non-blocking-task-runner-" + runnerId);
        this.scheduler = scheduler;
        this.runnerId = runnerId;
        this.metrics = metrics;
        this.state = TaskRunnerState.NEW;
        this.taskQueue = new ActorTaskQueue(runnerId);
        this.clock = clock != null ? clock : new DefaultActorClock();
        this.timerJobQueue = new ActorTimerQueue(this.clock);
    }

    @Override
    public void run()
    {
        idleStrategy.init();

        while (state == TaskRunnerState.RUNNING)
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

        state = TaskRunnerState.TERMINATED;

        terminationFuture.complete(null);
    }

    private void doWork()
    {
        clock.update();
        timerJobQueue.processExpiredTimers(clock);

        currentTask = taskQueue.pop();

        if (currentTask != null)
        {

            executeCurrentTask();
        }
        else
        {
            currentTask = trySteal();

            if (currentTask != null)
            {
                executeCurrentTask();
            }
            else
            {
                idleStrategy.idle();
            }
        }
    }

    private  void executeCurrentTask()
    {
        MDC.put("actor-name", currentTask.actor.getName());
        idleStrategy.onTaskExecute();
        metrics.incrementTaskExecutionCount();

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

        protected void idle()
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


        protected void onTaskExecute()
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
     * Work stealing: when this runner (aka. the "thief") has no more tasks to run, it attempts to take ("steal")
     * a task from another runner (aka. the "victim").
     *<p>
     * Work stealing in a mechanism for <em>load balancing</em>: it relies upon the assumption that there is more
     * work to do than there is resources (threads) to run it.
     */
    private ActorTask trySteal()
    {
        final ActorTaskRunner[] runners = scheduler.nonBlockingTasksRunners;

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
        final int offset = localRandom.nextInt(runners.length);

        for (int i = offset; i < offset + runners.length; i++)
        {
            final int runnerId = i % runners.length;

            if (runnerId != this.runnerId)
            {
                final ActorTaskRunner victim = runners[runnerId];
                final ActorTask stolenActor = victim.taskQueue.trySteal(taskQueue);

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
     * Submits an actor task to this runner. Can be called by any thread
     * The method appends the task to this runner's queue.
     */
    public void submit(ActorTask task)
    {
        task.state = ActorState.QUEUED;
        taskQueue.append(task);
    }

    /**
     * Must be called from this thread, schedules a job to be run later.
     */
    public void scheduleTimer(TimerSubscription timer)
    {
        timerJobQueue.schedule(timer, clock);
    }

    /**
     * Returns the current {@link ActorTaskRunner} or null if the current thread is not
     * an {@link ActorTaskRunner} thread.
     *
     * @return the current {@link ActorTaskRunner} or null
     */
    public static ActorTaskRunner current()
    {
        /*
         * Yes, we could work with a thread-local. Except thread locals are slow as f***
         * since they are kept in a map datastructure on the current thread.
         * This implementation takes advantage of the fact that ActorTaskRunner extends Thread
         * itself. If we can cast down, the current thread is the current ActorTaskRunner.
         */
        try
        {
            return (ActorTaskRunner) Thread.currentThread();
        }
        catch (ClassCastException e)
        {
            return null;
        }
    }

    ActorJob newJob()
    {
        return jobPool.nextJob();
    }

    void recycleJob(ActorJob j)
    {
        jobPool.reclaim(j);
    }

    public int getRunnerId()
    {
        return runnerId;
    }

    public ActorRunnerMetrics getMetrics()
    {
        return metrics;
    }

    @Override
    public void start()
    {
        if (UNSAFE.compareAndSwapObject(this, STATE_OFFSET, TaskRunnerState.NEW, TaskRunnerState.RUNNING))
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
        if (UNSAFE.compareAndSwapObject(this, STATE_OFFSET, TaskRunnerState.RUNNING, TaskRunnerState.TERMINATING))
        {
            return terminationFuture;
        }
        else
        {
            throw new IllegalStateException("Cannot stop runner, not in state 'RUNNING'.");
        }
    }

    public enum TaskRunnerState
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
