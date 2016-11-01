package org.camunda.tngp.logstreams.impl;

public class WriteErrorEvent
{
    protected long failedPosition;

    protected volatile boolean isHandlerRecovered;

    public long getFailedPosition()
    {
        return failedPosition;
    }

}
