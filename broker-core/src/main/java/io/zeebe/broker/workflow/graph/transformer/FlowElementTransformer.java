package io.zeebe.broker.workflow.graph.transformer;

import org.camunda.bpm.model.bpmn.instance.FlowElement;

import io.zeebe.broker.workflow.graph.model.ExecutableFlowElement;
import io.zeebe.broker.workflow.graph.model.ExecutableScope;

public class FlowElementTransformer implements BpmnElementTransformer<FlowElement, ExecutableFlowElement>
{
    @Override
    public Class<FlowElement> getType()
    {
        return FlowElement.class;
    }

    @Override
    public void transform(FlowElement modelElement, ExecutableFlowElement bpmnElement, ExecutableScope scope)
    {
        bpmnElement.setName(modelElement.getName());
    }

}
