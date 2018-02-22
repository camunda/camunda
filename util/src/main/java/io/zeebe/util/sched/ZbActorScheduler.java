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

import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.metrics.SchedulerMetrics;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.ConcurrentCountersManager;

public class ZbActorScheduler
{
    private final AtomicReference<SchedulerState> state = new AtomicReference<>();
    private final ActorExecutor actorTaskExecutor;
    private final ConcurrentCountersManager countersManager;

    public ZbActorScheduler(ActorSchedulerBuilder builder)
    {
        state.set(SchedulerState.NEW);
        actorTaskExecutor = builder.getActorExecutor();
        countersManager = builder.getCountersManager();
    }

    /**
     * Submits an actor to the scheduler. Does not collect task metrics (see
     * {@link #submitActor(ZbActor, boolean)}.
     *
     * @param actor
     *            the actor to submit
     */
    public void submitActor(ZbActor actor)
    {
        submitActor(actor, false);
    }

    /**
     * Submits a new actor to the scheduler.
     *
     * @param actor
     *            the actor to submit
     * @param collectTaskMetrics
     *            controls whether metrics should be written for this actor. This
     *            has the overhead cost (time & memory) for allocating the
     *            counters which are used to record the metrics. Generally,
     *            metrics should not be recorded for short-lived tasks but only
     *            make sense for long-lived tasks where a small overhead when
     *            initially submitting the actor is acceptable.
     *
     */
    public void submitActor(ZbActor actor, boolean collectTaskMetrics)
    {
        actorTaskExecutor.submit(actor.actor.task, collectTaskMetrics);
    }

    public void start()
    {
        if (state.compareAndSet(SchedulerState.NEW, SchedulerState.RUNNING))
        {
            actorTaskExecutor.start();
        }
        else
        {
            throw new IllegalStateException("Cannot start scheduler already started.");
        }
    }

    public Future<Void> stop()
    {
        if (state.compareAndSet(SchedulerState.RUNNING, SchedulerState.TERMINATING))
        {

            return actorTaskExecutor.closeAsync()
                .thenRun(() ->
                {
                    state.set(SchedulerState.TERMINATED);
                });
        }
        else
        {
            throw new IllegalStateException("Cannot stop scheduler not running");
        }
    }

    public void dumpMetrics(PrintStream ps)
    {
        SchedulerMetrics.printMetrics(countersManager, ps);
    }

    public static ActorSchedulerBuilder newActorScheduler()
    {
        return new ActorSchedulerBuilder();
    }

    public static ZbActorScheduler newDefaultActorScheduler()
    {
        return new ActorSchedulerBuilder().build();
    }

    public static class ActorSchedulerBuilder
    {
        private ActorClock actorClock;
        private ConcurrentCountersManager countersManager;
        private int actorThreadCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        private double[] priorityQuotas = new double[] { 0.60, 0.30, 0.10 };
        private ThreadAssignmentStrategy threadAssignmentStrategy;
        private ActorThread[] actorThreads;
        private ActorThreadFactory actorThreadFactory;
        private ThreadPoolExecutor blockingTasksRunner;
        private Duration blockingTasksShutdownTime = Duration.ofSeconds(15);
        private ActorExecutor actorExecutor;

        public ActorSchedulerBuilder setActorClock(ActorClock actorClock)
        {
            this.actorClock = actorClock;
            return this;
        }

        public ActorSchedulerBuilder setCountersManager(ConcurrentCountersManager countersManager)
        {
            this.countersManager = countersManager;
            return this;
        }

        public ActorSchedulerBuilder setActorThreadCount(int actorThreadCount)
        {
            this.actorThreadCount = actorThreadCount;
            return this;
        }

        public ActorSchedulerBuilder setPriorityQuotas(double[] priorityQuotas)
        {
            this.priorityQuotas = priorityQuotas;
            return this;
        }

        public ActorSchedulerBuilder setThreadAssignmentStrategy(ThreadAssignmentStrategy threadAssignmentStrategy)
        {
            this.threadAssignmentStrategy = threadAssignmentStrategy;
            return this;
        }

        public ActorSchedulerBuilder setActorThreadFactory(ActorThreadFactory actorThreadFactory)
        {
            this.actorThreadFactory = actorThreadFactory;
            return this;
        }

        public ActorSchedulerBuilder setBlockingTasksRunner(ThreadPoolExecutor blockingTasksRunner)
        {
            this.blockingTasksRunner = blockingTasksRunner;
            return this;
        }

        public ActorSchedulerBuilder setBlockingTasksShutdownTime(Duration blockingTasksShutdownTime)
        {
            this.blockingTasksShutdownTime = blockingTasksShutdownTime;
            return this;
        }

