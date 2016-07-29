package org.camunda.tngp.broker.wf.runtime.bpmn.handler;

import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventWriter;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class TriggerNoneEventHandler implements BpmnFlowElementEventHandler
{

    protected BpmnFlowElementEventWriter eventWriter = new BpmnFlowElementEventWriter();
    protected FlowElementVisitor flowElementVisitor = new FlowElementVisitor();

    @Override
    public void handle(BpmnFlowElementEventReader flowElementEventReader, ProcessGraph process, LogWriter logWriter,
            IdGenerator idGenerator)
    {
        flowElementVisitor.init(process).moveToNode(flowElementEventReader.flowElementId());

        flowElementVisitor.traverseEdge(BpmnEdgeTypes.SEQUENCE_FLOW_TARGET_NODE);

        eventWriter.eventType(ExecutionEventType.EVT_OCCURRED)
            .flowElementId(flowElementVisitor.nodeId())
            .key(idGenerator.nextId())
            .processId(flowElementEventReader.wfDefinitionId())
            .workflowInstanceId(flowElementEventReader.wfInstanceId());

        if (logWriter.write(eventWriter) < 0)
        {
            // TODO: could not write event
        }
    }

    @Override
    public BpmnAspect getHandledBpmnAspect()
    {
        return BpmnAspect.TRIGGER_NONE_EVENT;
    }

    public void setEventWriter(BpmnFlowElementEventWriter eventWriter)
    {
        this.eventWriter = eventWriter;
    }

}
