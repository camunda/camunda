package io.zeebe.gossip;

import io.zeebe.gossip.broadcasts.BroadcastQueue;
import io.zeebe.gossip.events.EventManager;
import io.zeebe.gossip.nodes.NodeManager;
import io.zeebe.transport.*;
import io.zeebe.util.actor.Actor;
import org.agrona.DirectBuffer;

public class Gossip implements Actor, ServerMessageHandler, ServerRequestHandler
{

    private final SocketAddress socketAddress;
    private final GossipConfiguration configuration;
    private final BufferingServerTransport serverTransport;
    private final ClientTransport clientTransport;

    private final ServerSubscriptionController subscriptionController;

    private final NodeManager nodeManager;
    private final BroadcastQueue queue;
    private final EventManager eventManager;

    public Gossip(final SocketAddress socketAddress, final GossipConfiguration configuration, final BufferingServerTransport serverTransport, final ClientTransport clientTransport)
    {
        this.socketAddress = socketAddress;
        this.configuration = configuration;
        this.serverTransport = serverTransport;
        this.clientTransport = clientTransport;

        subscriptionController = new ServerSubscriptionController("gossip", serverTransport, this, this);

        nodeManager = new NodeManager();
        queue = new BroadcastQueue();
        eventManager = new EventManager();
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += subscriptionController.doWork();

        workCount += nodeManager.doWork();
        workCount += queue.doWork();
        workCount += eventManager.doWork();

        return workCount;
    }

    @Override
    public boolean onMessage(final ServerOutput output, final RemoteAddress remoteAddress, final DirectBuffer buffer, final int offset, final int length)
    {
        // TODO: implement
        return false;
    }

    @Override
    public boolean onRequest(final ServerOutput output, final RemoteAddress remoteAddress, final DirectBuffer buffer, final int offset, final int length, final long requestId)
    {
        // TODO: implement
        return false;
    }
}
