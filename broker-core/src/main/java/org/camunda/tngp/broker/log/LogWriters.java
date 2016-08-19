package org.camunda.tngp.broker.log;

public interface LogWriters
{
    void writeToCurrentLog(LogEntryWriter<?, ?> logWriter);

    void writeToLog(int logId, LogEntryWriter<?, ?> logWriter);

    void writeToAllLogs(LogEntryWriter<?, ?> logWriter);
}
