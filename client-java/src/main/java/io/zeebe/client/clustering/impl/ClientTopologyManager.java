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
package io.zeebe.client.clustering.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.clustering.Topology;
import io.zeebe.client.impl.Loggers;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.time.ClockUtil;


public class ClientTopologyManager implements Actor
{
    /**
     * Interval in which the topology is refreshed even if the client is idle
     */
    public static final long MAX_REFRESH_INTERVAL_MILLIS = Duration.ofSeconds(10).toMillis();

    /**
     * Shortest possible interval in which the topology is refreshed,
     * even if the client is constantly making new requests that require topology refresh
     */
    public static final long MIN_REFRESH_INTERVAL_MILLIS = 300;

    protected final DeferredCommandContext commandContext = new DeferredCommandContext();

    protected final ClientTopologyController clientTopologyController;
    protected final List<CompletableFuture<Void>> refreshFutures;

    protected TopologyImpl topology;
    private ClientTransport transport;
    protected RemoteAddress topologyEndpoint;

    protected long nextLatestPossibleRequestTimestamp = 0L;
    protected long nextEarliestPossibleRequestTimestamp = 0L;

    public ClientTopologyManager(final ClientTransport transport, final ObjectMapper objectMapper, long requestTimeout, final SocketAddress... initialBrokers)
    {
        this.transport = transport;
        this.clientTopologyController = new ClientTopologyController(
                transport,
                objectMapper,
                this::onNewTopology,
                this::failRefreshFutures,
                requestTimeout);
        this.topology = new TopologyImpl();

        for (SocketAddress socketAddress : initialBrokers)
        {
            topology.addBroker(transport.registerRemoteAddress(socketAddress));
        }

        this.refreshFutures = new ArrayList<>();
        topologyEndpoint = topology.getRandomBroker();
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += commandContext.doWork();

        if (clientTopologyController.isRequestInProgress())
        {
            workCount += clientTopologyController.doWork();
        }
        else
        {
            if (shouldRefreshTopology() && !clientTopologyController.isRequestInProgress())
            {
                recordTopologyRefreshAttempt();
                clientTopologyController.triggerRefresh(topologyEndpoint);
                workCount++;
            }
        }

        return workCount;
    }

    public Topology getTopology()
    {
        return topology;
    }

    public RemoteAddress getLeaderForPartition(final int partition)
    {
        return topology.getLeaderForPartition(partition);
    }

    public RemoteAddress getArbitraryBroker()
    {
        return topology.getRandomBroker();
    }

    public CompletableFuture<Void> refreshNow()
    {
        return commandContext.runAsync(future ->
        {
            refreshFutures.add(future);
            topologyEndpoint = topology.getRandomBroker(); // switch to a different broker on explicit refresh
        });
    }

    public int getPartitionForTopic(String topic, int offset)
    {
        final List<Integer> partitions = topology.getPartitionsOfTopic(topic);

        if (partitions != null && !partitions.isEmpty())
        {
            return partitions.get(offset % partitions.size());
        }
        else
        {
            return -1;
        }
    }

    protected boolean shouldRefreshTopology()
    {
        final long now = ClockUtil.getCurrentTimeInMillis();
        return nextLatestPossibleRequestTimestamp < now ||
                (!refreshFutures.isEmpty() && nextEarliestPossibleRequestTimestamp < now);
    }

    protected CompletableFuture<Void> updateTopology(TopologyResponse topologyResponse)
    {
        final TopologyResponse response = topologyResponse;
        return commandContext.runAsync(future -> {
            onNewTopology(response);
            future.complete(null);
        });
    }

    protected void onNewTopology(TopologyResponse topologyResponse)
    {
        Loggers.CLIENT_LOGGER.debug("On new topology: {}", topologyResponse);
        final TopologyImpl topology = new TopologyImpl();
        topology.update(topologyResponse, transport);
        this.topology = topology;

        refreshFutures.forEach(f -> f.complete(null));
        refreshFutures.clear();
    }

    protected void failRefreshFutures(Exception e)
    {
        refreshFutures.forEach(f -> f.completeExceptionally(e));
        refreshFutures.clear();
    }

    protected void recordTopologyRefreshAttempt()
    {
        final long now = ClockUtil.getCurrentTimeInMillis();
        nextLatestPossibleRequestTimestamp = now + MAX_REFRESH_INTERVAL_MILLIS;
        nextEarliestPossibleRequestTimestamp = now + MIN_REFRESH_INTERVAL_MILLIS;
    }
}
