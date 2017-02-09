package org.camunda.tngp.broker.transport.controlmessage;

import static org.camunda.tngp.dispatcher.impl.log.LogBufferAppender.RESULT_PADDING_AT_END_OF_PARTITION;

import java.util.Objects;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.protocol.clientapi.ControlMessageResponseEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.util.buffer.BufferWriter;

public class ControlMessageResponseWriter
{
    protected final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    protected final RequestResponseProtocolHeaderDescriptor protocolHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ControlMessageResponseEncoder responseEncoder = new ControlMessageResponseEncoder();

    protected final int headerSize = TransportHeaderDescriptor.HEADER_LENGTH +
            RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH +
            MessageHeaderEncoder.ENCODED_LENGTH +
            ControlMessageResponseEncoder.BLOCK_LENGTH +
            ControlMessageResponseEncoder.dataHeaderLength();

    protected final Dispatcher sendBuffer;
    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    protected final UnsafeBuffer data = new UnsafeBuffer(0, 0);

    protected BufferWriter dataWriter;
    protected BrokerEventMetadata metadata;

    public ControlMessageResponseWriter(Dispatcher sendBuffer)
    {
        this.sendBuffer = sendBuffer;
    }

    public ControlMessageResponseWriter brokerEventMetadata(BrokerEventMetadata metadata)
    {
        this.metadata = metadata;
        return this;
    }

    public ControlMessageResponseWriter dataWriter(BufferWriter writer)
    {
        this.dataWriter = writer;
        return this;
    }

    public boolean tryWriteResponse()
    {
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(dataWriter);

        final int dataLengh = dataWriter.getLength();
        final int responseLength = headerSize + dataLengh;

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
                writeResponseToFragment(dataLengh);

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

    protected void writeResponseToFragment(final int dataLength)
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

        data.wrap(new byte[dataLength]);
        dataWriter.write(data, 0);

        responseEncoder.putData(data, 0, dataLength);
    }

    protected void reset()
    {
        data.wrap(0, 0);
        dataWriter = null;
        metadata = null;
    }

}
