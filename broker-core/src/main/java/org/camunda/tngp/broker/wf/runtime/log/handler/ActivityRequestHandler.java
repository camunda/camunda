package org.camunda.tngp.broker.wf.runtime.log.handler;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.wf.runtime.log.ActivityInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventWriter;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnBranchEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnBranchEventWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.protocol.log.ActivityInstanceRequestType;

public class ActivityRequestHandler implements LogEntryTypeHandler<ActivityInstanceRequestReader>
{

    protected Long2LongHashIndex eventIndex;
    protected LogReader logReader;

    protected BpmnActivityEventReader activityInstanceReader = new BpmnActivityEventReader();
    protected BpmnActivityEventWriter activityInstanceWriter = new BpmnActivityEventWriter();
    protected BpmnBranchEventWriter bpmnBranchWriter = new BpmnBranchEventWriter();
    protected BpmnBranchEventReader bpmnBranchReader = new BpmnBranchEventReader();

    protected UnsafeBuffer newBranchPayloadBuffer = new UnsafeBuffer(0, 0);

    public ActivityRequestHandler(LogReader logReader, Long2LongHashIndex eventIndex)
    {
        this.eventIndex = eventIndex;
        this.logReader = logReader;
    }

    @Override
    public void handle(ActivityInstanceRequestReader requestReader, ResponseControl responseControl, LogWriters logWriters)
    {

        if (requestReader.type() == ActivityInstanceRequestType.COMPLETE)
        {
            completeActivityInstance(requestReader, logWriters);
        }
    }

    protected void completeActivityInstance(ActivityInstanceRequestReader requestReader, LogWriters logWriters)
    {
        // TODO: extract methods

        final long activityInstanceId = requestReader.activityInstanceKey();
        final long latestPosition = eventIndex.get(activityInstanceId, -1);

        if (latestPosition < 0)
        {
            // activity instance does not exist; ignore
            return;
        }

        logReader.seek(latestPosition);
        logReader.next()
            .readValue(activityInstanceReader);

        final long branchPosition = eventIndex.get(activityInstanceReader.bpmnBranchKey(), -1L);

        if (latestPosition < 0)
        {
            // TODO: branch event not found => explode
        }

        logReader.seek(branchPosition);
        logReader.next()
             .readValue(bpmnBranchReader);

        final DirectBuffer branchPayload = bpmnBranchReader.materializedPayload();
        final DirectBuffer taskPayload = requestReader.payload();

        // default output mapping is append

        final byte[] newBranchPayload = new byte[branchPayload.capacity() + taskPayload.capacity()];
        branchPayload.getBytes(0, newBranchPayload, 0, branchPayload.capacity());
        taskPayload.getBytes(0, newBranchPayload, branchPayload.capacity(), taskPayload.capacity());

        newBranchPayloadBuffer.wrap(newBranchPayload);

        bpmnBranchWriter
             .materializedPayload(newBranchPayloadBuffer, 0, newBranchPayloadBuffer.capacity())
             .key(bpmnBranchReader.key());

        logWriters.writeToCurrentLog(bpmnBranchWriter);

        activityInstanceWriter
            .eventType(ExecutionEventType.ACT_INST_COMPLETED)
            .flowElementId(activityInstanceReader.flowElementId())
            .key(activityInstanceReader.key())
            .wfDefinitionId(activityInstanceReader.wfDefinitionId())
            .wfInstanceId(activityInstanceReader.wfInstanceId())
            .taskQueueId(activityInstanceReader.taskQueueId())
            .bpmnBranchKey(activityInstanceReader.bpmnBranchKey())
            .payload(taskPayload, 0, taskPayload.capacity());

        final DirectBuffer taskType = activityInstanceReader.getTaskType();

        activityInstanceWriter
            .taskType(taskType, 0, taskType.capacity());

        final DirectBuffer flowElementIdString = activityInstanceReader.getFlowElementIdString();

        activityInstanceWriter
            .flowElementIdString(flowElementIdString, 0, flowElementIdString.capacity());

        logWriters.writeToCurrentLog(activityInstanceWriter);

    }


}
