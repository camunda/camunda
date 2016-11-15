package org.camunda.tngp.broker.log;

import org.camunda.tngp.logstreams.EventLogger;
import org.camunda.tngp.logstreams.LogStream;

public class LogWriter
{
    protected final EventLogger logEntryWriter = new EventLogger();
    protected final LogStream stream;


    public LogWriter(LogStream stream)
    {
        this.stream = stream;
    }

    public long write(LogEntryWriter<?, ?> writer)
    {
        logEntryWriter.wrap(stream);
        logEntryWriter.valueWriter(writer);
        logEntryWriter.positionAsKey();
        return logEntryWriter.tryWrite();
    }
}
