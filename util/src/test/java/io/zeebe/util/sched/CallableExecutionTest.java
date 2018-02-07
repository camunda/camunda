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

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import io.zeebe.util.sched.testing.ActorSchedulerRule;

public class CallableExecutionTest
{
    @Rule
    public final ActorSchedulerRule schedulerRule = new ActorSchedulerRule(3);

    static class PongActor extends ZbActor
    {
        @Override
        protected void onActorStarted()
        {
            actor.runAtFixedRate(Duration.ofMillis(1), () ->
            {
                // keepalive
            });
        }

        Future<Integer> onPing(int val)
        {
            return actor.call(() ->
            {
                return val + 1;
            });
        }
    }

    static class PingActor extends ZbActor
    {

        int count = 0;
        final CountDownLatch latch;

        PongActor pongActor;

        Runnable sendPing = this::sendPing;

        PingActor(PongActor pongActor, CountDownLatch latch)
        {
            this.pongActor = pongActor;
            this.latch = latch;
        }

        @Override
        protected void onActorStarted()
        {
            actor.run(sendPing);
        }

        void sendPing()
        {
            final Future<Integer> future = pongActor.onPing(count);
            actor.await(future, (r, t) ->
            {
                count = r;
                if (count == 1_000_000)
                {
                    latch.countDown();
                }
                else
                {
                    actor.run(sendPing);
                }
            });
        }
    }

    @Test
    public void testRunMultipleTimesConcurrent() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(10);
        final PongActor pongActor = new PongActor();

        final PingActor[] actors = new PingActor[(int) latch.getCount()];

        for (int i = 0; i < actors.length; i++)
        {
            actors[i] = new PingActor(pongActor, latch);
        }

        schedulerRule.submitActor(pongActor);
        for (PingActor pingActor : actors)
        {
            schedulerRule.submitActor(pingActor);
        }

        if (!latch.await(5, TimeUnit.MINUTES))
        {
            Assert.fail();
        }

        final ZbActorScheduler actorScheduler = schedulerRule.get();
        actorScheduler.dumpMetrics(System.out);
    }
}
