package io.zeebe.model.bpmn.impl;

import static io.zeebe.util.EnsureUtil.ensureGreaterThan;
import static io.zeebe.util.EnsureUtil.ensureNotNull;

import java.util.*;

import io.zeebe.model.bpmn.impl.instance.*;
import io.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.zeebe.model.bpmn.instance.Workflow;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import org.agrona.DirectBuffer;

public class BpmnTransformer
{

    public WorkflowDefinition transform(DefinitionsImpl definitions)
    {
        final Map<DirectBuffer, Workflow> workflowsById = new HashMap<>();

        final List<ProcessImpl> processes = definitions.getProcesses();
        for (int p = 0; p < processes.size(); p++)
        {
            final ProcessImpl process = processes.get(p);

            transformProcess(process);

            workflowsById.put(process.getBpmnProcessId(), process);
        }

        definitions.getWorkflowsById().putAll(workflowsById);

        return definitions;
    }

    private void transformProcess(final ProcessImpl process)
    {
        final Map<DirectBuffer, FlowElementImpl> flowElementsById = getFlowElementsById(process);
        process.getFlowElementMap().putAll(flowElementsById);

        setInitialStartEvent(process);

        linkSequenceFlows(process, flowElementsById);
    }

    private Map<DirectBuffer, FlowElementImpl> getFlowElementsById(final ProcessImpl process)
    {
        final Map<DirectBuffer, FlowElementImpl> flowElementsById = new HashMap<>();

        final List<FlowElementImpl> flowElements = new ArrayList<>();
        flowElements.addAll(process.getStartEvents());
        flowElements.addAll(process.getEndEvents());
        flowElements.addAll(process.getSequenceFlows());
        flowElements.addAll(process.getServiceTasks());

        for (int f = 0; f < flowElements.size(); f++)
        {
            final FlowElementImpl flowElement = flowElements.get(f);

            flowElementsById.put(flowElement.getIdAsBuffer(), flowElement);
        }

        return flowElementsById;
    }

    private void setInitialStartEvent(final ProcessImpl process)
    {
        final List<StartEventImpl> startEvents = process.getStartEvents();
        ensureGreaterThan("start events", startEvents.size(), 0);

        final StartEventImpl startEvent = startEvents.get(0);
        ensureNotNull("start event", startEvent);

        process.setInitialStartEvent(startEvent);
    }

    private void linkSequenceFlows(final ProcessImpl process, final Map<DirectBuffer, FlowElementImpl> flowElementsById)
    {
        final List<SequenceFlowImpl> sequenceFlows = process.getSequenceFlows();
        for (int s = 0; s < sequenceFlows.size(); s++)
        {
            final SequenceFlowImpl sequenceFlow = sequenceFlows.get(s);

            final FlowElementImpl sourceElement = flowElementsById.get(sequenceFlow.getSourceRefAsBuffer());
            ensureNotNull("source element", sourceElement);
            sequenceFlow.setSourceNode((FlowNodeImpl) sourceElement);

            final FlowElementImpl targetElement = flowElementsById.get(sequenceFlow.getTargetRefAsBuffer());
            ensureNotNull("target element", targetElement);
            sequenceFlow.setTargetNode((FlowNodeImpl) targetElement);
        }
    }

}
