package org.camunda.tngp.broker.wf.runtime.log.bpmn;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.protocol.log.BpmnBranchEventDecoder;
import org.camunda.tngp.protocol.log.BpmnBranchEventDecoder.DeltaDecoder;
import org.camunda.tngp.protocol.log.MessageHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

public class BpmnBranchEventReader implements BufferReader
{
    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected BpmnBranchEventDecoder bodyDecoder = new BpmnBranchEventDecoder();
    protected DeltaDecoder deltaDecoder;

    protected UnsafeBuffer materializedPayloadBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());
        deltaDecoder = bodyDecoder.delta();
        offset = bodyDecoder.limit();
        offset += BpmnBranchEventDecoder.materializedPayloadHeaderLength();

        final int payloadLength = bodyDecoder.materializedPayloadLength();

        if (payloadLength > 0)
        {
            materializedPayloadBuffer.wrap(buffer, offset, payloadLength);
        }
        else
        {
            materializedPayloadBuffer.wrap(0, 0);
        }
    }

    public DirectBuffer materializedPayload()
    {
        return materializedPayloadBuffer;
    }

    public long key()
    {
        return bodyDecoder.key();
    }

}
