package org.camunda.tngp.broker.clustering.management;

import org.camunda.tngp.broker.clustering.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public class ClusterManagerContext
{
    private AgentRunnerServices agentRunner;
    private Peer localPeer;
    private Subscription subscription;
    private ClientChannelManager clientChannelManager;
    private TransportConnectionPool connections;
    private Dispatcher sendBuffer;
    private PeerList peers;

    public AgentRunnerServices getAgentRunner()
    {
        return agentRunner;
    }

    public void setAgentRunner(AgentRunnerServices agentRunner)
    {
        this.agentRunner = agentRunner;
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

    public PeerList getPeers()
    {
        return peers;
    }

    public void setPeers(PeerList peers)
    {
        this.peers = peers;
    }
}
