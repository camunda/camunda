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

import io.zeebe.util.sched.ActorTaskRunner;
import io.zeebe.util.sched.ZbActorScheduler;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.metrics.ActorRunnerMetrics;
import org.agrona.concurrent.status.CountersManager;
import org.junit.Assert;

public class ControlledActorScheduler extends ZbActorScheduler
{
    private ControlledActorTaskRunner controlledActorTaskRunner;

    public ControlledActorScheduler(CountersManager countersManager)
    {
        super(1, countersManager);
        blockingTaskShutdownTime = Duration.ofSeconds(0);
    }

    @Override
    protected ActorTaskRunner createTaskRunner(int i, ActorRunnerMetrics metrics, ActorClock clock)
    {
        controlledActorTaskRunner = new ControlledActorTaskRunner(this, i, metrics, clock);
        return controlledActorTaskRunner;
    }

    public void workUntilDone()
    {
        controlledActorTaskRunner.workUntilDone();
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

        Assert.fail("could not complete " + i + " blocking tasks withing 5s");
    }

}
