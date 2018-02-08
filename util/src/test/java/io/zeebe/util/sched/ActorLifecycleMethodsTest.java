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

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import io.zeebe.util.TestUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;

public class ActorLifecycleMethodsTest
{
    @Rule
    public final ActorSchedulerRule schedulerRule = new ActorSchedulerRule();

    @Test
    public void testOnActorStartCalled() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1);

        schedulerRule.submitActor(new ZbActor()
        {
            @Override
            protected void onActorStarted()
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
    public void testOnActorClosingCalled() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1);

        schedulerRule.submitActor(new ZbActor()
        {
            @Override
            protected void onActorClosing()
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
    public void testActorCloseInternally() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1);

        schedulerRule.submitActor(new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                actor.runAtFixedRate(Duration.ofMillis(1), () ->
                {
                    actor.close();
                });
            }

            @Override
            protected void onActorClosing()
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
    public void testActorCloseExternally() throws InterruptedException, ExecutionException, TimeoutException
    {
        final ZbActor actor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                // subscription would normally prevent actor from closing
                actor.runAtFixedRate(Duration.ofMillis(1), () ->
                {
                    // no-op
                });
            }
        };

        schedulerRule.submitActor(actor);

        final Future<Void> future = actor.actor.close();

        future.get(5, TimeUnit.MINUTES);

    }

    @Test
    public void testReSubmitClosedActor() throws InterruptedException, ExecutionException, TimeoutException
    {
        final AtomicLong invocations = new AtomicLong(0);

        final ZbActor actor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                actor.runAtFixedRate(Duration.ofMillis(1), () ->
                {
                    invocations.incrementAndGet();
                });
            }
        };

        schedulerRule.submitActor(actor);

        Future<Void> future = actor.actor.close();
        future.get(5, TimeUnit.SECONDS);

        invocations.set(0);

        // submit actor again
        schedulerRule.submitActor(actor);

        TestUtil.waitUntil(() -> invocations.get() > 0);

        future = actor.actor.close();
        future.get(5, TimeUnit.SECONDS);
    }

}
