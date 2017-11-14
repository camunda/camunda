/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it.clustering;

import static io.zeebe.test.util.TestUtil.doRepeatedly;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.clustering.impl.TopicLeader;
import io.zeebe.client.clustering.impl.TopologyResponse;
import io.zeebe.client.cmd.Request;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.SocketAddress;

public class TopologyObserver
{

    public static final Logger LOG = LoggerFactory.getLogger(TopologyObserver.class);
    public static final int WAIT_FOR_LEADER_RETRIES = 50;

    private final Request<TopologyResponse> requestTopologyCmd;

    public TopologyObserver(final ZeebeClient client)
    {
        requestTopologyCmd = client.requestTopology();
    }

    public void waitForBroker(final SocketAddress socketAddress)
    {
        updateTopology()
            .until(t -> t != null && t.getBrokers().contains(socketAddress),
                "Failed to wait for %s be a known broker", socketAddress);

        LOG.info("Broker {} is known by the cluster", socketAddress);
    }

    public SocketAddress waitForLeader(final int partition, final Set<SocketAddress> socketAddresses)
    {
        final TopologyResponse response = updateTopology()
            .until(t -> t != null && socketAddresses.contains(getLeaderForPartition(t, partition)), WAIT_FOR_LEADER_RETRIES,
                "Failed to wait for %s become leader of partition %d", socketAddresses, partition);

        final SocketAddress leader = getLeaderForPartition(response, partition);

        LOG.info("Broker {} is leader for partition {}", leader, partition);
        return leader;
    }

    public SocketAddress waitForLeader(final int partition)
    {
        final TopologyResponse topology = updateTopology().until(t -> t != null && getLeaderForPartition(t, partition) != null);
        return getLeaderForPartition(topology, partition);
    }

    protected TestUtil.Invocation<TopologyResponse> updateTopology()
    {
        return doRepeatedly(requestTopologyCmd::execute);
    }

    protected static SocketAddress getLeaderForPartition(TopologyResponse resp, int partition)
    {
        for (TopicLeader leader : resp.getTopicLeaders())
        {
            if (partition == leader.getPartitionId())
            {
                return leader.getSocketAddress();
            }
        }

        return null;
    }

}
