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
package io.zeebe.broker.transport.controlmessage;

import java.util.concurrent.CompletableFuture;

import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.broker.task.processor.TaskSubscription;
import io.zeebe.broker.task.processor.TaskSubscriptionRequest;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.transport.ServerOutput;
import org.agrona.DirectBuffer;

public class AddTaskSubscriptionHandler implements ControlMessageHandler
{
    protected final TaskSubscriptionRequest request = new TaskSubscriptionRequest();

    protected final TaskSubscriptionManager manager;

    protected final ControlMessageResponseWriter responseWriter;
    protected final ErrorResponseWriter errorResponseWriter;

    public AddTaskSubscriptionHandler(ServerOutput output, TaskSubscriptionManager manager)
    {
        this.manager = manager;
        this.errorResponseWriter = new ErrorResponseWriter(output);
        this.responseWriter = new ControlMessageResponseWriter(output);
    }

    @Override
    public ControlMessageType getMessageType()
    {
        return ControlMessageType.ADD_TASK_SUBSCRIPTION;
    }

    @Override
    public CompletableFuture<Void> handle(DirectBuffer buffer, BrokerEventMetadata eventMetada)
    {
        request.reset();
        request.wrap(buffer);

        final long requestId = eventMetada.getRequestId();
        final int requestStreamId = eventMetada.getRequestStreamId();

        final TaskSubscription taskSubscription = new TaskSubscription(request.getTopicName(), request.getPartitionId(), request.getLockTaskType(),
                request.getLockDuration(), request.getLockOwner(), requestStreamId);
        taskSubscription.setCredits(request.getCredits());

        final CompletableFuture<Void> future = manager.addSubscription(taskSubscription);

        return future.handle((v, failure) ->
        {
            if (failure == null)
            {
                request.setSubscriberKey(taskSubscription.getSubscriberKey());

                final boolean success = responseWriter
                    .dataWriter(request)
                    .tryWriteResponse(eventMetada.getRequestStreamId(), eventMetada.getRequestId());
                // TODO: proper back pressure
            }
            else
            {
                errorResponseWriter
                    .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
                    .errorMessage("Cannot add task subscription. %s", failure.getMessage())
                    .failedRequest(buffer, 0, buffer.capacity())
                    .tryWriteResponseOrLogFailure(requestStreamId, requestId);
                // TODO: proper back pressure
            }
            return null;
        });
    }

}
