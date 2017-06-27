package io.zeebe.broker.workflow.graph.transformer;

import org.camunda.bpm.model.bpmn.instance.BaseElement;

import io.zeebe.broker.workflow.graph.model.ExecutableFlowElement;
import io.zeebe.broker.workflow.graph.model.ExecutableScope;

public interface BpmnElementTransformer<T extends BaseElement, F extends ExecutableFlowElement>
{
    Class<T> getType();

    void transform(T modelElement, F flowElement, ExecutableScope scope);
}
