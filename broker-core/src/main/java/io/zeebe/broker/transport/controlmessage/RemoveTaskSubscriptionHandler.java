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

import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;

import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.broker.task.processor.TaskSubscriptionRequest;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.transport.ServerOutput;

public class RemoveTaskSubscriptionHandler implements ControlMessageHandler
{
    protected final TaskSubscriptionRequest subscription = new TaskSubscriptionRequest();

    protected final TaskSubscriptionManager manager;

    protected final ControlMessageResponseWriter responseWriter;
    protected final ErrorResponseWriter errorResponseWriter;

    public RemoveTaskSubscriptionHandler(ServerOutput output, TaskSubscriptionManager manager)
    {
        this.errorResponseWriter = new ErrorResponseWriter(output);
        this.responseWriter = new ControlMessageResponseWriter(output);
        this.manager = manager;
    }

    @Override
    public ControlMessageType getMessageType()
    {
        return ControlMessageType.REMOVE_TASK_SUBSCRIPTION;
    }

    @Override
    public ActorFuture<Void> handle(ActorControl actor, int partitionId, DirectBuffer buffer, BrokerEventMetadata eventMetada)
    {
        subscription.reset();

        subscription.wrap(buffer);

        final CompletableActorFuture<Void> completableActorFuture = new CompletableActorFuture<>();
        final ActorFuture<Void> future = manager.removeSubscription(subscription.getSubscriberKey());

        actor.runOnCompletion(future, (v, failure) ->
        {
            if (failure == null)
            {
                responseWriter
                    .dataWriter(subscription)
                    .tryWriteResponse(eventMetada.getRequestStreamId(), eventMetada.getRequestId());
                completableActorFuture.complete(null);
                // TODO: proper backpressure
            }
            else
            {
                errorResponseWriter
                    .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
                    .errorMessage("Cannot remove task subscription. %s", failure.getMessage())
                    .failedRequest(buffer, 0, buffer.capacity())
                    .tryWriteResponseOrLogFailure(eventMetada.getRequestStreamId(), eventMetada.getRequestId());
                completableActorFuture.completeExceptionally(failure);
                // TODO: proper backpressure
            }
        });

        return completableActorFuture;
    }

}
