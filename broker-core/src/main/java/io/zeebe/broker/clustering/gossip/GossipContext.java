package io.zeebe.broker.clustering.gossip;

import io.zeebe.broker.clustering.gossip.config.GossipConfiguration;
import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.PeerSelector;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;

public class GossipContext
{
    private GossipConfiguration config;

    private Peer localPeer;
    private PeerList peers;

    protected ClientTransport clientTransport;
    protected BufferingServerTransport serverTransport;

    private PeerSelector peerSelector;

    public GossipConfiguration getConfig()
    {
        return config;
    }

    public void setConfig(GossipConfiguration config)
    {
        this.config = config;
    }

    public void setClientTransport(ClientTransport clientTransport)
    {
        this.clientTransport = clientTransport;
    }

    public ClientTransport getClientTransport()
    {
        return clientTransport;
    }

    public void setServerTransport(BufferingServerTransport serverTransport)
    {
        this.serverTransport = serverTransport;
    }

    public BufferingServerTransport getServerTransport()
    {
        return serverTransport;
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

    public PeerSelector getPeerSelector()
    {
        return peerSelector;
    }

    public void setPeerSelector(PeerSelector peerSelector)
    {
        this.peerSelector = peerSelector;
    }

}
