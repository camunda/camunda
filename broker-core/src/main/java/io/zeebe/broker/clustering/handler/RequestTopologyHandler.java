/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.handler;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.management.ClusterManager;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.broker.transport.controlmessage.ControlMessageHandler;
import io.zeebe.broker.transport.controlmessage.ControlMessageResponseWriter;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;

public class RequestTopologyHandler extends Actor implements ControlMessageHandler
{
    protected final ClusterManager clusterManager;
    protected final ControlMessageResponseWriter responseWriter;
    protected final ErrorResponseWriter errorResponseWriter;
    private final ActorScheduler actorScheduler;

    private ActorFuture<Topology> topologyActorFuture;
    private CompletableActorFuture<Void> completableFuture;
    private DirectBuffer requestBuffer;
    private BrokerEventMetadata metadata;

    public RequestTopologyHandler(ActorScheduler actorScheduler, final ServerOutput ouput, final ClusterManager clusterManager)
    {
        this.clusterManager = clusterManager;
        this.responseWriter = new ControlMessageResponseWriter(ouput);
        this.errorResponseWriter = new ErrorResponseWriter(ouput);
        this.actorScheduler = actorScheduler;
    }

    @Override
    public ControlMessageType getMessageType()
    {
        return ControlMessageType.REQUEST_TOPOLOGY;
    }

    @Override
    protected void onActorStarted()
    {
        actor.runOnCompletion(topologyActorFuture, ((topology, throwable) ->
        {
            if (throwable == null)
            {
                responseWriter.dataWriter(topology);

                if (!responseWriter.tryWriteResponse(metadata.getRequestStreamId(), metadata.getRequestId()))
                {
                    errorResponseWriter.errorCode(ErrorCode.REQUEST_WRITE_FAILURE)
                        .errorMessage("Cannot write topology response.")
                        .failedRequest(requestBuffer, 0, requestBuffer.capacity())
                        .tryWriteResponseOrLogFailure(metadata.getRequestStreamId(), metadata.getRequestId());
                }
                completableFuture.complete(null);
            }
            else
            {
                Loggers.CLUSTERING_LOGGER.debug("Problem on requesting topology. Exception {}", throwable);
                errorResponseWriter.errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
                    .errorMessage("Cannot request topology!")
                    .failedRequest(requestBuffer, 0, requestBuffer.capacity())
                    .tryWriteResponseOrLogFailure(metadata.getRequestStreamId(), metadata.getRequestId());

                completableFuture.completeExceptionally(throwable);

            }
        }));
    }

    @Override
    public ActorFuture<Void> handle(int partitionId, final DirectBuffer buffer, final BrokerEventMetadata metadata)
    {
        // call cluster manager
        this.requestBuffer = buffer;
        this.metadata = metadata;

        this.completableFuture = new CompletableActorFuture<>();
        this.topologyActorFuture = clusterManager.requestTopology();
        actorScheduler.submitActor(this);

        return completableFuture;
    }
}
