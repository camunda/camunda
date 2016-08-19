package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventWriter;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class TriggerNoneEventHandler implements BpmnFlowElementAspectHandler
{

    protected BpmnFlowElementEventWriter eventWriter = new BpmnFlowElementEventWriter();
    protected FlowElementVisitor flowElementVisitor = new FlowElementVisitor();

    @Override
    public int handle(BpmnFlowElementEventReader flowElementEventReader, ProcessGraph process, LogWriters logWriters,
            IdGenerator idGenerator)
    {
        flowElementVisitor.init(process).moveToNode(flowElementEventReader.flowElementId());

        flowElementVisitor.traverseEdge(BpmnEdgeTypes.SEQUENCE_FLOW_TARGET_NODE);

        eventWriter.eventType(ExecutionEventType.EVT_OCCURRED)
            .flowElementId(flowElementVisitor.nodeId())
            .key(idGenerator.nextId())
            .processId(flowElementEventReader.wfDefinitionId())
            .workflowInstanceId(flowElementEventReader.wfInstanceId());

        logWriters.writeToCurrentLog(eventWriter);
        return LogEntryHandler.CONSUME_ENTRY_RESULT;
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
