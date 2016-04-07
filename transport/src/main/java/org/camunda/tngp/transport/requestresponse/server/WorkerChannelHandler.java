package org.camunda.tngp.transport.requestresponse.server;

import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.ringbuffer.OneToOneRingBuffer;

public class WorkerChannelHandler implements TransportChannelHandler
{
    protected OneToOneRingBuffer receiveBuffer;

    public WorkerChannelHandler(OneToOneRingBuffer receiveBuffer)
    {
        this.receiveBuffer = receiveBuffer;
    }

    @Override
    public void onChannelOpened(TransportChannel transportChannel)
    {
        // ignore
    }

    @Override
    public void onChannelClosed(TransportChannel transportChannel)
    {
        // ignore
    }

    @Override
    public void onChannelSendError(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        System.err.println("send error on channel "+transportChannel);
    }

    @Override
    public boolean onChannelReceive(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        return receiveBuffer.write(transportChannel.getId(), buffer, offset, length);
    }

    @Override
    public void onControlFrame(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        // drop
    }
}
