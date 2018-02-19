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
package io.zeebe.broker.event.handler;

import io.zeebe.broker.event.processor.CloseSubscriptionRequest;
import io.zeebe.broker.event.processor.TopicSubscriptionService;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.broker.transport.controlmessage.ControlMessageHandler;
import io.zeebe.broker.transport.controlmessage.ControlMessageResponseWriter;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;

import java.util.concurrent.CompletableFuture;

public class RemoveTopicSubscriptionHandler implements ControlMessageHandler
{
    protected final CloseSubscriptionRequest request = new CloseSubscriptionRequest();

    protected final TopicSubscriptionService subscriptionService;
    protected final ControlMessageResponseWriter responseWriter;
    protected final ErrorResponseWriter errorResponseWriter;

    public RemoveTopicSubscriptionHandler(ServerOutput output, TopicSubscriptionService subscriptionService)
    {
        this.errorResponseWriter = new ErrorResponseWriter(output);
        this.responseWriter = new ControlMessageResponseWriter(output);
        this.subscriptionService = subscriptionService;
    }

    @Override
    public ControlMessageType getMessageType()
    {
        return ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION;
    }

    @Override
    public ActorFuture<Void> handle(int partitionId, DirectBuffer buffer, BrokerEventMetadata metadata)
    {
        final CompletableActorFuture<Void> completableActorFuture = new CompletableActorFuture<>();
        request.reset();
        request.wrap(buffer);

        final CompletableFuture<Void> future = subscriptionService.closeSubscriptionAsync(
            partitionId,
            request.getSubscriberKey()
        );

        future.handle((v, failure) ->
        {
            if (failure == null)
            {
                responseWriter
                    .dataWriter(request)
                    .tryWriteResponse(metadata.getRequestStreamId(), metadata.getRequestId());
                // TODO: proper backpressure
                completableActorFuture.complete(null);
            }
            else
            {
                errorResponseWriter
                    .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
                    .errorMessage("Cannot close topic subscription. %s", failure.getMessage())
                    .failedRequest(buffer, 0, buffer.capacity())
                    .tryWriteResponseOrLogFailure(metadata.getRequestStreamId(), metadata.getRequestId());
                // TODO: proper backpressure
                completableActorFuture.completeExceptionally(failure);
            }
            return null;
        });
        return completableActorFuture;
    }


}
