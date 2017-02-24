package org.camunda.tngp.broker.clustering.gossip.service;

import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class LocalPeerService implements Service<Peer>
{
    private final Endpoint clientEndpoint;
    private final Endpoint managementEndpoint;
    private final Endpoint replicationEndpoint;

    private Peer localPeer;

    public LocalPeerService(final Endpoint clientEndpoint, final Endpoint managementEndpoint, final Endpoint replicationEndpoint)
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
