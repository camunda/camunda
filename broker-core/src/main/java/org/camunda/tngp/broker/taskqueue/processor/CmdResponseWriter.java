package org.camunda.tngp.broker.taskqueue.processor;

import java.util.Objects;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandResponseEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.util.buffer.BufferWriter;

public class CmdResponseWriter
{
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandResponseEncoder responseEncoder = new ExecuteCommandResponseEncoder();

    protected final int headerSize = TransportHeaderDescriptor.HEADER_LENGTH +
            RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH +
            ExecuteCommandResponseEncoder.BLOCK_LENGTH +
            ExecuteCommandResponseEncoder.BLOCK_LENGTH +
            ExecuteCommandResponseEncoder.bytesKeyHeaderLength() +
            ExecuteCommandResponseEncoder.eventHeaderLength();

    protected final Dispatcher sendBuffer;
    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    protected int topicId;
    protected long longKey;
    protected final UnsafeBuffer bytesKey = new UnsafeBuffer(0, 0);
    protected BufferWriter eventWriter;
    protected BrokerEventMetadata metadata;

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

    public CmdResponseWriter brokerEventMetadata(BrokerEventMetadata metadata)
    {
        this.metadata = metadata;
        return this;
    }

    public CmdResponseWriter eventWriter(BufferWriter writer)
    {
        this.eventWriter = writer;
        return this;
    }

    public boolean tryWriteResponse()
    {
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(eventWriter);

        final int eventLengh = eventWriter.getLength();
        final int responseLength = headerSize + bytesKey.capacity() + eventLengh;

        long claimedOffset = -1;

        do
        {
            // dispatcher header
            claimedOffset = sendBuffer.claim(claimedFragment, responseLength, metadata.getReqChannelId());
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
                buffer.putLong(RequestResponseProtocolHeaderDescriptor.connectionIdOffset(offset), metadata.getReqConnectionId());
                buffer.putLong(RequestResponseProtocolHeaderDescriptor.requestIdOffset(offset), metadata.getReqRequestId());
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
                    .putBytesKey(bytesKey, 0, bytesKey.capacity());

                int limit = responseEncoder.limit();
                buffer.putShort(limit, (short) eventLengh);
                limit += ExecuteCommandResponseEncoder.eventHeaderLength();
                eventWriter.write(buffer, limit);

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
                reset();
            }
        }

        return isSent;
    }

    protected void reset()
    {
        topicId = (int) ExecuteCommandResponseEncoder.topicIdNullValue();
        longKey = ExecuteCommandResponseEncoder.longKeyNullValue();
        bytesKey.wrap(0, 0);
        eventWriter = null;
        metadata = null;
    }

}
