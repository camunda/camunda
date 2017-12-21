package io.zeebe.gossip.protocol;

import io.zeebe.gossip.membership.GossipTerm;
import io.zeebe.transport.SocketAddress;
import org.agrona.DirectBuffer;

public interface CustomEvent
{
    GossipTerm getSenderGossipTerm();

    SocketAddress getSenderAddress();

    DirectBuffer getType();

    DirectBuffer getPayload();
}
