package org.camunda.tngp.broker.workflow.graph.transformer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.tngp.broker.workflow.graph.model.BpmnFactory;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableFlowElement;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableProcess;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableScope;

public class ProcessTransformer implements BpmnElementTransformer<Process, ExecutableProcess>
{
    @Override
    public Class<Process> getType()
    {
        return Process.class;
    }

    @Override
    public void transform(Process modelElement, ExecutableProcess executableProcess, ExecutableScope scope)
    {
        final Collection<FlowElement> flowElements = modelElement.getChildElementsByType(FlowElement.class);

        final Map<FlowElement, ExecutableFlowElement> executableFlowElements = new HashMap<>();

        for (FlowElement flowElement : flowElements)
        {
            final ExecutableFlowElement executableElement = BpmnFactory.createElement(flowElement);

            executableElement.setId(flowElement.getId());
            executableElement.setFlowScope(scope);
            executableElement.setProcess(executableProcess);

            executableProcess.getFlowElementMap().put(executableElement.getId(), executableElement);

            executableFlowElements.put(flowElement, executableElement);
        }

        scope.setFlowElements(executableFlowElements.values().toArray(new ExecutableFlowElement[executableFlowElements.size()]));

        for (Entry<FlowElement, ExecutableFlowElement> e : executableFlowElements.entrySet())
        {
            Transformers.apply(e.getKey(), e.getValue(), executableProcess);
        }
    }

}
