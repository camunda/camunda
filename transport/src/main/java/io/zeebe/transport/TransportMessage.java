package io.zeebe.transport;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.impl.TransportHeaderDescriptor;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.buffer.DirectBufferWriter;

public class TransportMessage
{
    protected final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    protected BufferWriter writer;
    protected DirectBufferWriter writerAdapter = new DirectBufferWriter();
    protected int remoteStreamId;

    public TransportMessage writer(BufferWriter writer)
    {
        this.writer = writer;
        return this;
    }

    public TransportMessage buffer(DirectBuffer buffer)
    {
        return buffer(buffer, 0, buffer.capacity());
    }

    public TransportMessage buffer(DirectBuffer buffer, int offset, int length)
    {
        return writer(writerAdapter.wrap(buffer, offset, length));
    }

    public TransportMessage remoteAddress(RemoteAddress remoteAddress)
    {
        this.remoteStreamId = remoteAddress.getStreamId();
        return this;
    }

    public TransportMessage remoteStreamId(int remoteStreamId)
    {
        this.remoteStreamId = remoteStreamId;
        return this;
    }

    public TransportMessage reset()
    {
        remoteStreamId = -1;
        writer = null;

        return this;
    }

    public boolean trySend(Dispatcher sendBuffer)
    {
        final int requiredLength = TransportHeaderDescriptor.framedLength(writer.getLength());

        long claimedOffset = -2;

        do
        {
            claimedOffset = sendBuffer.claim(claimedFragment, requiredLength, remoteStreamId);
        }
        while (claimedOffset == -2);

        if (claimedOffset >= 0)
        {
            try
            {
                final MutableDirectBuffer buffer = claimedFragment.getBuffer();

                int writeOffset = claimedFragment.getOffset();

                transportHeaderDescriptor.wrap(buffer, writeOffset)
                    .putProtocolSingleMessage();

                writeOffset += TransportHeaderDescriptor.headerLength();

                writer.write(buffer, writeOffset);

                claimedFragment.commit();

                return true;
            }
            catch (Throwable e)
            {
                claimedFragment.abort();
            }
        }

        return false;
    }
}
