package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.broker.wf.repository.WfTypeCacheService;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class WfRuntimeContext implements ResourceContext
{
    protected final int id;
    protected final String name;

    protected IdGenerator idGenerator;
    protected WfTypeCacheService wfTypeCacheService;
    protected Log log;
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

    public Log getLog()
    {
        return log;
    }

    public void setLog(Log log)
    {
        this.log = log;
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
