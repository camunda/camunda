package org.camunda.tngp.broker.clustering.gossip;

import org.camunda.tngp.broker.clustering.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.gossip.config.GossipConfiguration;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.data.PeerSelector;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public class GossipContext
{
    private GossipConfiguration config;

    private Peer localPeer;
    private PeerList peers;

    private Subscription subscription;
    private ClientChannelManager clientChannelManager;
    private TransportConnectionPool connections;
    private Dispatcher sendBuffer;

    private PeerSelector peerSelector;

    public GossipConfiguration getConfig()
    {
        return config;
    }

    public void setConfig(GossipConfiguration config)
    {
        this.config = config;
    }

    public Peer getLocalPeer()
    {
        return localPeer;
    }

    public void setLocalPeer(Peer localPeer)
    {
        this.localPeer = localPeer;
    }

    public PeerList getPeers()
    {
        return peers;
    }

    public void setPeers(PeerList peers)
    {
        this.peers = peers;
    }

    public Subscription getSubscription()
    {
        return subscription;
    }

    public void setSubscription(Subscription subscription)
    {
        this.subscription = subscription;
    }

    public ClientChannelManager getClientChannelManager()
    {
        return clientChannelManager;
    }

    public void setClientChannelManager(ClientChannelManager clientChannelManager)
    {
        this.clientChannelManager = clientChannelManager;
    }

    public TransportConnectionPool getConnections()
    {
        return connections;
    }

    public void setConnections(TransportConnectionPool connections)
    {
        this.connections = connections;
    }

    public Dispatcher getSendBuffer()
    {
        return sendBuffer;
    }

    public void setSendBuffer(Dispatcher sendBuffer)
    {
        this.sendBuffer = sendBuffer;
    }

    public PeerSelector getPeerSelector()
    {
        return peerSelector;
    }

    public void setPeerSelector(PeerSelector peerSelector)
    {
        this.peerSelector = peerSelector;
    }

}
