package org.camunda.tngp.transport;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.DirectBuffer;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

public class CollectingHandler implements TransportChannelHandler
{

    protected volatile int messagesReceived = 0;
    protected volatile int controlFramesReceived = 0;
    protected int lastMessageId = -1;

    @Override
    public void onChannelOpened(Channel transportChannel)
    {
    }

    @Override
    public void onChannelClosed(Channel transportChannel)
    {
    }

    @Override
    public void onChannelSendError(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
    }

    @Override
    public boolean onChannelReceive(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        final int messageId = buffer.getInt(offset + TransportHeaderDescriptor.headerLength());
        assertThat(messageId - 1).isEqualTo(lastMessageId);
        lastMessageId++;
        messagesReceived++;
        return true;
    }

    @Override
    public boolean onControlFrame(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        controlFramesReceived++;
        return true;
    }

    public int getMessagesReceived()
    {
        return messagesReceived;
    }

    public int getControlFramesReceived()
    {
        return controlFramesReceived;
    }

}
