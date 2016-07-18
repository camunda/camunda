package org.camunda.tngp.broker.wf.runtime.bpmn.handler;

import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventWriter;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class TakeOutgoingFlowsHandler implements BpmnActivityInstanceEventHandler, BpmnProcessEventHandler
{
    protected BpmnFlowElementEventWriter eventWriter = new BpmnFlowElementEventWriter();
    protected final FlowElementVisitor flowElementVisitor = new FlowElementVisitor();

    @Override
    public BpmnAspect getHandledBpmnAspect()
    {
        return BpmnAspect.TAKE_OUTGOING_FLOWS;
    }

    @Override
    public void handle(BpmnProcessEventReader processEventReader, ProcessGraph process, LogWriter logWriter,
            IdGenerator idGenerator)
    {
        flowElementVisitor.init(process).moveToNode(processEventReader.initialElementId());
        flowElementVisitor.traverseEdge(BpmnEdgeTypes.NODE_OUTGOING_SEQUENCE_FLOWS);

        writeSequenceFlowEvent(
                processEventReader.processId(),
                processEventReader.processInstanceId(),
                flowElementVisitor.nodeId(),
                idGenerator,
                logWriter);
    }

    @Override
    public void handle(BpmnActivityEventReader activityEventReader, ProcessGraph process, LogWriter logWriter,
            IdGenerator idGenerator)
    {
        flowElementVisitor.init(process).moveToNode(activityEventReader.flowElementId());
        flowElementVisitor.traverseEdge(BpmnEdgeTypes.NODE_OUTGOING_SEQUENCE_FLOWS);

        writeSequenceFlowEvent(
                activityEventReader.processId(),
                activityEventReader.processInstanceId(),
                flowElementVisitor.nodeId(),
                idGenerator,
                logWriter);
    }

    protected void writeSequenceFlowEvent(long processId, long processInstanceId, int sequenceFlowId, IdGenerator idGenerator, LogWriter logWriter)
    {
        eventWriter
            .eventType(ExecutionEventType.SQF_EXECUTED)
            .flowElementId(sequenceFlowId)
            .key(idGenerator.nextId())
            .processId(processId)
            .processInstanceId(processInstanceId);

        System.out.println("Taking flow " + sequenceFlowId);

        if (logWriter.write(eventWriter) < 0)
        {
            // TODO: throw exception/backpressure; could not write event
            System.err.println("cannot write sequence flow take event");
        }
    }

    public void setEventWriter(BpmnFlowElementEventWriter eventWriter)
    {
        this.eventWriter = eventWriter;
    }

}
