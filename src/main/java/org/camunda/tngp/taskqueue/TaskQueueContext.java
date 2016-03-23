package org.camunda.tngp.taskqueue;

import org.camunda.tngp.log.Log;
import org.camunda.tngp.transport.protocol.async.AsyncProtocolContext;

public class TaskQueueContext extends AsyncProtocolContext
{
    protected Log log;

    public Log getLog()
    {
        return log;
    }

    public void setLog(Log log)
    {
        this.log = log;
        setAsyncWorkBuffer(log.getWriteBuffer());
    }

}
