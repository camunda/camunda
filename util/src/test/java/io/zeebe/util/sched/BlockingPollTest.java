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

import java.util.concurrent.*;

import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.agrona.LangUtil;
import org.junit.*;

public class BlockingPollTest
{
    @Rule
    public final ActorSchedulerRule schedulerRule = new ActorSchedulerRule(3);

    @Test
    public void testSingleBlockingAction() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1000);

        final Runnable blockingTask = () ->
        {
            try
            {
                Thread.sleep(1);
            }
            catch (Exception e)
            {
                LangUtil.rethrowUnchecked(e);
            }
        };

        final Runnable nonBlockingTask = () ->
        {
            latch.countDown();
        };

        schedulerRule.submitActor(ZbActor.wrap((a) ->
        {
            a.pollBlocking(blockingTask, nonBlockingTask);
        }));

        if (!latch.await(1, TimeUnit.MINUTES))
        {
            Assert.fail("error");
        }
    }

    @Test
    public void testMultipleBlockingActions() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1000 * 100);

        final Runnable blockingTask = () ->
        {
            try
            {
                Thread.sleep(1);
            }
            catch (Exception e)
            {
                LangUtil.rethrowUnchecked(e);
            }
        };

        final Runnable nonBlockingTask = () ->
        {
            latch.countDown();
        };

        for (int i = 0; i < 100; i++)
        {
            schedulerRule.submitActor(ZbActor.wrap((a) ->
            {
                a.pollBlocking(blockingTask, nonBlockingTask);
            }));
        }

        if (!latch.await(1, TimeUnit.MINUTES))
        {
            Assert.fail("error");
        }
    }

}