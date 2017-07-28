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
import io.zeebe.client.impl.Partition;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.time.ClockUtil;


public class ClientTopologyManager implements Actor
{
    public static final long REFRESH_INTERVAL = Duration.ofSeconds(10).toMillis();

    protected final DeferredCommandContext commandContext = new DeferredCommandContext();

    protected final ClientTopologyController clientTopologyController;
    protected final List<CompletableFuture<Void>> refreshFutures;

    protected TopologyImpl topology;
    private ClientTransport transport;
    protected RemoteAddress topologyEndpoint;

    protected long nextRequestTimestamp = 0L;

    public ClientTopologyManager(final ClientTransport transport, final ObjectMapper objectMapper, final SocketAddress... initialBrokers)
    {
        this.transport = transport;
        this.clientTopologyController = new ClientTopologyController(
                transport,
                objectMapper,
                this::onNewTopology,
                this::failRefreshFutures);
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

    public RemoteAddress getLeaderForTopic(final Partition topic)
    {
        if (topic != null)
        {
            return topology.getLeaderForTopic(topic);
        }
        else
        {
            return topology.getRandomBroker();
        }
    }

    public CompletableFuture<Void> refreshNow()
    {
        return commandContext.runAsync(future ->
        {
            refreshFutures.add(future);
            topologyEndpoint = topology.getRandomBroker(); // switch to a different broker on explicit refresh
        });
    }

    protected boolean shouldRefreshTopology()
    {
        return nextRequestTimestamp < ClockUtil.getCurrentTimeInMillis() || !refreshFutures.isEmpty();
    }

    protected void onNewTopology(TopologyResponse topologyResponse)
    {
        recordTopologyRefreshAttempt();

        final TopologyImpl topology = new TopologyImpl();
        topology.update(topologyResponse, transport);
        this.topology = topology;

        refreshFutures.forEach(f -> f.complete(null));
        refreshFutures.clear();
    }

    protected void failRefreshFutures(Exception e)
    {
        recordTopologyRefreshAttempt();
        refreshFutures.forEach(f -> f.completeExceptionally(e));
        refreshFutures.clear();
    }

    protected void recordTopologyRefreshAttempt()
    {
        nextRequestTimestamp = ClockUtil.getCurrentTimeInMillis() + REFRESH_INTERVAL;
    }
}
