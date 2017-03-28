package org.camunda.tngp.broker.workflow.graph.model;

import org.agrona.DirectBuffer;
import org.camunda.tngp.util.buffer.BufferUtil;

public class ExecutableFlowElement
{
    private String id;
    private DirectBuffer idBuffer;

    private String name;
    private ExecutableScope flowScope;
    private ExecutableProcess process;

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
        this.idBuffer = BufferUtil.wrapString(id);
    }

    public DirectBuffer getIdBuffer()
    {
        return idBuffer;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public ExecutableScope getFlowScope()
    {
        return flowScope;
    }

    public void setFlowScope(ExecutableScope flowScope)
    {
        this.flowScope = flowScope;
    }

    public ExecutableProcess getProcess()
    {
        return process;
    }

    public void setProcess(ExecutableProcess process)
    {
        this.process = process;
    }

}
