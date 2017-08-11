package io.zeebe.model.bpmn.builder;

import java.util.concurrent.atomic.AtomicLong;

import io.zeebe.model.bpmn.impl.BpmnTransformer;
import io.zeebe.model.bpmn.impl.instance.*;
import io.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;

public class BpmnBuilder
{
    private final AtomicLong nextId = new AtomicLong(1);

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

    private String generateId(String prefix)
    {
        return prefix + "-" + nextId.getAndIncrement();
    }

    public BpmnBuilder startEvent()
    {
        return startEvent(generateId("start-event"));
    }

    public BpmnBuilder startEvent(String id)
    {
        final StartEventImpl startEvent = new StartEventImpl();
        startEvent.setId(id);

        process.getStartEvents().add(startEvent);

        sourceNode = startEvent;

        return this;
    }

    public BpmnBuilder sequenceFlow()
    {
        return sequenceFlow(generateId("sequence-flow"));
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

    public BpmnBuilder endEvent()
    {
        return endEvent(generateId("end-event"));
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
            sequenceFlow();
        }

        sequenceFlow.setTargetRef(targetNode.getId());
        targetNode.getIncoming().add(sequenceFlow);
    }

    public BpmnServiceTaskBuilder serviceTask()
    {
        return serviceTask(generateId("service-task"));
    }

    public BpmnServiceTaskBuilder serviceTask(String id)
    {
        final ServiceTaskImpl serviceTask = new ServiceTaskImpl();
        serviceTask.setId(id);

        connectToLastSequenceFlow(serviceTask);

        process.getServiceTasks().add(serviceTask);

        sourceNode = serviceTask;

        return new BpmnServiceTaskBuilder(this, serviceTask);
    }

    public WorkflowDefinition done()
    {
        final DefinitionsImpl definitionsImpl = new DefinitionsImpl();

        definitionsImpl.getProcesses().add(process);

        return transformer.transform(definitionsImpl);
    }

}
