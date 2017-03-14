package org.camunda.tngp.broker.workflow.graph.model;

import java.util.HashMap;
import java.util.Map;

public class ExecutableProcess extends ExecutableScope
{
    private final Map<String, ExecutableFlowElement> flowElementMap = new HashMap<>();

    public ExecutableProcess()
    {
        setProcess(this);
        setFlowScope(this);
    }

    public ExecutableFlowElement findFlowElementById(String id)
    {
        return flowElementMap.get(id);
    }

    public Map<String, ExecutableFlowElement> getFlowElementMap()
    {
        return flowElementMap;
    }

}
