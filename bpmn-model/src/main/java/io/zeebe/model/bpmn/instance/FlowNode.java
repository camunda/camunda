package io.zeebe.model.bpmn.instance;

import java.util.List;

public interface FlowNode extends FlowElement
{
    List<SequenceFlow> getIncomingSequenceFlows();

    List<SequenceFlow> getOutgoingSequenceFlows();

}
