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

import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;

public class ConditionTest
{
    @Rule
    public final ActorSchedulerRule schedulerRule = new ActorSchedulerRule(3);

    private CountDownLatch latch = new CountDownLatch(1);

    class CounterActor extends Actor
    {
        private volatile int counter = 0;

        private ActorCondition counterIncremented;

        @Override
        protected void onActorStarted()
        {
            counterIncremented = actor.onCondition("counterIncremented", this::monitorCounter);
        }

        void monitorCounter()
        {
            if (counter >= 10_000_000)
            {
                latch.countDown();
            }
        }

        public void incrementCounter()
        {
            ++counter;
            counterIncremented.signal();
        }
    }

    class Producer extends Actor
    {
        final CounterActor counterActor;
        final Runnable incrementCounter = this::incrementCounter;

        Producer(CounterActor counterActor)
        {
            this.counterActor = counterActor;
        }

        @Override
        protected void onActorStarted()
        {
            actor.run(incrementCounter);
        }

        void incrementCounter()
        {
            for (int i = 0; i < 1_000; i++)
            {
                counterActor.incrementCounter();
            }
            actor.yield();
            actor.run(incrementCounter);
        }
    }

    @Test
    public void testSingleWriter() throws InterruptedException
    {
        final CounterActor monitor = new CounterActor();
        schedulerRule.submitActor(monitor);
        while (monitor.counterIncremented == null)
        {
            // wait
        }
        schedulerRule.submitActor(new Producer(monitor));

        latch.await();
    }

    @Test
    public void testMultipleWriters() throws InterruptedException
    {
        final CounterActor monitor = new CounterActor();
        schedulerRule.submitActor(monitor);
        while (monitor.counterIncremented == null)
        {
            // wait
        }
        schedulerRule.submitActor(new Producer(monitor));
        schedulerRule.submitActor(new Producer(monitor));

        latch.await();
    }
}
