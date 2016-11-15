package org.camunda.tngp.broker.log;

import static org.camunda.tngp.broker.log.LogEntryHandler.CONSUME_ENTRY_RESULT;
import static org.camunda.tngp.broker.log.LogEntryHandler.FAILED_ENTRY_RESULT;
import static org.camunda.tngp.broker.log.LogEntryHandler.POSTPONE_ENTRY_RESULT;

import org.camunda.tngp.logstreams.LogStreamReader;
import org.camunda.tngp.logstreams.LoggedEvent;
import org.camunda.tngp.util.buffer.BufferReader;

public class LogEntryProcessor<T extends BufferReader>
{
    protected LogStreamReader logReader;
    protected T bufferReader;
    protected LogEntryHandler<T> entryHandler;

    public LogEntryProcessor(LogStreamReader logReader, T bufferReader, LogEntryHandler<T> entryHandler)
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
            final LoggedEvent nextEntry = logReader.next();
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
            final LoggedEvent nextEntry = logReader.next();

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

    public void setLogReader(LogStreamReader logReader)
    {
        this.logReader = logReader;
    }
}
