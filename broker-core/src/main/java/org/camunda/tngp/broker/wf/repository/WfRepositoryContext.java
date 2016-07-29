package org.camunda.tngp.broker.wf.repository;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.broker.wf.repository.idx.WfDefinitionIndexWriter;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class WfRepositoryContext implements ResourceContext
{
    protected final int id;
    protected final String name;

    protected Log wfDefinitionLog;
    protected IdGenerator wfDefinitionIdGenerator;
    protected HashIndexManager<Bytes2LongHashIndex> wfDefinitionKeyIndex;
    protected HashIndexManager<Long2LongHashIndex> wfDefinitionIdIndex;
    protected WfDefinitionIndexWriter wfDefinitionIndexWriter;
    protected WfDefinitionCacheService wfDefinitionCacheService;

    public WfRepositoryContext(int id, String name)
    {
        this.id = id;
        this.name = name;
    }

    public void setWfDefinitionLog(Log log)
    {
        this.wfDefinitionLog = log;
    }

    public Log getWfDefinitionLog()
    {
        return wfDefinitionLog;
    }

    @Override
    public int getResourceId()
    {
        return id;
    }

    @Override
    public String getResourceName()
    {
        return name;
    }

    public IdGenerator getWfDefinitionIdGenerator()
    {
        return wfDefinitionIdGenerator;
    }

    public void setWfDefinitionIdGenerator(IdGenerator wfDefinitionIdGenerator)
    {
        this.wfDefinitionIdGenerator = wfDefinitionIdGenerator;
    }

    public HashIndexManager<Bytes2LongHashIndex> getWfDefinitionKeyIndex()
    {
        return wfDefinitionKeyIndex;
    }

    public void setWfDefinitionKeyIndex(HashIndexManager<Bytes2LongHashIndex> wfDefinitionKeyIndex)
    {
        this.wfDefinitionKeyIndex = wfDefinitionKeyIndex;
    }

    public WfDefinitionIndexWriter getWfDefinitionIndexWriter()
    {
        return wfDefinitionIndexWriter;
    }

    public void setWfDefinitionIndexWriter(WfDefinitionIndexWriter wfDefinitionIndexWriter)
    {
        this.wfDefinitionIndexWriter = wfDefinitionIndexWriter;
    }

    public HashIndexManager<Long2LongHashIndex> getWfDefinitionIdIndex()
    {
        return wfDefinitionIdIndex;
    }

    public void setWfDefinitionIdIndex(HashIndexManager<Long2LongHashIndex> wfDefinitionIdIndex)
    {
        this.wfDefinitionIdIndex = wfDefinitionIdIndex;
    }

    public WfDefinitionCacheService getWfDefinitionCacheService()
    {
        return wfDefinitionCacheService;
    }

    public void setWfDefinitionCacheService(WfDefinitionCacheService wfDefinitionCacheService)
    {
        this.wfDefinitionCacheService = wfDefinitionCacheService;
    }
}
