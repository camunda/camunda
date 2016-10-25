package org.camunda.tngp.protocol.wf;

import org.camunda.tngp.protocol.log.MessageHeaderDecoder;
import org.camunda.tngp.protocol.log.WfDefinitionRequestDecoder;
import org.camunda.tngp.protocol.log.WfDefinitionRequestType;
import org.camunda.tngp.util.buffer.BufferReader;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class WfDefinitionRequestReader implements BufferReader
{

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected WfDefinitionRequestDecoder bodyDecoder = new WfDefinitionRequestDecoder();

    protected UnsafeBuffer resourceBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        offset += headerDecoder.blockLength();
        offset += WfDefinitionRequestDecoder.resourceHeaderLength();

        resourceBuffer.wrap(buffer, offset, bodyDecoder.resourceLength());
    }

    public WfDefinitionRequestType type()
    {
        return bodyDecoder.type();
    }

    public DirectBuffer resource()
    {
        return resourceBuffer;
    }

}
