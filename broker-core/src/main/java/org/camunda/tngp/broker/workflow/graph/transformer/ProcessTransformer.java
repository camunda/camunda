package org.camunda.tngp.broker.workflow.graph.transformer;

import static org.camunda.tngp.util.EnsureUtil.ensureNotNull;

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
import org.camunda.tngp.broker.workflow.graph.model.ExecutableStartEvent;

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
        executableProcess.setId(modelElement.getId());
        executableProcess.setName(modelElement.getName());

        final Collection<FlowElement> flowElements = modelElement.getChildElementsByType(FlowElement.class);
        transformChildElements(executableProcess, scope, flowElements);

        setStartEvent(executableProcess);
    }

    private void transformChildElements(ExecutableProcess executableProcess, ExecutableScope scope, final Collection<FlowElement> flowElements)
    {
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

    private void setStartEvent(ExecutableProcess executableProcess)
    {
        for (ExecutableFlowElement flowElement : executableProcess.getFlowElements())
        {
            if (flowElement instanceof ExecutableStartEvent)
            {
                final ExecutableStartEvent startEvent = (ExecutableStartEvent) flowElement;

                if (executableProcess.getScopeStartEvent() == null)
                {
                    executableProcess.setScopeStartEvent(startEvent);
                }
                else
                {
                    throw new RuntimeException("a process can only have one start event");
                }
            }
        }
        ensureNotNull("start event", executableProcess.getScopeStartEvent());
    }

}
