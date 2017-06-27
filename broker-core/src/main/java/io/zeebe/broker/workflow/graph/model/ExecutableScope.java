package io.zeebe.broker.workflow.graph.model;

import org.agrona.DirectBuffer;

import io.zeebe.util.buffer.BufferUtil;

public class ExecutableScope extends ExecutableFlowNode
{
    private ExecutableFlowElement[] flowElements;

    private ExecutableStartEvent scopeStartEvent;

    public ExecutableFlowElement[] getFlowElements()
    {
        return flowElements;
    }

    public void setFlowElements(ExecutableFlowElement[] flowElements)
    {
        this.flowElements = flowElements;
    }

    public <T extends ExecutableFlowElement> T getChildById(String id)
    {
        return getChildById(BufferUtil.wrapString(id));
    }

    @SuppressWarnings("unchecked")
    public <T extends ExecutableFlowElement> T getChildById(DirectBuffer id)
    {
        for (int i = 0; i < flowElements.length; i++)
        {
            final ExecutableFlowElement flowElement = flowElements[i];

            if (BufferUtil.equals(id, flowElement.getId()))
            {
                return (T) flowElement;
            }
        }

        return null;
    }

    public ExecutableStartEvent getScopeStartEvent()
    {
        return scopeStartEvent;
    }

    public void setScopeStartEvent(ExecutableStartEvent scopeStartEvent)
    {
        this.scopeStartEvent = scopeStartEvent;
    }
}
