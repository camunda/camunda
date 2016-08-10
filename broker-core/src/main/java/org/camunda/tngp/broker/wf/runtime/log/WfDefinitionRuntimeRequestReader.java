package org.camunda.tngp.broker.wf.runtime.log;

import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.WfDefinitionRequestType;
import org.camunda.tngp.taskqueue.data.WfDefinitionRuntimeRequestDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class WfDefinitionRuntimeRequestReader implements BufferReader
{
    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected WfDefinitionRuntimeRequestDecoder bodyDecoder = new WfDefinitionRuntimeRequestDecoder();

    protected UnsafeBuffer keyBuffer = new UnsafeBuffer(0, 0);
    protected UnsafeBuffer resourceBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());
        offset += headerDecoder.blockLength();

        final int keyLength = bodyDecoder.keyLength();
        offset += WfDefinitionRuntimeRequestDecoder.keyHeaderLength();

        keyBuffer.wrap(buffer, offset, keyLength);

        final int resourceLength = bodyDecoder.resourceLength();
        offset += WfDefinitionRuntimeRequestDecoder.resourceHeaderLength();

        if (resourceLength > 0)
        {
            resourceBuffer.wrap(buffer, offset, resourceLength);
        }
        else
        {
            resourceBuffer.wrap(0, 0);
        }

    }

    public WfDefinitionRequestType type()
    {
        return bodyDecoder.type();
    }

    public long id()
    {
        return bodyDecoder.id();
    }

    public DirectBuffer key()
    {
        return keyBuffer;
    }

    public DirectBuffer resource()
    {
        return resourceBuffer;
    }

}
