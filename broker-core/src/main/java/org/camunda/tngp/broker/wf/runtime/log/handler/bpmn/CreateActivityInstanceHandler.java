package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import org.agrona.DirectBuffer;
import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventWriter;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnBranchEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class CreateActivityInstanceHandler implements BpmnFlowElementAspectHandler
{

    protected final Long2LongHashIndex bpmnEventIndex;
    protected final LogReader logReader;

    protected final BpmnBranchEventReader bpmnBranchReader = new BpmnBranchEventReader();

    protected BpmnActivityEventWriter eventWriter = new BpmnActivityEventWriter();
    protected final FlowElementVisitor flowElementVisitor = new FlowElementVisitor();

    public CreateActivityInstanceHandler(LogReader logReader, Long2LongHashIndex bpmnEventIndex)
    {
        this.logReader = logReader;
        this.bpmnEventIndex = bpmnEventIndex;
    }

    @Override
    public int handle(BpmnFlowElementEventReader flowElementEventReader, ProcessGraph process, LogWriters logWriters, IdGenerator idGenerator)
    {
        final int sequenceFlowId = flowElementEventReader.flowElementId();
        flowElementVisitor.init(process).moveToNode(sequenceFlowId);

        flowElementVisitor.traverseEdge(BpmnEdgeTypes.SEQUENCE_FLOW_TARGET_NODE);

        final DirectBuffer taskTypeBuffer = flowElementVisitor.taskType();

        final long branchPosition = bpmnEventIndex.get(flowElementEventReader.bpmnBranchKey(), -1L);

        if (branchPosition < 0)
        {
            // TODO: explode
        }

        logReader.seek(branchPosition);
        logReader.next().readValue(bpmnBranchReader);


        final DirectBuffer materializedPayload = bpmnBranchReader.materializedPayload();
        // TODO: input mappings here
        // TODO: should be separated into states ACT_INST_READY (token arrived, no payload yet)
        //   and ACT_INST_ACTIVE (payload available); according to BPMN spec

        eventWriter
            .eventType(ExecutionEventType.ACT_INST_CREATED)
            .flowElementId(flowElementVisitor.nodeId())
            .key(idGenerator.nextId())
            .wfDefinitionId(flowElementEventReader.wfDefinitionId())
            .wfInstanceId(flowElementEventReader.wfInstanceId())
            .bpmnBranchKey(flowElementEventReader.bpmnBranchKey())
            .taskQueueId(flowElementVisitor.taskQueueId())
            .taskType(taskTypeBuffer, 0, taskTypeBuffer.capacity())
            .flowElementIdString(flowElementVisitor.stringIdBuffer(), 0, flowElementVisitor.stringIdBytesLength())
            .payload(materializedPayload, 0, materializedPayload.capacity());

        logWriters.writeToCurrentLog(eventWriter);

        return LogEntryHandler.CONSUME_ENTRY_RESULT;
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
