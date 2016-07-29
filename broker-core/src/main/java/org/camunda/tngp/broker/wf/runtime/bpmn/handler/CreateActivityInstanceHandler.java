package org.camunda.tngp.broker.wf.runtime.bpmn.handler;

import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventWriter;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

import uk.co.real_logic.agrona.DirectBuffer;

public class CreateActivityInstanceHandler implements BpmnFlowElementEventHandler
{

    protected BpmnActivityEventWriter eventWriter = new BpmnActivityEventWriter();
    protected final FlowElementVisitor flowElementVisitor = new FlowElementVisitor();

    @Override
    public void handle(BpmnFlowElementEventReader flowElementEventReader, ProcessGraph process, LogWriter logWriter, IdGenerator idGenerator)
    {
        final int sequenceFlowId = flowElementEventReader.flowElementId();
        flowElementVisitor.init(process).moveToNode(sequenceFlowId);

        flowElementVisitor.traverseEdge(BpmnEdgeTypes.SEQUENCE_FLOW_TARGET_NODE);

        final DirectBuffer taskTypeBuffer = flowElementVisitor.taskType();

        eventWriter
            .eventType(ExecutionEventType.ACT_INST_CREATED)
            .flowElementId(flowElementVisitor.nodeId())
            .key(idGenerator.nextId())
            .wfDefinitionId(flowElementEventReader.wfDefinitionId())
            .wfInstanceId(flowElementEventReader.wfInstanceId())
            .taskQueueId(flowElementVisitor.taskQueueId())
            .taskType(taskTypeBuffer, 0, taskTypeBuffer.capacity());

        if (logWriter.write(eventWriter) < 0)
        {
            // TODO: throw exception/backpressure; could not write event
            System.err.println("cannot write activity instance create event");
        }
    }

    @Override
    public BpmnAspect getHandledBpmnAspect()
    {
        return BpmnAspect.CREATE_ACTIVITY_INSTANCE;
    }

    public void setEventWriter(BpmnActivityEventWriter eventWriter)
    {
        this.eventWriter = eventWriter;

    }

}
