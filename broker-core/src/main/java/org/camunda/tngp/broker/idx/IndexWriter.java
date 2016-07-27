package org.camunda.tngp.broker.idx;

import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.impl.Subscription;
import org.camunda.tngp.log.LogEntryWriteListener;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.util.buffer.BufferReader;

import uk.co.real_logic.agrona.DirectBuffer;

public class IndexWriter<T extends BufferReader> implements LogEntryHandler<T>, LogEntryWriteListener
{
    protected LogEntryProcessor<T> logEntryProcessor;
    protected LogReader logReader;
    protected Subscription logBufferSubscription;

    protected T bufferReader;

    protected final HashIndexManager<?>[] managedIndexes;

    protected LogEntryTracker<T> logEntryTracker;

    protected final IndexFragmentHandler fragmentHandler = new IndexFragmentHandler();

    protected int logId;

    public IndexWriter(
            LogReader logReader,
            Subscription logBufferSubscription,
            int logId,
            T bufferReader,
            LogEntryTracker<T> logEntryTracker,
            HashIndexManager<?>[] managedIndexes)
    {
        this.logBufferSubscription = logBufferSubscription;
        this.logReader = logReader;
        this.logId = logId;
        this.bufferReader = bufferReader;
        logEntryProcessor = new LogEntryProcessor<>(logReader, bufferReader, this);
        this.managedIndexes = managedIndexes;
        this.logEntryTracker = logEntryTracker;
    }

    public void resetToLastCheckpointPosition()
    {
        long lastCheckpoint = Integer.MAX_VALUE;

        for (int i = 0; i < managedIndexes.length; i++)
        {
            final long indexCheckpoint = managedIndexes[i].getLastCheckpointPosition();
            if (managedIndexes[i].getLastCheckpointPosition() < lastCheckpoint)
            {
                lastCheckpoint = indexCheckpoint;
            }
        }

        if (lastCheckpoint >= 0)
        {
            logReader.setPosition(lastCheckpoint);
        }
    }

    public void writeCheckpoints()
    {
        for (int i = 0; i < managedIndexes.length; i++)
        {
            managedIndexes[i].writeCheckPoint(logReader.position());
        }
    }

    public int indexLogEntries()
    {
        logBufferSubscription.poll(fragmentHandler, Integer.MAX_VALUE);

        return logEntryProcessor.doWork(Integer.MAX_VALUE);
    }

    @Override
    public int handle(long position, T reader)
    {
        logEntryTracker.onLogEntryCommit(reader, position);
        // that is fine as long as index writers do not read from indices that may be dirty
        return LogEntryHandler.CONSUME_ENTRY_RESULT;
    }

    public void setLogEntryProcessor(LogEntryProcessor<T> logEntryProcessor)
    {
        this.logEntryProcessor = logEntryProcessor;
    }

    @Override
    public void beforeCommit(DirectBuffer buffer, int offset, int length)
    {
        bufferReader.wrap(buffer, offset, length);
        logEntryTracker.onLogEntryStaged(bufferReader);
    }

    public class IndexFragmentHandler implements FragmentHandler
    {

        @Override
        public int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean failed)
        {
            if (streamId != logId)
            {
                // ignore events of other logs
                return FragmentHandler.CONSUME_FRAGMENT_RESULT;
            }

            if (failed)
            {
                bufferReader.wrap(buffer, offset, length);
                logEntryTracker.onLogEntryFailed(bufferReader);
            }

            return FragmentHandler.CONSUME_FRAGMENT_RESULT;
        }
    }

}
