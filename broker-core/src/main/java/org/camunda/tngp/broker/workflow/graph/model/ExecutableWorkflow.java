package org.camunda.tngp.broker.workflow.graph.model;

import java.util.HashMap;
import java.util.Map;

import org.agrona.DirectBuffer;

public class ExecutableWorkflow extends ExecutableScope
{
    private final Map<DirectBuffer, ExecutableFlowElement> flowElementMap = new HashMap<>();

    public ExecutableWorkflow()
    {
        setWorkflow(this);
        setFlowScope(this);
    }

    public ExecutableFlowElement findFlowElementById(DirectBuffer id)
    {
        return flowElementMap.get(id);
    }

    public Map<DirectBuffer, ExecutableFlowElement> getFlowElementMap()
    {
        return flowElementMap;
    }

}
