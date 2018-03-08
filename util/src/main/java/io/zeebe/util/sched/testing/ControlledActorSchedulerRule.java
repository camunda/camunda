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
package io.zeebe.util.sched.testing;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;

import io.zeebe.util.sched.*;
import io.zeebe.util.sched.ActorScheduler.ActorSchedulerBuilder;
import io.zeebe.util.sched.ActorScheduler.ActorThreadFactory;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.metrics.ActorThreadMetrics;
import org.junit.Assert;
import org.junit.rules.ExternalResource;

public class ControlledActorSchedulerRule extends ExternalResource
{
    private final ActorScheduler actorScheduler;
    private final ControlledActorThread controlledActorTaskRunner;
    private final ThreadPoolExecutor blockingTasksRunner;

    public ControlledActorSchedulerRule()
    {
        final ControlledActorThreadFactory actorTaskRunnerFactory = new ControlledActorThreadFactory();
        final ActorSchedulerBuilder builder = ActorScheduler.newActorScheduler()
                                                            .setCpuBoundActorThreadCount(1)
                                                            .setIoBoundActorThreadCount(0)
                                                            .setActorThreadFactory(actorTaskRunnerFactory)
                                                            .setBlockingTasksShutdownTime(Duration.ofSeconds(0));

        actorScheduler = builder.build();

        controlledActorTaskRunner = actorTaskRunnerFactory.controlledThread;
        blockingTasksRunner = builder.getBlockingTasksRunner();
    }

    @Override
    protected void before() throws Throwable
    {
        actorScheduler.start();
    }

    @Override
    protected void after()
    {
        actorScheduler.stop();
    }

    public ActorFuture<Void> submitActor(Actor actor)
    {
        return actorScheduler.submitActor(actor);
    }

    public ActorScheduler get()
    {
        return actorScheduler;
    }

    public void awaitBlockingTasksCompleted(int i)
    {
        final long currentTimeMillis = System.currentTimeMillis();

        while (System.currentTimeMillis() - currentTimeMillis < 5000)
        {
            final long completedTaskCount = blockingTasksRunner.getCompletedTaskCount();
            if (completedTaskCount >= i)
            {
                return;
            }
        }

        Assert.fail("could not complete " + i + " blocking tasks within 5s");
    }

    public void workUntilDone()
    {
        controlledActorTaskRunner.workUntilDone();
    }


    static class ControlledActorThreadFactory implements ActorThreadFactory
    {
        private ControlledActorThread controlledThread;

        @Override
        public ActorThread newThread(
                String name,
                int id,
                ActorThreadGroup threadGroup,
                TaskScheduler taskScheduler,
                ActorClock clock,
                ActorThreadMetrics metrics)
        {
            controlledThread = new ControlledActorThread(name, id, threadGroup, taskScheduler, clock, metrics);
            return controlledThread;
        }
    }
}
