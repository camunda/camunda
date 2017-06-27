package io.zeebe.broker.clustering.management;

import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.logstreams.LogStreamsManager;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.actor.ActorScheduler;

public class ClusterManagerContext
{
    private ActorScheduler actorScheduler;
    private Peer localPeer;
    private PeerList peers;
    private LogStreamsManager logStreamsManager;
    protected ClientTransport clientTransport;
    protected BufferingServerTransport serverTransport;

    public ActorScheduler getActorScheduler()
    {
        return actorScheduler;
    }

    public void setActorScheduler(ActorScheduler actorScheduler)
    {
        this.actorScheduler = actorScheduler;
    }

    public Peer getLocalPeer()
    {
        return localPeer;
    }

    public void setLocalPeer(Peer localPeer)
    {
        this.localPeer = localPeer;
    }

    public BufferingServerTransport getServerTransport()
    {
        return serverTransport;
    }

    public void setServerTransport(BufferingServerTransport serverTransport)
    {
        this.serverTransport = serverTransport;
    }

    public ClientTransport getClientTransport()
    {
        return clientTransport;
    }

    public void setClientTransport(ClientTransport clientTransport)
    {
        this.clientTransport = clientTransport;
    }

    public PeerList getPeers()
    {
        return peers;
    }

    public void setPeers(PeerList peers)
    {
        this.peers = peers;
    }

    public LogStreamsManager getLogStreamsManager()
    {
        return logStreamsManager;
    }

    public void setLogStreamsManager(LogStreamsManager logStreamsManager)
    {
        this.logStreamsManager = logStreamsManager;
    }

}
