package io.zeebe.broker.workflow.graph.model;

import org.agrona.DirectBuffer;

import io.zeebe.util.buffer.BufferUtil;

public class ExecutableFlowElement
{
    private DirectBuffer id;

    private String name;
    private ExecutableScope flowScope;
    private ExecutableWorkflow workflow;

    public void setId(String id)
    {
        this.id = BufferUtil.wrapString(id);
    }

    public DirectBuffer getId()
    {
        return id;
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

    public ExecutableWorkflow getWorkflow()
    {
        return workflow;
    }

    public void setWorkflow(ExecutableWorkflow process)
    {
        this.workflow = process;
    }

}
