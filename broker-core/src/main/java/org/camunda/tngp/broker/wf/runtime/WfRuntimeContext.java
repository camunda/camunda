package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.broker.wf.repository.WfTypeCacheService;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class WfRuntimeContext implements ResourceContext
{
    protected final int id;
    protected final String name;

    protected IdGenerator idGenerator;
    protected WfTypeCacheService wfTypeCacheService;
    protected LogReader logReader;
    protected LogWriter logWriter;
    protected BpmnEventHandler bpmnEventHandler;

    public WfRuntimeContext(int id, String name)
    {
        this.id = id;
        this.name = name;
    }

    public IdGenerator getIdGenerator()
    {
        return idGenerator;
    }

    public void setIdGenerator(IdGenerator idGenerator)
    {
        this.idGenerator = idGenerator;
    }

    public LogReader getLogReader()
    {
        return logReader;
    }

    public void setLogReader(LogReader logReader)
    {
        this.logReader = logReader;
    }

    public LogWriter getLogWriter()
    {
        return logWriter;
    }

    public void setLogWriter(LogWriter logWriter)
    {
        this.logWriter = logWriter;
    }

    public WfTypeCacheService getWfTypeCacheService()
    {
        return wfTypeCacheService;
    }

    public void setWfTypeCacheService(WfTypeCacheService wfTypeCacheService)
    {
        this.wfTypeCacheService = wfTypeCacheService;
    }

    public void setBpmnEventHandler(BpmnEventHandler bpmnEventHandler)
    {
        this.bpmnEventHandler = bpmnEventHandler;
    }

    public BpmnEventHandler getBpmnEventHandler()
    {
        return bpmnEventHandler;
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
}
