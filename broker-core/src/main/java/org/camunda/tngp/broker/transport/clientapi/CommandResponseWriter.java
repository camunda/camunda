package org.camunda.tngp.broker.transport.clientapi;

import static org.camunda.tngp.dispatcher.impl.log.LogBufferAppender.RESULT_PADDING_AT_END_OF_PARTITION;

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

public class CommandResponseWriter
{
    protected final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    protected final RequestResponseProtocolHeaderDescriptor protocolHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandResponseEncoder responseEncoder = new ExecuteCommandResponseEncoder();

    protected final int headerSize = TransportHeaderDescriptor.HEADER_LENGTH +
            RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH +
            MessageHeaderEncoder.ENCODED_LENGTH +
            ExecuteCommandResponseEncoder.BLOCK_LENGTH +
            ExecuteCommandResponseEncoder.bytesKeyHeaderLength() +
            ExecuteCommandResponseEncoder.eventHeaderLength();

    protected final Dispatcher sendBuffer;
    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    protected int topicId;
    protected long longKey;

    protected final UnsafeBuffer bytesKey = new UnsafeBuffer(0, 0);
    protected final UnsafeBuffer event = new UnsafeBuffer(0, 0);

    protected BufferWriter eventWriter;
    protected BrokerEventMetadata metadata;

    public CommandResponseWriter(Dispatcher sendBuffer)
    {
        this.sendBuffer = sendBuffer;
    }

    public CommandResponseWriter topicId(int topicId)
    {
        this.topicId = topicId;
        return this;
    }

    public CommandResponseWriter longKey(long key)
    {
        this.longKey = key;
        return this;
    }

    public CommandResponseWriter bytesKey(DirectBuffer buffer)
    {
        bytesKey.wrap(buffer, 0, buffer.capacity());
        return this;
    }

    public CommandResponseWriter brokerEventMetadata(BrokerEventMetadata metadata)
    {
        this.metadata = metadata;
        return this;
    }

    public CommandResponseWriter eventWriter(BufferWriter writer)
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
            claimedOffset = sendBuffer.claim(claimedFragment, responseLength, metadata.getReqChannelId());
        }
        while (claimedOffset == RESULT_PADDING_AT_END_OF_PARTITION);

        boolean isSent = false;

        if (claimedOffset >= 0)
        {
            try
            {
                writeResponseToFragment(eventLengh);

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

    protected void writeResponseToFragment(final int eventLengh)
    {
        final MutableDirectBuffer buffer = claimedFragment.getBuffer();
        int offset = claimedFragment.getOffset();

        // transport protocol header
        transportHeaderDescriptor.wrap(buffer, offset)
            .protocolId(Protocols.REQUEST_RESPONSE);

        offset += TransportHeaderDescriptor.HEADER_LENGTH;

        // request/response protocol header
        protocolHeaderDescriptor.wrap(buffer, offset)
            .connectionId(metadata.getReqConnectionId())
            .requestId(metadata.getReqRequestId());

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

        event.wrap(new byte[eventLengh]);
        eventWriter.write(event, 0);

        responseEncoder
            .topicId(topicId)
            .longKey(longKey)
            .putBytesKey(bytesKey, 0, bytesKey.capacity())
            .putEvent(event, 0, eventLengh);
    }

    protected void reset()
    {
        topicId = (int) ExecuteCommandResponseEncoder.topicIdNullValue();
        longKey = ExecuteCommandResponseEncoder.longKeyNullValue();
        bytesKey.wrap(0, 0);
        event.wrap(0, 0);
        eventWriter = null;
        metadata = null;
    }

}
