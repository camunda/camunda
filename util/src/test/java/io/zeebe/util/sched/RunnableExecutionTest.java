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

import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.*;

public class RunnableExecutionTest
{
    @Rule
    public final ActorSchedulerRule schedulerRule = new ActorSchedulerRule(3);

    @Test
    public void testRunOnce() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1);

        schedulerRule.submitActor(new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                actor.run(this::someMethod);
            }

            void someMethod()
            {
                latch.countDown();
            }
        });

        if (!latch.await(5, TimeUnit.SECONDS))
        {
            fail("onActorStarted() never called");
        }
    }

    @Test
    public void testRunMultipleTimes() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(100);

        schedulerRule.submitActor(new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                final Runnable method = this::someMethod;

                for (int i = 0; i < 100; i++)
                {
                    actor.run(method);
                }
            }

            void someMethod()
            {
                latch.countDown();
            }
        });

        if (!latch.await(10, TimeUnit.MINUTES))
        {
            fail("onActorStarted() never called");
        }
    }

    @Test
    public void testRunUntilDone() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1);

        schedulerRule.submitActor(new ZbActor()
        {
            int innerIterationCount = 0;

            @Override
            protected void onActorStarted()
            {
                actor.runUntilDone(this::someMethod);
            }

            void someMethod()
            {
                if (++innerIterationCount == 10)
                {
                    actor.done();
                }
            }

            @Override
            protected void onActorClosing()
            {
                latch.countDown();
            }
        });

        if (!latch.await(10, TimeUnit.MINUTES))
        {
            fail("onActorStarted() never called");
        }
    }


    @Test
    public void testRunMultipleTimesConcurrent() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(100_000);

        for (int actorCount = 0; actorCount < 100_000; actorCount++)
        {
            schedulerRule.submitActor(new ZbActor()
            {
                final Runnable method = this::someMethod;

                int innerIterationCount = 0;

                @Override
                protected void onActorStarted()
                {
                    actor.run(method);
                }

                void someMethod()
                {
                    innerIterationCount++;

                    if (innerIterationCount < 10_000)
                    {
                        actor.run(method);
                    }
                    else
                    {
                        latch.countDown();
                    }
                }
            });
        }

        if (!latch.await(10, TimeUnit.MINUTES))
        {
            fail("onActorStarted() never called");
        }

        schedulerRule.get().dumpMetrics(System.out);
    }


    @Test
    public void testRunBlockingMultipleTimesConcurrent() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(100_000);

        for (int actorCount = 0; actorCount < 100_000; actorCount++)
        {
            schedulerRule.submitActor(new ZbActor()
            {
                final Runnable method = this::someMethod;

                @Override
                protected void onActorStarted()
                {
                    actor.runBlocking(method);
                }

                void someMethod()
                {
                    if (latch.getCount() == 0)
                    {
                        Assert.fail("fail");
                    }
                    latch.countDown();
                }
            });
        }

        if (!latch.await(10, TimeUnit.MINUTES))
        {
            fail("onActorStarted() never called");
        }

        schedulerRule.get().dumpMetrics(System.out);
    }

}
