package org.camunda.tngp.broker.log;

import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.ReadableLogEntry;
import org.camunda.tngp.util.buffer.BufferReader;

import static org.camunda.tngp.broker.log.LogEntryHandler.*;

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
        int handlerResult = CONSUME_ENTRY_RESULT;

        while (handlerResult != FAILED_ENTRY_RESULT && workCount < cycles && logReader.hasNext())
        {
            final ReadableLogEntry nextEntry = logReader.next();
            nextEntry.readValue(bufferReader);
            handlerResult = entryHandler.handle(nextEntry.getPosition(), bufferReader);

            if (handlerResult == POSTPONE_ENTRY_RESULT)
            {
                logReader.seek(logReader.getPosition());
            }

            ++workCount;
        }


        return workCount;
    }

    /**
     * @param position is exclusive
     */
    public int doWorkUntil(long position)
    {
        int workCount = 0;

        while (logReader.hasNext())
        {
            final ReadableLogEntry nextEntry = logReader.next();

            if (nextEntry.getPosition() < position)
            {
                nextEntry.readValue(bufferReader);
                entryHandler.handle(nextEntry.getPosition(), bufferReader);
                ++workCount;
            }
            else
            {
                break;
            }
        }

        return workCount;
    }

    public void setLogReader(LogReader logReader)
    {
        this.logReader = logReader;
    }
}
