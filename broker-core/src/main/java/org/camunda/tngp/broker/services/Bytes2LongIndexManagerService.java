package org.camunda.tngp.broker.services;

import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;

public class Bytes2LongIndexManagerService extends HashIndexManagerService<Bytes2LongHashIndex>
{
    protected final int keyLength;

    public Bytes2LongIndexManagerService(int indexSize, int blockLength, int keyLength)
    {
        super(indexSize, blockLength);
        this.keyLength = keyLength;
    }

    @Override
    protected Bytes2LongHashIndex createIndex(FileChannelIndexStore indexStore, boolean createNew)
    {
        if (createNew)
        {
            return new Bytes2LongHashIndex(indexStore, indexSize, blockLength, keyLength);
        }
        else
        {
            return new Bytes2LongHashIndex(indexStore);
        }
    }

}
