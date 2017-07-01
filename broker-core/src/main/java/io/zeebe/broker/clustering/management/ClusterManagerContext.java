package io.zeebe.broker.clustering.management;

import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.logstreams.LogStreamsManager;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.transport.ChannelManager;
import io.zeebe.transport.requestresponse.client.TransportConnectionPool;
import io.zeebe.util.actor.ActorScheduler;

public class ClusterManagerContext
{
    private ActorScheduler actorScheduler;
    private Peer localPeer;
    private Subscription subscription;
    private ChannelManager clientChannelPool;
    private TransportConnectionPool connections;
    private Dispatcher sendBuffer;
    private PeerList peers;
    private LogStreamsManager logStreamsManager;

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

    public void setClientChannelPool(ChannelManager clientChannelManager)
    {
        this.clientChannelPool = clientChannelManager;
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
