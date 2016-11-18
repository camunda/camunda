package org.camunda.tngp.broker.wf.runtime.log.bpmn;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.protocol.log.BpmnFlowElementEventDecoder;
import org.camunda.tngp.protocol.log.MessageHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

public class BpmnFlowElementEventReader implements BufferReader
{

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final BpmnFlowElementEventDecoder bodyDecoder = new BpmnFlowElementEventDecoder();

    protected final UnsafeBuffer flowElementIdStringBuffer = new UnsafeBuffer(0, 0);
    protected final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        offset += bodyDecoder.encodedLength();
        offset +=  BpmnFlowElementEventDecoder.flowElementIdStringHeaderLength();
        final int flowElementIdStringLength = bodyDecoder.flowElementIdStringLength();

        flowElementIdStringBuffer.wrap(buffer, offset, flowElementIdStringLength);

        offset += flowElementIdStringLength;
        bodyDecoder.limit(offset);
        offset +=  BpmnFlowElementEventDecoder.payloadHeaderLength();
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

    public DirectBuffer flowElementIdString()
    {
        return flowElementIdStringBuffer;
    }

    public long bpmnBranchKey()
    {
        return bodyDecoder.bpmnBranchKey();
    }

    public DirectBuffer payload()
    {
        return payloadBuffer;
    }
}
