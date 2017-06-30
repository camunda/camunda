package io.zeebe.transport;

public interface TransportChannelListener
{

    void onChannelClosed(Channel channel);

    void onChannelInterrupted(Channel channel);

    void onChannelOpened(Channel channel);
}
