package org.camunda.tngp.broker.wf.runtime;

import java.util.Arrays;

import org.camunda.tngp.broker.wf.runtime.handler.StartProcessInstanceHandler;
import org.camunda.tngp.protocol.wf.runtime.MessageHeaderDecoder;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import uk.co.real_logic.agrona.DirectBuffer;

public class StartProcessInstanceRequestReader implements BufferReader
{

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected StartWorkflowInstanceDecoder bodyDecoder = new StartWorkflowInstanceDecoder();

    protected byte[] wfTypeKey = new byte[StartProcessInstanceHandler.WF_TYPE_KEY_MAX_LENGTH];

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        int wfTypeKeyLength = bodyDecoder.wfTypeKeyLength();
        // TODO: check max length

        offset += wfTypeKeyLength;

        buffer.getBytes(offset, wfTypeKey, 0, wfTypeKeyLength);
        Arrays.fill(wfTypeKey, wfTypeKeyLength, wfTypeKey.length, (byte) 0);
    }

    public long wfTypeId()
    {
        return bodyDecoder.wfTypeId();
    }

    public byte[] wfTypeKey()
    {
        return wfTypeKey;
    }

}
