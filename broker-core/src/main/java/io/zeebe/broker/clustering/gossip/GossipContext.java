package io.zeebe.broker.clustering.gossip;

import io.zeebe.broker.clustering.gossip.config.GossipConfiguration;
import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.PeerSelector;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.transport.ChannelManager;
import io.zeebe.transport.requestresponse.client.TransportConnectionPool;

public class GossipContext
{
    private GossipConfiguration config;

    private Peer localPeer;
    private PeerList peers;

    private Subscription subscription;
    private ChannelManager clientChannelPool;
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

    public ChannelManager getClientChannelPool()
    {
        return clientChannelPool;
    }

    public void setClientChannelPool(ChannelManager clientChannelPool)
    {
        this.clientChannelPool = clientChannelPool;
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
