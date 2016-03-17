package org.camunda.tngp.log;

import org.camunda.tngp.dispatcher.Dispatcher;

public class Log
{
    protected final Dispatcher writeBuffer;

    public Log(final LogContext logContext)
    {
        this.writeBuffer = logContext.getWriteBuffer();
    }

    public Dispatcher getWriteBuffer()
    {
        return writeBuffer;
    }
}
