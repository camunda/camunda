package org.camunda.tngp.broker.clustering.gossip;

import static org.camunda.tngp.clustering.gossip.PeerState.ALIVE;
import static org.camunda.tngp.clustering.gossip.PeerState.SUSPECT;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.data.PeerListIterator;
import org.camunda.tngp.broker.clustering.gossip.handler.GossipFragmentHandler;
import org.camunda.tngp.broker.clustering.gossip.protocol.GossipController;
import org.camunda.tngp.util.actor.Actor;

public class Gossip implements Actor
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
    public String name()
    {
        return "gossip";
    }

    @Override
    public int getPriority(long now)
    {
        return PRIORITY_LOW;
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

    public static String fileName(String directory)
    {
        return directory + GOSSIP_FILE_NAME;
    }

}
