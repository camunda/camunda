package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.broker.wf.repository.WfTypeCacheService;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.BpmnEventHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.TaskEventHandler;
import org.camunda.tngp.broker.wf.runtime.idx.WorkflowEventIndexWriter;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class WfRuntimeContext implements ResourceContext
{
    protected final int id;
    protected final String name;

    protected IdGenerator idGenerator;
    protected WfTypeCacheService wfTypeCacheService;
    protected LogWriter logWriter;
    protected BpmnEventHandler bpmnEventHandler;
    protected TaskEventHandler taskEventHandler;

    protected WorkflowEventIndexWriter activityInstanceIndexWriter;

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

    public WorkflowEventIndexWriter getActivityInstanceIndexWriter()
    {
        return activityInstanceIndexWriter;
    }

    public void setActivityInstanceIndexWriter(WorkflowEventIndexWriter activityInstanceIndexWriter)
    {
        this.activityInstanceIndexWriter = activityInstanceIndexWriter;
    }

    public void setTaskEventHandler(TaskEventHandler taskEventHandler)
    {
        this.taskEventHandler = taskEventHandler;
    }

    public TaskEventHandler getTaskEventHandler()
    {
        return taskEventHandler;
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
