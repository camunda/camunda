package io.zeebe.broker.transport.clientapi;

import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.transport.TransportMessage;
import io.zeebe.transport.impl.RequestResponseHeaderDescriptor;
import io.zeebe.transport.impl.TransportHeaderDescriptor;

public class BufferingServerOutput implements ServerOutput
{

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ErrorResponseDecoder errorDecoder = new ErrorResponseDecoder();

    protected List<DirectBuffer> sentResponses = new ArrayList<>();

    @Override
    public boolean sendMessage(TransportMessage transportMessage)
    {
        throw new RuntimeException("not yet implemented");
    }

    @Override
    public boolean sendResponse(ServerResponse response)
    {
        final UnsafeBuffer buf = new UnsafeBuffer(new byte[response.getLength()]);
        response.write(buf, 0);
        sentResponses.add(buf);
        return true;
    }

    public List<DirectBuffer> getSentResponses()
    {
        return sentResponses;
    }

    public ErrorResponseDecoder getAsErrorResponse(int index)
    {
        final DirectBuffer sentResponse = sentResponses.get(index);
        final int offset = TransportHeaderDescriptor.HEADER_LENGTH + RequestResponseHeaderDescriptor.HEADER_LENGTH;
        headerDecoder.wrap(sentResponse, offset);

        errorDecoder.wrap(sentResponse, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        return errorDecoder;
    }
}
