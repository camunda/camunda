package io.zeebe.model.bpmn.instance;

public interface SequenceFlow extends FlowElement
{

    FlowNode getSourceNode();

    FlowNode getTargetNode();

}
