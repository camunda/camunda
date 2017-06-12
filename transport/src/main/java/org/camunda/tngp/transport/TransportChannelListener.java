package org.camunda.tngp.transport;

public interface TransportChannelListener
{

    void onChannelClosed(Channel channel);

    void onChannelInterrupted(Channel channel);

    void onChannelOpened(Channel channel);
}
