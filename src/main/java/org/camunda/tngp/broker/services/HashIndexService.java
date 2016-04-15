package org.camunda.tngp.broker.services;

import static org.camunda.tngp.hashindex.HashIndexDescriptor.requiredBlockBufferSize;
import static org.camunda.tngp.hashindex.HashIndexDescriptor.requiredIndexBufferSize;

import java.io.File;
import java.nio.MappedByteBuffer;

import org.camunda.tngp.broker.servicecontainer.Service;
import org.camunda.tngp.broker.servicecontainer.ServiceContext;
import org.camunda.tngp.hashindex.HashIndex;

import uk.co.real_logic.agrona.IoUtil;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public abstract class HashIndexService<I extends HashIndex<?,?>> implements Service<I>
{
    protected final String path;
    protected final int blockLength;
    protected final int keyLength;
    protected final int valueLength;

    protected File indexFile;
    protected MappedByteBuffer mappedIndexFile;
    protected I index;

    public HashIndexService(String path, int blockLength, int keyLength, int valueLength)
    {
        this.path = path;
        this.blockLength = blockLength;
        this.keyLength = keyLength;
        this.valueLength = valueLength;
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        indexFile = new File(path + File.separator + serviceContext.getName() + ".idx");

        final int indexBufferSize = requiredIndexBufferSize(64);
        final int blockBufferSize = requiredBlockBufferSize(64, blockLength);

        IoUtil.deleteIfExists(indexFile);
        mappedIndexFile = IoUtil.mapNewFile(indexFile, indexBufferSize + blockBufferSize);

        final UnsafeBuffer indexBuffer = new UnsafeBuffer(mappedIndexFile, 0, indexBufferSize);
        final UnsafeBuffer blockBuffer = new UnsafeBuffer(mappedIndexFile, indexBufferSize, blockBufferSize);

        index = createIndex(indexBuffer, blockBuffer);
    }

    protected abstract I createIndex(final UnsafeBuffer indexBuffer, final UnsafeBuffer blockBuffer);

    @Override
    public void stop()
    {
        mappedIndexFile.force();
        IoUtil.unmap(mappedIndexFile);
    }

    @Override
    public I get()
    {
        return index;
    }

}
