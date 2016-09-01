package org.camunda.tngp.broker.log;

import org.camunda.tngp.log.Log;

public class LogWriter
{

    protected final org.camunda.tngp.log.LogEntryWriter logEntryWriter = new org.camunda.tngp.log.LogEntryWriter();
    protected final Log log;


    public LogWriter(Log log)
    {
        this.log = log;
    }

    public long write(LogEntryWriter<?, ?> writer)
    {
        return logEntryWriter.write(log, writer);
    }
}
