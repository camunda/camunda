package org.camunda.tngp.broker.workflow.graph.model;

public class ExecutableSequenceFlow extends ExecutableFlowElement
{
    private ExecutableFlowNode sourceNode;
    private ExecutableFlowNode targetNode;

    public ExecutableFlowNode getSourceNode()
    {
        return sourceNode;
    }

    public ExecutableFlowNode getTargetNode()
    {
        return targetNode;
    }

    public void setSourceNode(ExecutableFlowNode sourceNode)
    {
        this.sourceNode = sourceNode;
    }

    public void setTargetNode(ExecutableFlowNode targetNode)
    {
        this.targetNode = targetNode;
    }
}
