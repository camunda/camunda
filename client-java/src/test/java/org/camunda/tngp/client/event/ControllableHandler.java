package org.camunda.tngp.client.event;

public class ControllableHandler implements TopicEventHandler
{

    protected Object monitor = new Object();
    protected boolean isWaiting = false;

    @Override
    public void handle(EventMetadata metadata, TopicEvent event) throws Exception
    {
        synchronized (monitor)
        {
            isWaiting = true;
            monitor.wait();
            isWaiting = false;
        }
    }

    public void signal()
    {
        synchronized (monitor)
        {
            monitor.notify();
        }
    }

    public boolean isWaiting()
    {
        return isWaiting;
    }
}
