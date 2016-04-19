package org.camunda.tngp.broker.services;

import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;

public class Long2LongIndexManagerService extends HashIndexManagerService<Long2LongHashIndex>
{

    public Long2LongIndexManagerService(int indexSize, int blockLength)
    {
        super(indexSize, blockLength);
    }

    @Override
    protected Long2LongHashIndex createIndex(FileChannelIndexStore indexStore, boolean createNew)
    {
        if(createNew)
        {
            return new Long2LongHashIndex(indexStore, indexSize, blockLength);
        }
        else
        {
            return new Long2LongHashIndex(indexStore);
        }
    }

}
