package io.zeebe.transport;

public interface TransportListener
{
    void onConnectionEstablished(RemoteAddress remoteAddress);

    void onConnectionClosed(RemoteAddress remoteAddress);
}
