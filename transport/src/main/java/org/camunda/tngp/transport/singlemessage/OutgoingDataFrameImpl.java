package org.camunda.tngp.transport.singlemessage;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.util.buffer.BufferWriter;

public class OutgoingDataFrameImpl implements OutgoingDataFrame
{

    protected final ClaimedFragment claimedFragment = new ClaimedFragment();
    protected TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    protected final DataFramePoolImpl framePool;
    protected final Dispatcher sendBuffer;

    protected UnsafeBuffer buffer = new UnsafeBuffer(0, 0);

    public OutgoingDataFrameImpl(DataFramePoolImpl framePool, Dispatcher sendBuffer)
    {
        this.framePool = framePool;
        this.sendBuffer = sendBuffer;
    }

    @Override
    public MutableDirectBuffer getBuffer()
    {
        return buffer;
    }

    @Override
    public void write(BufferWriter writer)
    {
        writer.write(buffer, 0);
    }

    @Override
    public void commit()
    {

        if (claimedFragment.isOpen())
        {
            claimedFragment.commit();
        }
        else
        {
            System.err.println("Could not commit data frame. Claimed send buffer fragment not open");
        }

        // always close after commit, the frame is no longer useful once committed
        close();
    }

    @Override
    public void abort()
    {
        if (claimedFragment.isOpen())
        {
            claimedFragment.abort();
        }

        close();
    }

    @Override
    public void close()
    {
        framePool.reclaim(this);
    }

    /**
     * @param messageLength required payload size
     * @param channelId channel to send message on
     *
     * @return true if opening the frame was successful
     */
    public boolean open(int messageLength, int channelId)
    {
        final int headerLength = TransportHeaderDescriptor.headerLength() +
                SingleMessageHeaderDescriptor.HEADER_LENGTH;

        final int framedLength = headerLength + messageLength;

        long claimedPosition;
        do
        {
            claimedPosition = sendBuffer.claim(claimedFragment, framedLength, channelId);
        }
        while (claimedPosition == -2);

        if (claimedPosition >= 0)
        {
            writeHeader();
            buffer.wrap(claimedFragment.getBuffer(), claimedFragment.getOffset() + headerLength, messageLength);
            return true;
        }
        else
        {
            abort();
            return false;
        }
    }

    public boolean isOpen()
    {
        return claimedFragment.isOpen();
    }

    protected void writeHeader()
    {
        // protocol header
        transportHeaderDescriptor.wrap(claimedFragment.getBuffer(), claimedFragment.getOffset())
            .protocolId(Protocols.FULL_DUPLEX_SINGLE_MESSAGE);

        // here may come a single-message-specific header if any
    }

}
