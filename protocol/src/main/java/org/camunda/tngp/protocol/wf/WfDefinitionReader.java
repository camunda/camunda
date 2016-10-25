package org.camunda.tngp.protocol.wf;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.protocol.log.MessageHeaderDecoder;
import org.camunda.tngp.protocol.log.WfDefinitionDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

public class WfDefinitionReader implements BufferReader
{
    public static final int MAX_LENGTH = 1024 * 1024 * 2;

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final WfDefinitionDecoder decoder = new WfDefinitionDecoder();

    protected final UnsafeBuffer typeKeyBuffer = new UnsafeBuffer(0, 0);
    protected final UnsafeBuffer resourceBuffer = new UnsafeBuffer(0, 0);

    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        decoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        offset += headerDecoder.blockLength();

        final int wfDefinitionKeyLength = decoder.keyLength();

        offset += WfDefinitionDecoder.keyHeaderLength();

        typeKeyBuffer.wrap(buffer, offset, wfDefinitionKeyLength);

        offset += wfDefinitionKeyLength;

        decoder.limit(offset);

        final int resourceLength = decoder.resourceLength();

        offset += WfDefinitionDecoder.resourceHeaderLength();


        if (resourceLength > 0)
        {
            resourceBuffer.wrap(buffer, offset, resourceLength);
        }
        else
        {
            resourceBuffer.wrap(0, 0);
        }

    }

    public DirectBuffer getTypeKey()
    {
        return typeKeyBuffer;
    }

    public DirectBuffer getResource()
    {
        return resourceBuffer;
    }

    public long id()
    {
        return decoder.id();
    }

}
