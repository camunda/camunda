package org.camunda.tngp.broker.log;

import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.util.buffer.BufferReader;

public class LogEntryProcessor<T extends BufferReader>
{
    protected LogReader logReader;
    protected T bufferReader;
    protected LogEntryHandler<T> entryHandler;

    public LogEntryProcessor(LogReader logReader, T bufferReader, LogEntryHandler<T> entryHandler)
    {
        this.bufferReader = bufferReader;
        this.logReader = logReader;
        this.entryHandler = entryHandler;
    }

    public int doWorkSingle()
    {
        return doWork(1);
    }

    public int doWork(final int cycles)
    {
        int workCount = 0;

        boolean hasNext;

        do
        {
            final long position = logReader.position();
            hasNext = logReader.read(bufferReader);
            if (hasNext)
            {
                entryHandler.handle(position, bufferReader);
                workCount++;
            }
            else
            {
                break;
            }
        } while (hasNext && workCount < cycles);

        return workCount;
    }

    public void setLogReader(LogReader logReader)
    {
        this.logReader = logReader;
    }
}
