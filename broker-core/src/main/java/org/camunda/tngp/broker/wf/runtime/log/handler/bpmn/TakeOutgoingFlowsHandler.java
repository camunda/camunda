package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventWriter;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class TakeOutgoingFlowsHandler implements BpmnActivityInstanceAspectHandler, BpmnProcessAspectHandler
{
    public static final String DEBUG_LOGGING_ENABLED_PROP_NAME = "camunda.debug.logging.enabled";
    public static final boolean DEBUG_LOGGING_ENABLED = Boolean.getBoolean(DEBUG_LOGGING_ENABLED_PROP_NAME);

    protected BpmnFlowElementEventWriter eventWriter = new BpmnFlowElementEventWriter();
    protected final FlowElementVisitor flowElementVisitor = new FlowElementVisitor();

    @Override
    public BpmnAspect getHandledBpmnAspect()
    {
        return BpmnAspect.TAKE_OUTGOING_FLOWS;
    }

    @Override
    public int handle(BpmnProcessEventReader processEventReader, ProcessGraph process, LogWriters logWriters,
            IdGenerator idGenerator)
    {
        flowElementVisitor.init(process).moveToNode(processEventReader.initialElementId());
        flowElementVisitor.traverseEdge(BpmnEdgeTypes.NODE_OUTGOING_SEQUENCE_FLOWS);

        return writeSequenceFlowEvent(
                processEventReader.processId(),
                processEventReader.processInstanceId(),
                flowElementVisitor,
                processEventReader.bpmnBranchKey(),
                idGenerator,
                logWriters);
    }

    @Override
    public int handle(BpmnActivityEventReader activityEventReader, ProcessGraph process, LogWriters logWriters,
            IdGenerator idGenerator)
    {
        flowElementVisitor.init(process).moveToNode(activityEventReader.flowElementId());
        flowElementVisitor.traverseEdge(BpmnEdgeTypes.NODE_OUTGOING_SEQUENCE_FLOWS);

        return writeSequenceFlowEvent(
                activityEventReader.wfDefinitionId(),
                activityEventReader.wfInstanceId(),
                flowElementVisitor,
                activityEventReader.bpmnBranchKey(),
                idGenerator,
                logWriters);
    }

    protected int writeSequenceFlowEvent(long processId,
            long processInstanceId,
            FlowElementVisitor flowElementVisitor,
            long bpmnBranchKey,
            IdGenerator idGenerator,
            LogWriters logWriters)
    {
        eventWriter
            .eventType(ExecutionEventType.SQF_EXECUTED)
            .flowElementId(flowElementVisitor.nodeId())
            .key(idGenerator.nextId())
            .processId(processId)
            .workflowInstanceId(processInstanceId)
            .flowElementIdString(flowElementVisitor.stringIdBuffer(), 0, flowElementVisitor.stringIdBytesLength());

        if (DEBUG_LOGGING_ENABLED)
        {
            System.out.println("Taking flow " + flowElementVisitor.stringId());
        }

        logWriters.writeToCurrentLog(eventWriter);
        return LogEntryHandler.CONSUME_ENTRY_RESULT;
    }

    public void setEventWriter(BpmnFlowElementEventWriter eventWriter)
    {
        this.eventWriter = eventWriter;
    }

}
