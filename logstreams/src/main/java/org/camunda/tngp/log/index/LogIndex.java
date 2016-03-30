package org.camunda.tngp.log.index;

import java.nio.channels.FileChannel;

import org.camunda.tngp.hashindex.HashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogFragmentHandler;

/**
 * A log index
 *
 */
public class LogIndex
{
    protected HashIndex index;

    protected LogEntryIndexer indexer;

    protected long indexerPosition = 0;

    protected Log log;

    protected final LogFragmentHandler logFragmentHandler = new LogFragmentHandler()
    {
        @Override
        public void onFragment(long position, FileChannel fileChannel, int offset, int length)
        {
            indexer.indexEntry(index, position, fileChannel, offset, length);
        }
    };


    public LogIndex(
            final Log log,
            final LogEntryIndexer indexer,
            final HashIndex index)
    {
        this.log = log;
        this.indexer = indexer;
        this.index = index;
    }

    public void setIndexerPosition(long indexerPosition)
    {
        this.indexerPosition = indexerPosition;
    }

    public long getIndexerPosition()
    {
        return indexerPosition;
    }

    public void updateIndex()
    {

        long pollPosition = indexerPosition;

        do
        {
            this.indexerPosition = pollPosition;
            pollPosition = log.pollFragment(pollPosition, logFragmentHandler);
        }
        while(pollPosition > 0);

    }

}
