package org.camunda.tngp.broker.log.idx;

import org.camunda.tngp.broker.log.LogEntryHeaderReader;

public class IndexWriterTracker
{
    protected long lastIndexedPosition;
    protected IndexWriter indexWriter;

    public IndexWriterTracker(IndexWriter indexWriter)
    {
        this.indexWriter = indexWriter;
        this.lastIndexedPosition = indexWriter.getIndexManager().getLastCheckpointPosition();
    }

    public void writeIndexAndTrack(long position, LogEntryHeaderReader entryReader)
    {
        indexWriter.indexLogEntry(position, entryReader);
        lastIndexedPosition = position;
    }

    public void writeCheckpoint()
    {
        this.indexWriter.getIndexManager().writeCheckPoint(lastIndexedPosition);
    }

    public long getLastIndexedPosition()
    {
        return lastIndexedPosition;
    }
}
