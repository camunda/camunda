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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.zeebe.util.sched.channel.ConcurrentQueueChannel;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.junit.*;

public class ChannelConsumerTest
{
    @Rule
    public final ActorSchedulerRule schedulerRule = new ActorSchedulerRule(3);

    final CountDownLatch latch = new CountDownLatch(1);

    final ConcurrentQueueChannel<Object> ch = new ConcurrentQueueChannel<>(new ManyToOneConcurrentArrayQueue<>(32));

    class ProducerActor extends ZbActor
    {
        Runnable sendData = this::sendData;
        Object payload = new Object();

        @Override
        protected void onActorStarted()
        {
            actor.run(sendData);
        }

        protected void sendData()
        {
            while (ch.offer(payload))
            {
                // spin
            }
            actor.yield();
            actor.run(sendData);
        }
    }

    class ConsumerActor extends ZbActor
    {
        int count = 0;

        @Override
        protected void onActorStarted()
        {
            actor.consume(ch, () ->
            {
                while (ch.poll() != null)
                {
                    if (++count == 10_000_000)
                    {
                        latch.countDown();
                    }
                }
            });
        }
    }

    @Test
    public void testRunMultipleTimesConcurrent() throws InterruptedException
    {
        schedulerRule.submitActor(new ProducerActor());
        schedulerRule.submitActor(new ConsumerActor());

        if (!latch.await(5, TimeUnit.MINUTES))
        {
            Assert.fail();
        }

        final ZbActorScheduler actorScheduler = schedulerRule.get();
        actorScheduler.dumpMetrics(System.out);
    }

}
