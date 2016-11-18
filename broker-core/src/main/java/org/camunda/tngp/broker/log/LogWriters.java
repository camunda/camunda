package org.camunda.tngp.broker.log;

public interface LogWriters
{
    long writeToCurrentLog(LogEntryWriter<?, ?> logWriter);

    long writeToLog(int logId, LogEntryWriter<?, ?> logWriter);

    void writeToAllLogs(LogEntryWriter<?, ?> logWriter);
}
