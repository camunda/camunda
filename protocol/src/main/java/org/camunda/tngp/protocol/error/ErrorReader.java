package org.camunda.tngp.protocol.error;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.util.buffer.BufferReader;


public class ErrorReader implements BufferReader
{
    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected ErrorDecoder bodyDecoder = new ErrorDecoder();

    protected final UnsafeBuffer errorMessageBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        offset += headerDecoder.blockLength();
        offset += ErrorDecoder.errorMessageHeaderLength();

        errorMessageBuffer.wrap(buffer, offset, bodyDecoder.errorMessageLength());
    }

    public String errorMessage()
    {
        final byte[] bytes = new byte[errorMessageBuffer.capacity()];

        errorMessageBuffer.getBytes(0, bytes);

        return new String(bytes, StandardCharsets.UTF_8);
    }

    public int componentCode()
    {
        return bodyDecoder.componentCode();
    }

    public int detailCode()
    {
        return bodyDecoder.detailCode();
    }

}
