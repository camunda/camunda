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
package io.zeebe.client.impl.clustering;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.zeebe.client.api.commands.Topology;
import io.zeebe.client.cmd.BrokerErrorException;
import io.zeebe.client.impl.ControlMessageRequestHandler;
import io.zeebe.client.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.transport.*;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;

public class ClientTopologyManager extends Actor
{
    /**
     * Interval in which the topology is refreshed even if the client is idle
     */
    public static final Duration MAX_REFRESH_INTERVAL_MILLIS = Duration.ofSeconds(10);

    /**
     * Shortest possible interval in which the topology is refreshed,
     * even if the client is constantly making new requests that require topology refresh
     */
    public static final Duration MIN_REFRESH_INTERVAL_MILLIS = Duration.ofMillis(300);

    protected final ClientOutput output;
    protected final ClientTransport transport;
    protected final ClientTransport internalTransport;

    protected final AtomicReference<ClusterStateImpl> topology;
    protected final List<CompletableActorFuture<ClusterState>> nextTopologyFutures = new ArrayList<>();

    protected final ControlMessageRequestHandler requestWriter;
    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final ErrorResponseDecoder errorResponseDecoder = new ErrorResponseDecoder();

    protected int refreshAttempt = 0;
    protected long lastRefreshTime = -1;


    public ClientTopologyManager(ClientTransport transport,
        ClientTransport internalTransport,
        ZeebeObjectMapperImpl objectMapper,
        RemoteAddress initialContact)
    {
        this.transport = transport;
        this.internalTransport = internalTransport;
        this.output = transport.getOutput();

        this.topology = new AtomicReference<>(new ClusterStateImpl(initialContact));
        this.requestWriter = new ControlMessageRequestHandler(objectMapper, new TopologyRequestImpl(null, null));
    }

    @Override
    protected void onActorStarted()
    {
        actor.run(this::refreshTopology);
    }

    public ActorFuture<Void> close()
    {
        return actor.close();
    }

    public ClusterStateImpl getTopology()
    {
        return topology.get();
    }

    public ActorFuture<ClusterState> requestTopology()
    {
        final CompletableActorFuture<ClusterState> future = new CompletableActorFuture<>();

        actor.call(() ->
        {
            final boolean isFirstStagedRequest = nextTopologyFutures.isEmpty();
            nextTopologyFutures.add(future);

            if (isFirstStagedRequest)
            {
                scheduleNextRefresh();
            }
        });

        return future;
    }

    private void scheduleNextRefresh()
    {
        final long now = ActorClock.currentTimeMillis();
        final long timeSinceLastRefresh = now - lastRefreshTime;

        if (timeSinceLastRefresh >= MIN_REFRESH_INTERVAL_MILLIS.toMillis())
        {
            refreshTopology();
        }
        else
        {
            final long timeoutToNextRefresh = MIN_REFRESH_INTERVAL_MILLIS.toMillis() - timeSinceLastRefresh;
            actor.runDelayed(Duration.ofMillis(timeoutToNextRefresh), () -> refreshTopology());
        }
    }

    public void provideTopology(Topology response)
    {
        actor.call(() ->
        {
            // TODO: not sure we should complete the refresh futures in this case,
            //   as the response could be older than the time when the future was submitted
            onNewTopology(response);
        });
    }

    private void refreshTopology()
    {
        final RemoteAddress endpoint = topology.get().getRandomBroker();
        final RemoteAddress internalRemoteAddress = internalTransport.registerRemoteAddress(endpoint.getAddress());
        final ActorFuture<ClientResponse> responseFuture = internalTransport.getOutput()
            .sendRequest(internalRemoteAddress, requestWriter, Duration.ofSeconds(1));

        refreshAttempt++;
        lastRefreshTime = ActorClock.currentTimeMillis();
        actor.runOnCompletion(responseFuture, this::handleResponse);
        actor.runDelayed(MAX_REFRESH_INTERVAL_MILLIS, scheduleIdleRefresh());
    }

    /**
     * Only schedules topology refresh if there was no refresh attempt in the last ten seconds
     */
    private Runnable scheduleIdleRefresh()
    {
        final int currentAttempt = refreshAttempt;

        return () ->
        {
            // if no topology refresh attempt was made in the meantime
            if (currentAttempt == refreshAttempt)
            {
                actor.run(this::refreshTopology);
            }
        };
    }

    private void handleResponse(ClientResponse response, Throwable t)
    {
        if (t == null)
        {
            final TopologyImpl topologyResponse = decodeTopology(response.getResponseBuffer());
            onNewTopology(topologyResponse);
        }
        else
        {
            failRefreshFutures(t);
        }
    }

    private void onNewTopology(Topology response)
    {
        this.topology.set(new ClusterStateImpl(response, transport::registerRemoteAddress));
        completeRefreshFutures();
    }

    private void completeRefreshFutures()
    {
        nextTopologyFutures.forEach(f -> f.complete(topology.get()));
        nextTopologyFutures.clear();
    }

    private void failRefreshFutures(Throwable t)
    {
        nextTopologyFutures.forEach(f -> f.completeExceptionally("Could not refresh topology", t));
        nextTopologyFutures.clear();
    }

    private TopologyImpl decodeTopology(DirectBuffer encodedTopology)
    {
        messageHeaderDecoder.wrap(encodedTopology, 0);

        final int blockLength = messageHeaderDecoder.blockLength();
        final int version = messageHeaderDecoder.version();

        final int responseMessageOffset = messageHeaderDecoder.encodedLength();

        if (requestWriter.handlesResponse(messageHeaderDecoder))
        {
            try
            {
                return (TopologyImpl) requestWriter.getResult(encodedTopology, responseMessageOffset, blockLength, version);
            }
            catch (final Exception e)
            {
                throw new RuntimeException("Unable to parse topic list from broker response", e);
            }
        }
        else if (messageHeaderDecoder.schemaId() == ErrorResponseDecoder.SCHEMA_ID && messageHeaderDecoder.templateId() == ErrorResponseDecoder.TEMPLATE_ID)
        {
            errorResponseDecoder.wrap(encodedTopology, 0, blockLength, version);
            throw new BrokerErrorException(errorResponseDecoder.errorCode(), errorResponseDecoder.errorData());
        }
        else
        {
            throw new RuntimeException(String.format("Unexpected response format. Schema %s and template %s.", messageHeaderDecoder.schemaId(), messageHeaderDecoder.templateId()));
        }
    }

}
