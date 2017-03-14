package org.camunda.tngp.broker.workflow.graph.model;

public class ExecutableScope extends ExecutableFlowNode
{
    private ExecutableFlowElement[] flowElements;

    private ExecutableBpmnEvent scopeStartEvent;

    public ExecutableFlowElement[] getFlowElements()
    {
        return flowElements;
    }

    public void setFlowElements(ExecutableFlowElement[] flowElements)
    {
        this.flowElements = flowElements;
    }

    public ExecutableFlowElement getChildById(String id)
    {
        for (int i = 0; i < flowElements.length; i++)
        {
            final ExecutableFlowElement flowElement = flowElements[i];

            if (id.equals(flowElement.getId()))
            {
                return flowElement;
            }
        }

        return null;
    }

    public ExecutableBpmnEvent getScopeStartEvent()
    {
        return scopeStartEvent;
    }

    public void setScopeStartEvent(ExecutableBpmnEvent scopeStartEvent)
    {
        this.scopeStartEvent = scopeStartEvent;
    }
}
