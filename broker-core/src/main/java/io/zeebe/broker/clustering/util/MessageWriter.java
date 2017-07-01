package io.zeebe.broker.clustering.util;

import static io.zeebe.transport.protocol.Protocols.*;

import org.agrona.MutableDirectBuffer;
import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.protocol.TransportHeaderDescriptor;
import io.zeebe.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import io.zeebe.transport.singlemessage.SingleMessageHeaderDescriptor;
import io.zeebe.util.buffer.BufferWriter;

public class MessageWriter
{
    protected final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    protected final RequestResponseProtocolHeaderDescriptor requestResponseHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();

    protected final Dispatcher sendBuffer;
    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    private short protocol;
    private int channelId;
    private long connectionId;
    private long requestId;
    private BufferWriter message;

    public MessageWriter(final Dispatcher sendBuffer)
    {
        this.sendBuffer = sendBuffer;
    }

    public MessageWriter protocol(final short protocol)
    {
        this.protocol = protocol;
        return this;
    }

    public MessageWriter channelId(final int channelId)
    {
        this.channelId = channelId;
        return this;
    }

    public MessageWriter connectionId(final long connectionId)
    {
        this.connectionId = connectionId;
        return this;
    }

    public MessageWriter requestId(final long requestId)
    {
        this.requestId = requestId;
        return this;
    }

    public MessageWriter message(final BufferWriter message)
    {
        this.message = message;
        return this;
    }

    public boolean tryWriteMessage()
    {
        // TODO: assert values!

        final int messageLength = message.getLength();

        // transport header size
        int length = TransportHeaderDescriptor.HEADER_LENGTH;

        switch (protocol)
        {
            case REQUEST_RESPONSE:
            {
                // request/response header size
                length += RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH;
                break;
            }
            default:
            {
                // single message header size
                length += SingleMessageHeaderDescriptor.HEADER_LENGTH;
            }
        }

        // message size
        length += messageLength;

        long claimedOffset = -1;

        do
        {
            claimedOffset = sendBuffer.claim(claimedFragment, length, channelId);
        }
        while (claimedOffset == -2);

        boolean isSent = false;

        if (claimedOffset >= 0)
        {
            try
            {
                writeResponseToFragment(messageLength);

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
            .protocolId(protocol);

        offset += TransportHeaderDescriptor.HEADER_LENGTH;

        switch (protocol)
        {
            case REQUEST_RESPONSE:
            {
                // request/response protocol header
                requestResponseHeaderDescriptor.wrap(buffer, offset)
                    .connectionId(connectionId)
                    .requestId(requestId);
                offset += RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH;
                break;
            }
            default:
            {
                // single message header
                offset += SingleMessageHeaderDescriptor.HEADER_LENGTH;
            }
        }

        // message
        message.write(buffer, offset);
    }

    protected void reset()
    {
        protocol = -1;
        channelId = -1;
        connectionId = -1L;
        requestId = -1L;
        message = null;
    }

}
