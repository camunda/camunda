package org.camunda.tngp.client.clustering;

import org.camunda.tngp.client.impl.Topic;
import org.camunda.tngp.transport.SocketAddress;

public interface Topology
{
    SocketAddress getLeaderForTopic(Topic topic);

    SocketAddress getRandomBroker();

}
