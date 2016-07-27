package org.camunda.tngp.broker.wf.repository;

import org.camunda.tngp.broker.idx.IndexWriter;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionReader;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class WfRepositoryContext implements ResourceContext
{
    protected final int id;
    protected final String name;

    protected Log wfDefinitionLog;
    protected IdGenerator wfDefinitionIdGenerator;
    protected HashIndexManager<Bytes2LongHashIndex> wfDefinitionKeyIndex;
    protected HashIndexManager<Long2LongHashIndex> wfDefinitionIdIndex;
    protected WfDefinitionCache wfDefinitionCache;

    protected IndexWriter<WfDefinitionReader> indexWriter;
    protected LogWriter logWriter;

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

    public IndexWriter<WfDefinitionReader> getIndexWriter()
    {
        return indexWriter;
    }

    public void setIndexWriter(IndexWriter<WfDefinitionReader> indexWriter)
    {
        this.indexWriter = indexWriter;
    }

    public HashIndexManager<Long2LongHashIndex> getWfDefinitionIdIndex()
    {
        return wfDefinitionIdIndex;
    }

    public void setWfDefinitionIdIndex(HashIndexManager<Long2LongHashIndex> wfDefinitionIdIndex)
    {
        this.wfDefinitionIdIndex = wfDefinitionIdIndex;
    }

    public WfDefinitionCache getWfDefinitionCache()
    {
        return wfDefinitionCache;
    }

    public void setWfDefinitionCacheService(WfDefinitionCache wfDefinitionCache)
    {
        this.wfDefinitionCache = wfDefinitionCache;
    }

    public LogWriter getLogWriter()
    {
        return logWriter;
    }

    public void setLogWriter(LogWriter logWriter)
    {
        this.logWriter = logWriter;
    }
}
