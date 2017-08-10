package io.zeebe.model.bpmn.builder;

import io.zeebe.model.bpmn.impl.BpmnTransformer;
import io.zeebe.model.bpmn.impl.instance.*;
import io.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;

public class BpmnBuilder
{
    private final BpmnTransformer transformer;

    private final ProcessImpl process;

    private FlowNodeImpl sourceNode;
    private SequenceFlowImpl sequenceFlow;

    public BpmnBuilder(BpmnTransformer transformer, String bpmnProcessId)
    {
        this.transformer = transformer;

        this.process = new ProcessImpl();
        process.setId(bpmnProcessId);
        process.setExecutable(true);
    }

    public BpmnBuilder startEvent(String id)
    {
        final StartEventImpl startEvent = new StartEventImpl();
        startEvent.setId(id);

        process.getStartEvents().add(startEvent);

        sourceNode = startEvent;

        return this;
    }

    public BpmnBuilder sequenceFlow(String id)
    {
        final SequenceFlowImpl sequenceFlow = new SequenceFlowImpl();
        sequenceFlow.setId(id);

        sequenceFlow.setSourceRef(sourceNode.getId());
        sourceNode.getOutgoing().add(sequenceFlow);

        process.getSequenceFlows().add(sequenceFlow);

        this.sequenceFlow = sequenceFlow;

        return this;
    }

    public BpmnBuilder endEvent(String id)
    {
        final EndEventImpl endEvent = new EndEventImpl();
        endEvent.setId(id);

        connectToLastSequenceFlow(endEvent);

        process.getEndEvents().add(endEvent);

        sourceNode = null;

        return this;
    }

    private void connectToLastSequenceFlow(final FlowNodeImpl targetNode)
    {
        if (sequenceFlow == null)
        {
            // TODO generate id
            sequenceFlow("sf");
        }

        sequenceFlow.setTargetRef(targetNode.getId());
        targetNode.getIncoming().add(sequenceFlow);
    }

    public BpmnBuilder serviceTask(String id)
    {
        final ServiceTaskImpl serviceTask = new ServiceTaskImpl();
        serviceTask.setId(id);

        connectToLastSequenceFlow(serviceTask);

        process.getServiceTasks().add(serviceTask);

        sourceNode = serviceTask;

        return this;
    }

    public WorkflowDefinition done()
    {
        final DefinitionsImpl definitionsImpl = new DefinitionsImpl();

        definitionsImpl.getProcesses().add(process);

        return transformer.transform(definitionsImpl);
    }

}
