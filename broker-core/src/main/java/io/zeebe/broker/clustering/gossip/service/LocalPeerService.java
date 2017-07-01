package io.zeebe.broker.clustering.gossip.service;

import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.SocketAddress;

public class LocalPeerService implements Service<Peer>
{
    private final SocketAddress clientEndpoint;
    private final SocketAddress managementEndpoint;
    private final SocketAddress replicationEndpoint;

    private Peer localPeer;

    public LocalPeerService(final SocketAddress clientEndpoint, final SocketAddress managementEndpoint, final SocketAddress replicationEndpoint)
    {
        this.clientEndpoint = clientEndpoint;
        this.managementEndpoint = managementEndpoint;
        this.replicationEndpoint = replicationEndpoint;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        this.localPeer = new Peer();

        this.localPeer.heartbeat().generation(System.currentTimeMillis());
        this.localPeer.heartbeat().version(0);

        this.localPeer.clientEndpoint().wrap(clientEndpoint);
        this.localPeer.managementEndpoint().wrap(managementEndpoint);
        this.localPeer.replicationEndpoint().wrap(replicationEndpoint);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
    }

    @Override
    public Peer get()
    {
        return localPeer;
    }

}
