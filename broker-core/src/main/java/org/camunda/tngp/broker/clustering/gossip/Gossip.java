package org.camunda.tngp.broker.clustering.gossip;

import static org.camunda.tngp.clustering.gossip.PeerState.*;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.data.PeerListIterator;
import org.camunda.tngp.broker.clustering.gossip.handler.GossipFragmentHandler;
import org.camunda.tngp.broker.clustering.gossip.protocol.GossipController;

public class Gossip implements Agent
{
    private final Peer peer;
    private final PeerList peers;

    private final GossipController gossipController;
    private final GossipFragmentHandler fragmentHandler;
    public static final String GOSSIP_FILE_NAME = "gossip.tngp";

    public Gossip(final GossipContext context)
    {
        this.peer = context.getLocalPeer();
        this.peers = context.getPeers();

        this.gossipController = new GossipController(context);
        this.fragmentHandler = new GossipFragmentHandler(this, context.getSubscription());
    }

    @Override
    public String roleName()
    {
        return "gossip";
    }

    public void open()
    {
        final PeerListIterator iterator = peers.iterator();
        while (iterator.hasNext())
        {
            final Peer peer = iterator.next();
            if (peer.state() == SUSPECT)
            {
                peer.state(ALIVE);
            }

            peers.set(iterator.position(), peer);
        }

        gossipController.open();
    }

    public void close()
    {
        gossipController.close();
    }

    @Override
    public int doWork() throws Exception
    {
        int workcount = 0;

        workcount += gossipController.doWork();
        workcount += fragmentHandler.doWork();

        return workcount;
    }

    public Peer peer()
    {
        return peer;
    }

    public PeerList peers()
    {
        return peers;
    }

    public int onGossipRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId, final long connectionId, final long requestId)
    {
        return gossipController.onGossipRequest(buffer, offset, length, channelId, connectionId, requestId);
    }

    public int onProbeRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId, final long connectionId, final long requestId)
    {
        return gossipController.onProbeRequest(buffer, offset, length, channelId, connectionId, requestId);
    }
}
