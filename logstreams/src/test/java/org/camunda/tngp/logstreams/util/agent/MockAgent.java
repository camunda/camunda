/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.logstreams.util.agent;

import static org.assertj.core.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.concurrent.Agent;

public class MockAgent implements Agent
{
    private final String name;
    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    private AtomicInteger invocations = new AtomicInteger(0);

    private boolean isClosed = false;

    public MockAgent(String name)
    {
        this.name = name;
    }

    @Override
    public int doWork() throws Exception
    {
        invocations.incrementAndGet();

        countDownLatch.countDown();
        return 0;
    }

    @Override
    public String roleName()
    {
        return name;
    }

    @Override
    public void onClose()
    {
        isClosed = true;
    }

    public boolean isClosed()
    {
        return isClosed;
    }

    public void waitUntilInvocation()
    {
        try
        {
            final boolean finished = countDownLatch.await(10, TimeUnit.SECONDS);
            if (!finished)
            {
                fail("timeout while waiting for an invokation");
            }
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    public int getInvocationCount()
    {
        return invocations.get();
    }

}