        public ActorSchedulerBuilder setActorExecutor(ActorExecutor actorExecutor)
        {
            this.actorExecutor = actorExecutor;
            return this;
        }

        public ActorClock getActorClock()
        {
            return actorClock;
        }

        public ConcurrentCountersManager getCountersManager()
        {
            return countersManager;
        }

        public int getActorThreadCount()
        {
            return actorThreadCount;
        }

        public double[] getPriorityQuotas()
        {
            return priorityQuotas;
        }

        public ThreadAssignmentStrategy getThreadAssignmentStrategy()
        {
            return threadAssignmentStrategy;
        }

        public ActorThread[] getActorThreads()
        {
            return actorThreads;
        }

        public ActorThreadFactory getActorThreadFactory()
        {
            return actorThreadFactory;
        }

        public ThreadPoolExecutor getBlockingTasksRunner()
        {
            return blockingTasksRunner;
        }

        public Duration getBlockingTasksShutdownTime()
        {
            return blockingTasksShutdownTime;
        }

        public ActorExecutor getActorExecutor()
        {
            return actorExecutor;
        }

        private void initCountersManager()
        {
            if (countersManager == null)
            {
                final UnsafeBuffer valueBuffer = new UnsafeBuffer(new byte[64 * 1024]);
                final UnsafeBuffer labelBuffer = new UnsafeBuffer(new byte[valueBuffer.capacity() * 2 + 1]);
                countersManager = new ConcurrentCountersManager(labelBuffer, valueBuffer);
            }
        }

        private void initThreadAssignmentStrategy()
        {
            if (threadAssignmentStrategy == null)
            {
                threadAssignmentStrategy = new RandomThreadAssignmentStrategy(actorThreadCount);
            }
        }

        private void initActorThreadFactory()
        {
            if (actorThreadFactory == null)
            {
                actorThreadFactory = new DefaultActorThreadFactory();
            }
        }

        private void initActorThreads()
        {
            actorThreads = new ActorThread[actorThreadCount];

            for (int i = 0; i < actorThreadCount; i++)
            {
                actorThreads[i] = actorThreadFactory.newThread(i, this);
            }
        }

        private void initBlockingTaskRunner()
        {
            if (blockingTasksRunner == null)
            {
                blockingTasksRunner = new ThreadPoolExecutor(1,
                    Integer.MAX_VALUE,
                    60L,
                    TimeUnit.SECONDS,
                    new SynchronousQueue<>(),
                    new BlockingTasksThreadFactory());
            }
        }

        private void initActorExecutor()
        {
            if (actorExecutor == null)
            {
                actorExecutor = new ActorExecutor(actorThreads,
                    blockingTasksRunner,
                    countersManager,
                    threadAssignmentStrategy,
                    blockingTasksShutdownTime);
            }
        }

        public ZbActorScheduler build()
        {
            initCountersManager();
            initThreadAssignmentStrategy();
            initActorThreadFactory();
            initActorThreads();
            initBlockingTaskRunner();
            initActorExecutor();

            return new ZbActorScheduler(this);
        }
    }

    public interface ActorThreadFactory
    {
        ActorThread newThread(int runnerId, ActorSchedulerBuilder builder);
    }

    public static class DefaultActorThreadFactory implements ActorThreadFactory
    {
        @Override
        public ActorThread newThread(int runnerId, ActorSchedulerBuilder builder)
        {
            return new ActorThread(runnerId, builder);
        }
    }

    public static class BlockingTasksThreadFactory implements ThreadFactory
    {
        final AtomicLong idGenerator = new AtomicLong();

        @Override
        public Thread newThread(Runnable r)
        {
            final Thread thread = new Thread(r);
            thread.setName("zb-blocking-task-runner-" + idGenerator.incrementAndGet());
            return thread;
        }
    }

    public interface ThreadAssignmentStrategy
    {
        ActorThread nextRunner(ActorThread[] runners);
    }

    public static class RandomThreadAssignmentStrategy implements ThreadAssignmentStrategy
    {
        private final int numOfRunnerThreads;

        RandomThreadAssignmentStrategy(int numOfRunnerThreads)
        {
            this.numOfRunnerThreads = numOfRunnerThreads;
        }

        @Override
        public ActorThread nextRunner(ActorThread[] runners)
        {
            final int runnerOffset = ThreadLocalRandom.current().nextInt(numOfRunnerThreads);

            return runners[runnerOffset];
        }
    }

    private enum SchedulerState
    {
        NEW,
        RUNNING,
        TERMINATING,
        TERMINATED // scheduler is not reusable
    }
}
