package org.camunda.tngp.broker.wf.runtime.log.bpmn;

import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.protocol.log.BpmnActivityEventDecoder;
import org.camunda.tngp.protocol.log.MessageHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class BpmnActivityEventReader implements BufferReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final BpmnActivityEventDecoder bodyDecoder = new BpmnActivityEventDecoder();
    protected final UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(0, 0);
    protected final UnsafeBuffer flowElementIdStringBuffer = new UnsafeBuffer(0, 0);
    protected final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        offset += bodyDecoder.encodedLength();
        offset +=  BpmnActivityEventDecoder.taskTypeHeaderLength();
        final int taskTypeLength = bodyDecoder.taskTypeLength();

        taskTypeBuffer.wrap(buffer, offset, taskTypeLength);

        offset += taskTypeLength;
        bodyDecoder.limit(offset);
        offset += BpmnActivityEventDecoder.flowElementIdStringHeaderLength();
        final int flowElementIdStringLength = bodyDecoder.flowElementIdStringLength();

        flowElementIdStringBuffer.wrap(buffer, offset, flowElementIdStringLength);

        offset += flowElementIdStringLength;
        bodyDecoder.limit(offset);
        offset += BpmnActivityEventDecoder.payloadHeaderLength();
        final int payloadLength = bodyDecoder.payloadLength();

        if (payloadLength > 0)
        {
            payloadBuffer.wrap(buffer, offset, payloadLength);
        }
        else
        {
            payloadBuffer.wrap(0, 0);
        }

    }

    public long key()
    {
        return bodyDecoder.key();
    }

    public long wfDefinitionId()
    {
        return bodyDecoder.wfDefinitionId();
    }

    public ExecutionEventType event()
    {
        return ExecutionEventType.get(bodyDecoder.event());
    }

    public int flowElementId()
    {
        return bodyDecoder.flowElementId();
    }

    public long wfInstanceId()
    {
        return bodyDecoder.wfInstanceId();
    }

    public int taskQueueId()
    {
        return bodyDecoder.taskQueueId();
    }

    public int resourceId()
    {
        return headerDecoder.resourceId();
    }

    public DirectBuffer getTaskType()
    {
        return taskTypeBuffer;
    }

    public DirectBuffer getFlowElementIdString()
    {
        return flowElementIdStringBuffer;
    }

    public long bpmnBranchKey()
    {
        return bodyDecoder.bpmnBranchKey();
    }

    public DirectBuffer getPayload()
    {
        return payloadBuffer;
    }
}
