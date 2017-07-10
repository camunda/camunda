/**
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
package io.zeebe.servicecontainer.impl;

import java.util.LinkedList;
import java.util.List;

import org.junit.Assert;

public class ControlledServiceContainer extends ServiceContainerImpl
{
    protected List<Runnable> shortRunning = new LinkedList<>();

    @Override
    public void start()
    {
        state = ContainerState.OPEN;
    }

    public void doWorkUntilDone()
    {
        final int maxIterations = 10000;
        int iterations = 0;

        while (doWork() > 0)
        {
            if (iterations > maxIterations)
            {
                Assert.fail("max iterations exhausted.");
            }

            ++iterations;
        }
    }

    @Override
    public void executeShortRunning(Runnable runnable)
    {
        shortRunning.add(runnable);
    }

    public void executeAsyncActions()
    {
        while (!shortRunning.isEmpty())
        {
            shortRunning.remove(0).run();
        }
    }
}
