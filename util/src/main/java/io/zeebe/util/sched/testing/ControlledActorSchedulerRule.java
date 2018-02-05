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

import io.zeebe.util.sched.ZbActor;
import io.zeebe.util.sched.ZbActorScheduler;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.ConcurrentCountersManager;
import org.junit.rules.ExternalResource;

public class ControlledActorSchedulerRule extends ExternalResource
{
    private final ControlledActorScheduler actorScheduler;

    public ControlledActorSchedulerRule()
    {
        final UnsafeBuffer valueBuffer = new UnsafeBuffer(new byte[16 * 1024]);
        final UnsafeBuffer labelBuffer = new UnsafeBuffer(new byte[valueBuffer.capacity() * 2 + 1]);
        final ConcurrentCountersManager countersManager = new ConcurrentCountersManager(labelBuffer, valueBuffer);
        actorScheduler = new ControlledActorScheduler(countersManager);
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

    public void submitActor(ZbActor actor)
    {
        actorScheduler.submitActor(actor);
    }

    public ZbActorScheduler get()
    {
        return actorScheduler;
    }

    public void awaitBlockingTasksCompleted(int i)
    {
        actorScheduler.awaitBlockingTasksCompleted(i);
    }

    public void workUntilDone()
    {
        actorScheduler.workUntilDone();
    }
}
