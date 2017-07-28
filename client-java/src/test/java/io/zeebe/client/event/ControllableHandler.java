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
package io.zeebe.client.event;

import java.util.concurrent.atomic.AtomicInteger;

public class ControllableHandler implements TopicEventHandler
{

    protected Object monitor = new Object();
    protected boolean shouldWait = true;
    protected boolean isWaiting = false;
    protected AtomicInteger numHandledEvents = new AtomicInteger(0);

    @Override
    public void handle(TopicEvent event) throws Exception
    {
        if (shouldWait)
        {
            synchronized (monitor)
            {
                isWaiting = true;
                monitor.wait();
                isWaiting = false;
            }
        }

        numHandledEvents.incrementAndGet();
    }

    public int getNumHandledEvents()
    {
        return numHandledEvents.get();
    }

    public void signal()
    {
        synchronized (monitor)
        {
            monitor.notify();
        }
    }

    public void disableWait()
    {
        shouldWait = false;
    }

    public boolean isWaiting()
    {
        return isWaiting;
    }
}
