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
package io.zeebe.broker.system.log;

import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;

import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.broker.transport.controlmessage.ControlMessageHandler;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.transport.ServerOutput;

public class RequestPartitionsMessageHandler implements ControlMessageHandler
{
    protected final SystemPartitionManager systemPartitionManager;
    protected final ErrorResponseWriter errorWriter;

    public RequestPartitionsMessageHandler(ServerOutput output, SystemPartitionManager systemPartitionManager)
    {
        this.systemPartitionManager = systemPartitionManager;
        this.errorWriter = new ErrorResponseWriter(output);
    }

    @Override
    public ControlMessageType getMessageType()
    {
        return ControlMessageType.REQUEST_PARTITIONS;
    }

    @Override
    public CompletableFuture<Void> handle(int partitionId, DirectBuffer buffer, BrokerEventMetadata metadata)
    {
        final int requestStreamId = metadata.getRequestStreamId();
        final long requestId = metadata.getRequestId();

        if (partitionId != Protocol.SYSTEM_PARTITION)
        {
            sendErrorResponse(ErrorCode.REQUEST_PROCESSING_FAILURE, "Partitions request must address the system partition " + Protocol.SYSTEM_PARTITION, buffer, requestStreamId, requestId);
            return CompletableFuture.completedFuture(null);
        }

        final CompletableFuture<Void> handlerFuture = systemPartitionManager.sendPartitions(requestStreamId, requestId);
        if (handlerFuture == null)
        {
            // it is important that partition not found is returned here to signal a client that it may have addressed a broker
            // that appeared as the system partition leader but is not (yet) able to respond
            sendErrorResponse(ErrorCode.PARTITION_NOT_FOUND, "System partition processor not available", buffer, requestStreamId, requestId);
            return CompletableFuture.completedFuture(null);
        }
        else
        {
            return handlerFuture;
        }
    }

    protected void sendErrorResponse(ErrorCode errorCode, String message, DirectBuffer request, int requestStream, long requestId)
    {
        errorWriter.errorCode(errorCode)
            .errorMessage(message)
            .failedRequest(request, 0, request.capacity())
            .tryWriteResponse(requestStream, requestId);
        // TODO: backpressure
    }

}
