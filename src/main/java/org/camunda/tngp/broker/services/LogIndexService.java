package org.camunda.tngp.broker.services;

import static org.camunda.tngp.hashindex.HashIndexDescriptor.requiredBlockBufferSize;
import static org.camunda.tngp.hashindex.HashIndexDescriptor.requiredIndexBufferSize;

import java.io.File;
import java.nio.MappedByteBuffer;

import org.camunda.tngp.broker.servicecontainer.Injector;
import org.camunda.tngp.broker.servicecontainer.Service;
import org.camunda.tngp.broker.servicecontainer.ServiceContext;
import org.camunda.tngp.broker.servicecontainer.ServiceName;
import org.camunda.tngp.broker.taskqueue.TaskInstanceIndexer;
import org.camunda.tngp.hashindex.HashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.index.LogEntryIndexer;
import org.camunda.tngp.log.index.LogIndex;

import uk.co.real_logic.agrona.IoUtil;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class LogIndexService implements Service<LogIndex>
{
    protected final Injector<Log> logInjector = new Injector<>();
    protected final LogEntryIndexer logEntryIndexer;

    protected File indexFile;

    protected int blockLength;
    protected int valueLength;

    protected LogIndex logIndex;
    protected MappedByteBuffer mappedIndexFile;

    public LogIndexService(
            final LogEntryIndexer logEntryIndexer,
            int blockLength,
            int valueLength)
    {
        this.logEntryIndexer = logEntryIndexer;
        this.blockLength = blockLength;
        this.valueLength = valueLength;
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        final Log log = logInjector.getValue();

        indexFile = new File(log.getLogDirectory().getAbsolutePath() + File.separator + serviceContext.getName() + ".idx");


        final int indexBufferSize = requiredIndexBufferSize(64);
        final int blockBufferSize = requiredBlockBufferSize(64, blockLength);

        IoUtil.deleteIfExists(indexFile);
        mappedIndexFile = IoUtil.mapNewFile(indexFile, indexBufferSize + blockBufferSize);

        final UnsafeBuffer indexBuffer = new UnsafeBuffer(mappedIndexFile, 0, indexBufferSize);
        final UnsafeBuffer blockBuffer = new UnsafeBuffer(mappedIndexFile, indexBufferSize, blockBufferSize);

        final HashIndex hashIndex = new HashIndex(indexBuffer, blockBuffer, 32, blockLength, TaskInstanceIndexer.VALUE_LENGTH);

        logIndex = new LogIndex(log, new TaskInstanceIndexer(), hashIndex);

        System.out.print("Recreating index " + serviceContext.getName() + " ... ");
        logIndex.setIndexerPosition(log.getInitialPosition());
        logIndex.updateIndex();
        System.out.println("done.");
    }

    @Override
    public void stop()
    {
        IoUtil.unmap(mappedIndexFile);
        IoUtil.deleteIfExists(indexFile);
    }

    @Override
    public LogIndex get()
    {
        return logIndex;
    }

    public Injector<Log> getLogInjector()
    {
        return logInjector;
    }

}
