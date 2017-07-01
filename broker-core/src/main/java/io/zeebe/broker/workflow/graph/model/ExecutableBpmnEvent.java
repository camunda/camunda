package io.zeebe.broker.workflow.graph.model;

public class ExecutableBpmnEvent extends ExecutableFlowNode
{
    private ExecutableScope eventScope;

    public ExecutableScope getEventScope()
    {
        return eventScope;
    }

    public void setEventScope(ExecutableScope eventScope)
    {
        this.eventScope = eventScope;
    }
}
