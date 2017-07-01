package io.zeebe.broker.transport.clientapi;

import static io.zeebe.dispatcher.impl.log.LogBufferAppender.RESULT_PADDING_AT_END_OF_PARTITION;

import org.agrona.MutableDirectBuffer;
import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.protocol.Protocols;
import io.zeebe.transport.protocol.TransportHeaderDescriptor;
import io.zeebe.transport.singlemessage.SingleMessageHeaderDescriptor;
import io.zeebe.util.buffer.BufferWriter;

/**
 * Writes a message according to the full duplex single message protocol
 */
public class SingleMessageWriter
{
    protected static final int STATIC_HEADER_LENGTH = TransportHeaderDescriptor.HEADER_LENGTH +
            SingleMessageHeaderDescriptor.HEADER_LENGTH;

    protected final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();

    protected Dispatcher sendBuffer;
    protected ClaimedFragment claimedFragment = new ClaimedFragment();

    public SingleMessageWriter(Dispatcher sendBuffer)
    {
        this.sendBuffer = sendBuffer;
    }

    public boolean tryWrite(int channelId, BufferWriter bodyWriter)
    {
        final int responseLength = STATIC_HEADER_LENGTH + bodyWriter.getLength();

        long claimedOffset = -1;

        do
        {
            claimedOffset = sendBuffer.claim(claimedFragment, responseLength, channelId);
        }
        while (claimedOffset == RESULT_PADDING_AT_END_OF_PARTITION);

        boolean isSent = false;

        if (claimedOffset >= 0)
        {
            try
            {
                writeToFragment(bodyWriter);

                claimedFragment.commit();
                isSent = true;
            }
            catch (RuntimeException e)
            {
                claimedFragment.abort();
                throw e;
            }
        }

        return isSent;
    }

    protected void writeToFragment(BufferWriter bodyWriter)
    {
        final MutableDirectBuffer buffer = claimedFragment.getBuffer();
        int offset = claimedFragment.getOffset();

        // transport protocol header
        transportHeaderDescriptor.wrap(buffer, offset)
            .protocolId(Protocols.FULL_DUPLEX_SINGLE_MESSAGE);

        offset += TransportHeaderDescriptor.HEADER_LENGTH;
        offset += SingleMessageHeaderDescriptor.HEADER_LENGTH;

        bodyWriter.write(buffer, offset);
    }

}
