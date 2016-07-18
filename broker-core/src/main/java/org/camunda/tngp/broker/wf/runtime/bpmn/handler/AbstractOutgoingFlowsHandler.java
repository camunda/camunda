package org.camunda.tngp.broker.wf.runtime.bpmn.handler;

import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.util.buffer.BufferReader;

public abstract class AbstractOutgoingFlowsHandler<T extends BufferReader>
{
    protected BpmnFlowElementEventWriter eventWriter = new BpmnFlowElementEventWriter();
    protected final FlowElementVisitor flowElementVisitor = new FlowElementVisitor();

    public void handle(T eventReader, ProcessGraph process, LogWriter logWriter, IdGenerator idGenerator)
    {
        final int elementId = getCurrentElementId(eventReader);
        flowElementVisitor.init(process).moveToNode(elementId);

        flowElementVisitor.traverseEdge(BpmnEdgeTypes.NODE_OUTGOING_SEQUENCE_FLOWS);

        eventWriter
            .eventType(ExecutionEventType.SQF_EXECUTED)
            .flowElementId(flowElementVisitor.nodeId())
            .key(idGenerator.nextId())
            .processId(getProcessId(eventReader))
            .processInstanceId(getProcessInstanceId(eventReader));

        System.out.println("Taking flow " + flowElementVisitor.nodeId());

        if (logWriter.write(eventWriter) < 0)
        {
            // TODO: throw exception/backpressure; could not write event
            System.err.println("cannot write sequence flow take event");
        }
    }

    protected abstract int getCurrentElementId(T eventReader);

    protected abstract long getProcessId(T eventReader);

    protected abstract long getProcessInstanceId(T eventReader);

    public void setEventWriter(BpmnFlowElementEventWriter eventWriter)
    {
        this.eventWriter = eventWriter;
    }
}
