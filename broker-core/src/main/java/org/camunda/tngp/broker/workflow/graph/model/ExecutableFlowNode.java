package org.camunda.tngp.broker.workflow.graph.model;

public class ExecutableFlowNode extends ExecutableFlowElement
{
    private BpmnAspect aspect = BpmnAspect.NONE;

    private ExecutableSequenceFlow[] outgoingSequenceFlows;
    private ExecutableSequenceFlow[] incomingSequenceFlows;

    public ExecutableSequenceFlow[] getOutgoingSequenceFlows()
    {
        return outgoingSequenceFlows;
    }
    public void setOutgoingSequenceFlows(ExecutableSequenceFlow[] outgoingSequenceFlows)
    {
        this.outgoingSequenceFlows = outgoingSequenceFlows;
    }
    public ExecutableSequenceFlow[] getIncomingSequenceFlows()
    {
        return incomingSequenceFlows;
    }
    public void setIncomingSequenceFlows(ExecutableSequenceFlow[] incomingSequenceFlows)
    {
        this.incomingSequenceFlows = incomingSequenceFlows;
    }

    public void setBpmnAspect(BpmnAspect aspect)
    {
        this.aspect = aspect;
    }

    public BpmnAspect getBpmnAspect()
    {
        return aspect;
    }

}
