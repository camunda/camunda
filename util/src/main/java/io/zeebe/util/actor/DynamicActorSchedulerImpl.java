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
package io.zeebe.util.actor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import io.zeebe.util.Loggers;
import org.slf4j.Logger;

public class DynamicActorSchedulerImpl implements ActorScheduler
{
    public static final Logger LOG = Loggers.ACTOR_LOGGER;

    private final ExecutorService executorService;
    private final Thread schedulerThread;

    private final ActorRunner[] runners;
    private final ActorSchedulerRunnable schedulerRunnable;

    public DynamicActorSchedulerImpl(int threadCount, Supplier<ActorRunner> runnerFactory, Function<ActorRunner[], ActorSchedulerRunnable> schedulerFactory)
    {
        runners = createTaskRunners(threadCount, runnerFactory);
        schedulerRunnable = schedulerFactory.apply(runners);

        executorService = Executors.newFixedThreadPool(threadCount, new RunnerThreadFactory());
        for (int r = 0; r < runners.length; r++)
        {
            executorService.execute(runners[r]);
        }

        schedulerThread = new Thread(schedulerRunnable, "actor-scheduler");
        schedulerThread.start();
    }

    private static ActorRunner[] createTaskRunners(int runnerCount, Supplier<ActorRunner> factory)
    {
        final ActorRunner[] runners = new ActorRunner[runnerCount];

        for (int i = 0; i < runnerCount; i++)
        {
            runners[i] = factory.get();
        }
        return runners;
    }

    @Override
    public ActorReference schedule(Actor actor)
    {
        return schedulerRunnable.schedule(actor);
    }

    @Override
    public void close()
    {
        executorService.shutdown();

        schedulerRunnable.close();

        for (int r = 0; r < runners.length; r++)
        {
            final ActorRunner runner = runners[r];
            runner.close();
        }

        try
        {
            schedulerThread.join(1000);
        }
        catch (Exception e)
        {
            LOG.error("Actor Scheduler did not exit within 1 second");
        }

        try
        {
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            LOG.error("Actor Runners did not exit within 10 seconds");
        }
    }

    @Override
    public String toString()
    {
        return schedulerRunnable.toString();
    }

    private final class RunnerThreadFactory implements ThreadFactory
    {
        private final AtomicInteger t = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r)
        {
            final String name = "actor-runner-" + t.incrementAndGet();

            return new Thread(r, name);
        }
    }

}
