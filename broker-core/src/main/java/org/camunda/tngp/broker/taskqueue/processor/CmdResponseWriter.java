package org.camunda.tngp.broker.taskqueue.processor;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.requests.LogStreamRequest;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandResponseEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;

public class CmdResponseWriter
{
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandResponseEncoder responseEncoder = new ExecuteCommandResponseEncoder();

    protected final int headerSize = TransportHeaderDescriptor.HEADER_LENGTH +
            RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH +
            ExecuteCommandResponseEncoder.BLOCK_LENGTH +
            ExecuteCommandResponseEncoder.BLOCK_LENGTH +
            ExecuteCommandResponseEncoder.bytesKeyHeaderLength() +
            ExecuteCommandResponseEncoder.dataHeaderLength();

    protected final Dispatcher sendBuffer;
    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    protected int topicId;
    protected long longKey;
    protected LogStreamRequest request;
    protected final UnsafeBuffer bytesKey = new UnsafeBuffer(0, 0);
    protected final UnsafeBuffer event = new UnsafeBuffer(0, 0);

    public CmdResponseWriter(Dispatcher sendBuffer)
    {
        this.sendBuffer = sendBuffer;
    }

    public CmdResponseWriter topicId(int topicId)
    {
        this.topicId = topicId;
        return this;
    }

    public CmdResponseWriter longKey(long key)
    {
        this.longKey = key;
        return this;
    }

    public CmdResponseWriter bytesKey(DirectBuffer buffer)
    {
        bytesKey.wrap(buffer, 0, buffer.capacity());
        return this;
    }

    public CmdResponseWriter forRequest(LogStreamRequest request)
    {
        this.request = request;
        return this;
    }

    public CmdResponseWriter event(DirectBuffer buffer, int offset, int length)
    {
        event.wrap(buffer, offset, length);
        return this;
    }

    public boolean tryWriteResponse()
    {
        final int responseLength = headerSize + bytesKey.capacity() +  event.capacity();

        long claimedOffset = -1;

        do
        {
            // dispatcher header
            claimedOffset = sendBuffer.claim(claimedFragment, responseLength, request.getChannelId());
        }
        while (claimedOffset == -2);

        boolean isSent = false;

        if (claimedOffset >= 0)
        {
            try
            {
                final MutableDirectBuffer buffer = claimedFragment.getBuffer();
                int offset = claimedFragment.getOffset();

                // transport protocol header
                buffer.putShort(TransportHeaderDescriptor.protocolIdOffset(offset), Protocols.REQUEST_RESPONSE);
                offset += TransportHeaderDescriptor.HEADER_LENGTH;

                // request/response protocol header
                buffer.putLong(RequestResponseProtocolHeaderDescriptor.connectionIdOffset(offset), request.getConnectionId());
                buffer.putLong(RequestResponseProtocolHeaderDescriptor.requestIdOffset(offset), request.getRequestId());
                offset += RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH;

                // protocol header
                messageHeaderEncoder.wrap(buffer, offset);

                messageHeaderEncoder.blockLength(responseEncoder.sbeBlockLength())
                    .templateId(responseEncoder.sbeTemplateId())
                    .schemaId(responseEncoder.sbeSchemaId())
                    .version(responseEncoder.sbeSchemaVersion());

                offset += messageHeaderEncoder.encodedLength();

                // protocol message
                responseEncoder.wrap(buffer, offset);

                responseEncoder.topicId(topicId)
                    .longKey(longKey)
                    .putBytesKey(bytesKey, 0, bytesKey.capacity())
                    .putData(event, 0, event.capacity());

                claimedFragment.commit();

                isSent = true;
            }
            catch (RuntimeException e)
            {
                claimedFragment.abort();
                throw e;
            }
            finally
            {
                request.close();
                reset();
            }
        }

        return isSent;
    }

    protected void reset()
    {
        topicId = (int) ExecuteCommandResponseEncoder.topicIdNullValue();
        longKey = ExecuteCommandResponseEncoder.longKeyNullValue();
        request = null;
        bytesKey.wrap(0, 0);
        event.wrap(0, 0);
    }

}
