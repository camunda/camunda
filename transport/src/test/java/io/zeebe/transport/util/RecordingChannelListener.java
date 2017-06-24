package io.zeebe.transport.util;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.TransportListener;

public class RecordingChannelListener implements TransportListener
{

    protected List<RemoteAddress> closedConnections = new ArrayList<>();
    protected List<RemoteAddress> openedConnections = new ArrayList<>();

    public List<RemoteAddress> getClosedConnections()
    {
        return closedConnections;
    }

    public List<RemoteAddress> getOpenedConnections()
    {
        return openedConnections;
    }

    @Override
    public void onConnectionEstablished(RemoteAddress remoteAddress)
    {
        openedConnections.add(remoteAddress);
    }

    @Override
    public void onConnectionClosed(RemoteAddress remoteAddress)
    {
        closedConnections.add(remoteAddress);
    }

}