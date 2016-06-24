package org.camunda.tngp.log;

import org.camunda.tngp.util.buffer.BufferWriter;

public class LogWriter
{

    protected final LogEntryWriter logEntryWriter = new LogEntryWriter();
    protected final Log log;


    public LogWriter(Log log)
    {
        this.log = log;
    }

    public long write(BufferWriter writer)
    {
        return logEntryWriter.write(log, writer);
    }
}
