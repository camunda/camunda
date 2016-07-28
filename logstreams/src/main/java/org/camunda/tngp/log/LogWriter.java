package org.camunda.tngp.log;

import org.camunda.tngp.util.buffer.BufferWriter;

public class LogWriter
{

    protected final LogEntryWriter logEntryWriter = new LogEntryWriter();
    protected final Log log;
    protected final LogEntryWriteListener listener;

    public LogWriter(Log log)
    {
        this(log, null);
    }

    public LogWriter(Log log, LogEntryWriteListener listener)
    {
        this.log = log;
        this.listener = listener;
    }

    public long write(BufferWriter writer)
    {
        return logEntryWriter.write(log, writer, listener);
    }
}
