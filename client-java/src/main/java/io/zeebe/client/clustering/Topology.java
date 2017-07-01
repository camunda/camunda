package io.zeebe.client.clustering;

import io.zeebe.client.impl.Topic;
import io.zeebe.transport.SocketAddress;

public interface Topology
{
    SocketAddress getLeaderForTopic(Topic topic);

    SocketAddress getRandomBroker();

}
