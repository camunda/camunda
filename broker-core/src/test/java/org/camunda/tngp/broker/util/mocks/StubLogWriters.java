package org.camunda.tngp.broker.util.mocks;

import org.agrona.collections.Int2ObjectHashMap;
import org.camunda.tngp.broker.log.LogEntryWriter;
import org.camunda.tngp.broker.log.LogWriters;

public class StubLogWriters implements LogWriters
{
    protected int thisLogId;

    protected Int2ObjectHashMap<StubLogWriter> logWriters = new Int2ObjectHashMap<>();

    public StubLogWriters(int thisLogId)
    {
        this.thisLogId = thisLogId;
    }

    public StubLogWriters(int thisLogId, StubLogWriter thisLogWriter)
    {
        this(thisLogId);
        addWriter(thisLogId, thisLogWriter);
    }

    public void addWriter(int logId, StubLogWriter writer)
    {
        this.logWriters.put(logId, writer);
    }

    public StubLogWriter getWriter(int id)
    {
        return logWriters.get(id);
    }

    @Override
    public long writeToCurrentLog(LogEntryWriter<?, ?> logWriter)
    {
        return writeToLog(thisLogId, logWriter);
    }

    @Override
    public long writeToLog(int logId, LogEntryWriter<?, ?> logWriter)
    {
        final StubLogWriter stubLogWriter = logWriters.get(logId);

        if (stubLogWriter == null)
        {
            throw new RuntimeException("No writer for log " + logId);
        }

        return stubLogWriter.write(logWriter);

    }

    @Override
    public void writeToAllLogs(LogEntryWriter<?, ?> logWriter)
    {
        for (int logId : logWriters.keySet())
        {
            writeToLog(logId, logWriter);
        }

    }

    public int writtenEntries()
    {
        return logWriters.values().stream().mapToInt((w) -> w.size()).sum();
    }

}
