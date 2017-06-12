package org.camunda.tngp.client.event;

import java.util.concurrent.atomic.AtomicInteger;

public class ControllableHandler implements TopicEventHandler
{

    protected Object monitor = new Object();
    protected boolean shouldWait = true;
    protected boolean isWaiting = false;
    protected AtomicInteger numHandledEvents = new AtomicInteger(0);

    @Override
    public void handle(EventMetadata metadata, TopicEvent event) throws Exception
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
