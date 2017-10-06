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

import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;

import io.zeebe.broker.clustering.gossip.Gossip;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.broker.transport.controlmessage.ControlMessageHandler;
import io.zeebe.broker.transport.controlmessage.ControlMessageResponseWriter;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.transport.ServerOutput;

public class RequestTopologyHandler implements ControlMessageHandler
{

    protected final Gossip gossip;
    protected final ControlMessageResponseWriter responseWriter;
    protected final ErrorResponseWriter errorResponseWriter;

    public RequestTopologyHandler(final ServerOutput ouput, final Gossip gossip)
    {
        this.gossip = gossip;
        this.responseWriter = new ControlMessageResponseWriter(ouput);
        this.errorResponseWriter = new ErrorResponseWriter(ouput);
    }

    @Override
    public ControlMessageType getMessageType()
    {
        return ControlMessageType.REQUEST_TOPOLOGY;
    }

    @Override
    public CompletableFuture<Void> handle(int partitionId, final DirectBuffer buffer, final BrokerEventMetadata metadata)
    {
        return gossip.getTopology()
            .handle((topology, failure) ->
            {
                if (failure == null)
                {
                    responseWriter
                        .dataWriter(topology);

                    if (!responseWriter.tryWriteResponse(metadata.getRequestStreamId(), metadata.getRequestId()))
                    {
                        errorResponseWriter
                            .errorCode(ErrorCode.REQUEST_WRITE_FAILURE)
                            .errorMessage("Cannot write topology response.")
                            .failedRequest(buffer, 0, buffer.capacity())
                            .tryWriteResponseOrLogFailure(metadata.getRequestStreamId(), metadata.getRequestId());
                    }
                }
                else
                {
                    errorResponseWriter
                        .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
                        .errorMessage("Cannot close topic subscription. %s", failure.getMessage())
                        .failedRequest(buffer, 0, buffer.capacity())
                        .tryWriteResponseOrLogFailure(metadata.getRequestStreamId(), metadata.getRequestId());
                }

                return null;
            });

    }

}
