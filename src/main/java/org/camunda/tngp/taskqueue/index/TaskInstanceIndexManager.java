package org.camunda.tngp.taskqueue.index;

import static org.camunda.tngp.hashindex.HashIndexDescriptor.*;

import java.io.File;
import java.nio.MappedByteBuffer;

import org.camunda.tngp.hashindex.HashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.index.LogIndex;

import uk.co.real_logic.agrona.IoUtil;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class TaskInstanceIndexManager implements AutoCloseable
{
    protected LogIndex logIndex;
    protected File indexFile;
    protected MappedByteBuffer mappedIndexFile;
    protected Log log;
    protected int blockSize;

    public TaskInstanceIndexManager(Log log, int blockSize)
    {
        this.log = log;
        this.blockSize = blockSize;
        indexFile = new File(log.getLogDirectory().getAbsolutePath() + File.separator + "task-instance.idx");
    }

    public void openIndex()
    {
        IoUtil.deleteIfExists(this.indexFile);

        final int indexBufferSize = requiredIndexBufferSize(64);
        final int blockBufferSize = requiredBlockBufferSize(64, blockSize);
        ensureIndexFileExists(indexFile);
        mappedIndexFile = IoUtil.mapExistingFile(indexFile, "index file", 0l, indexBufferSize + blockBufferSize);
        final UnsafeBuffer indexBuffer = new UnsafeBuffer(mappedIndexFile, 0, indexBufferSize);
        final UnsafeBuffer blockBuffer = new UnsafeBuffer(mappedIndexFile, indexBufferSize, blockBufferSize);
        final HashIndex hashIndex = new HashIndex(indexBuffer, blockBuffer, 32, blockSize, TaskInstanceIndexer.VALUE_LENGTH);
        logIndex = new LogIndex(log, new TaskInstanceIndexer(), hashIndex);
    }

    public void close()
    {
        if(mappedIndexFile != null)
        {
            IoUtil.unmap(mappedIndexFile);
        }
    }

    public void recreateIndex()
    {
        System.out.print("Recreating index ...");
        logIndex.setIndexerPosition(log.getInitialPosition());
        logIndex.updateIndex();
        System.out.println("done");
    }

    public LogIndex getLogIndex()
    {
        return logIndex;
    }

    private static File ensureIndexFileExists(File indexFile)
    {
        if(!indexFile.exists())
        {
            IoUtil.createEmptyFile(indexFile, 0);
        }
        return indexFile;
    }
}
