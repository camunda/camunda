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
package io.zeebe.servicecontainer.impl;

import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.concurrent.IdleStrategy;

public class WaitingIdleStrategy implements IdleStrategy
{
    protected int idleCount = 0;
    protected AtomicBoolean isWaiting = new AtomicBoolean();

    @Override
    public void idle(int workCount)
    {
        if (workCount == 0)
        {
            if (idleCount > 10000)
            {
                idle();
            }
            else
            {
                ++idleCount;
                Thread.yield();
            }
        }
        else
        {
            idleCount = 0;
        }
    }

    @Override
    public void idle()
    {
        try
        {
            synchronized (this)
            {
                isWaiting.set(true);
                wait(1000);
            }
        }
        catch (InterruptedException e)
        {
            // ignore
        }
    }

    @Override
    public void reset()
    {

    }

    public void signalWorkAvailable()
    {
        if (isWaiting.compareAndSet(true, false))
        {
            synchronized (this)
            {
                notify();
            }
        }
    }
}
