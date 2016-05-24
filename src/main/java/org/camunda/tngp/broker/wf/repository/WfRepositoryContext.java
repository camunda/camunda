package org.camunda.tngp.broker.wf.repository;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class WfRepositoryContext implements ResourceContext
{
    protected final int id;
    protected final String name;

    protected Log wfTypeLog;
    protected IdGenerator wfTypeIdGenerator;
    protected HashIndexManager<Bytes2LongHashIndex> wfTypeKeyIndex;
    protected HashIndexManager<Long2LongHashIndex> wfTypeIdIndex;
    protected WfTypeIndexWriter wfTypeIndexWriter;
    protected WfTypeCacheService wfTypeCacheService;

    public WfRepositoryContext(int id, String name)
    {
        this.id = id;
        this.name = name;
    }

    public void setWfTypeLog(Log log)
    {
        this.wfTypeLog = log;
    }

    public Log getWfTypeLog()
    {
        return wfTypeLog;
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

    public IdGenerator getWfTypeIdGenerator()
    {
        return wfTypeIdGenerator;
    }

    public void setWfTypeIdGenerator(IdGenerator wfTypeIdGenerator)
    {
        this.wfTypeIdGenerator = wfTypeIdGenerator;
    }

    public HashIndexManager<Bytes2LongHashIndex> getWfTypeKeyIndex()
    {
        return wfTypeKeyIndex;
    }

    public void setWfTypeKeyIndex(HashIndexManager<Bytes2LongHashIndex> wfTypeKeyIndex)
    {
        this.wfTypeKeyIndex = wfTypeKeyIndex;
    }

    public WfTypeIndexWriter getWfTypeIndexWriter()
    {
        return wfTypeIndexWriter;
    }

    public void setWfTypeIndexWriter(WfTypeIndexWriter wfTypeIndexWriter)
    {
        this.wfTypeIndexWriter = wfTypeIndexWriter;
    }

    public HashIndexManager<Long2LongHashIndex> getWfTypeIdIndex()
    {
        return wfTypeIdIndex;
    }

    public void setWfTypeIdIndex(HashIndexManager<Long2LongHashIndex> wfTypeIdIndex)
    {
        this.wfTypeIdIndex = wfTypeIdIndex;
    }

    public WfTypeCacheService getWfTypeCacheService()
    {
        return wfTypeCacheService;
    }

    public void setWfTypeCacheService(WfTypeCacheService wfTypeCacheService)
    {
        this.wfTypeCacheService = wfTypeCacheService;
    }
}
