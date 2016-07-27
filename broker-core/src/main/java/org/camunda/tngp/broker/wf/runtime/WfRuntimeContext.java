package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.broker.idx.IndexWriter;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.broker.wf.repository.WfDefinitionCache;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.BpmnEventHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.TaskEventHandler;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class WfRuntimeContext implements ResourceContext
{
    protected final int id;
    protected final String name;

    protected IdGenerator idGenerator;
    protected WfDefinitionCache wfDefinitionCache;
    protected LogWriter logWriter;
    protected BpmnEventHandler bpmnEventHandler;
    protected TaskEventHandler taskEventHandler;

    protected IndexWriter<BpmnEventReader> indexWriter;

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

    public WfDefinitionCache getWfDefinitionCache()
    {
        return wfDefinitionCache;
    }

    public void setWfDefinitionCache(WfDefinitionCache wfDefinitionCache)
    {
        this.wfDefinitionCache = wfDefinitionCache;
    }

    public void setBpmnEventHandler(BpmnEventHandler bpmnEventHandler)
    {
        this.bpmnEventHandler = bpmnEventHandler;
    }

    public BpmnEventHandler getBpmnEventHandler()
    {
        return bpmnEventHandler;
    }

    public IndexWriter<BpmnEventReader> getIndexWriter()
    {
        return indexWriter;
    }

    public void setIndexWriter(IndexWriter<BpmnEventReader> indexWriter)
    {
        this.indexWriter = indexWriter;
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
