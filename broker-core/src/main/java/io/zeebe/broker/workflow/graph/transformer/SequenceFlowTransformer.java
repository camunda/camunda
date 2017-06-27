package io.zeebe.broker.workflow.graph.transformer;

import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;

import io.zeebe.broker.workflow.graph.model.ExecutableFlowNode;
import io.zeebe.broker.workflow.graph.model.ExecutableScope;
import io.zeebe.broker.workflow.graph.model.ExecutableSequenceFlow;

public class SequenceFlowTransformer implements BpmnElementTransformer<SequenceFlow, ExecutableSequenceFlow>
{
    @Override
    public Class<SequenceFlow> getType()
    {
        return SequenceFlow.class;
    }

    @Override
    public void transform(SequenceFlow modelElement, ExecutableSequenceFlow bpmnElement, ExecutableScope scope)
    {
        final FlowNode source = modelElement.getSource();
        final FlowNode target = modelElement.getTarget();

        bpmnElement.setSourceNode((ExecutableFlowNode) scope.getChildById(source.getId()));
        bpmnElement.setTargetNode((ExecutableFlowNode) scope.getChildById(target.getId()));
    }

}
