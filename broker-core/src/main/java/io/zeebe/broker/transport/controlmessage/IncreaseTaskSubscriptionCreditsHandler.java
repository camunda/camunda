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

import io.zeebe.broker.task.CreditsRequest;
import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.broker.task.processor.TaskSubscriptionRequest;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;

import java.util.function.BooleanSupplier;

public class IncreaseTaskSubscriptionCreditsHandler implements ControlMessageHandler
{
    protected static final CompletableActorFuture<Void> COMPLETED_FUTURE = CompletableActorFuture.completed(null);

    protected final TaskSubscriptionRequest subscription = new TaskSubscriptionRequest();
    protected final CreditsRequest creditsRequest = new CreditsRequest();

    protected final TaskSubscriptionManager manager;

    protected final ControlMessageResponseWriter responseWriter;
    protected final ErrorResponseWriter errorResponseWriter;


    public IncreaseTaskSubscriptionCreditsHandler(ServerOutput output, TaskSubscriptionManager manager)
    {
        this.errorResponseWriter = new ErrorResponseWriter(output);
        this.responseWriter = new ControlMessageResponseWriter(output);
        this.manager = manager;
    }

    @Override
    public ControlMessageType getMessageType()
    {
        return ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS;
    }

    @Override
    public void handle(ActorControl actor, int partitionId, DirectBuffer buffer, BrokerEventMetadata eventMetadata)
    {
        final long requestId = eventMetadata.getRequestId();
        final int requestStreamId = eventMetadata.getRequestStreamId();
        subscription.reset();
        subscription.wrap(buffer);

        if (subscription.getCredits() <= 0)
        {
            sendResponse(actor,  () ->
            {
                return errorResponseWriter
                    .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
                    .errorMessage("Cannot increase task subscription credits. Credits must be positive.")
                    .tryWriteResponseOrLogFailure(requestStreamId, requestId);
            });
        }
        else
        {
            creditsRequest.setCredits(subscription.getCredits());
            creditsRequest.setSubscriberKey(subscription.getSubscriberKey());

            final boolean success = manager.increaseSubscriptionCreditsAsync(creditsRequest);
            if (success)
            {
                sendResponse(actor, () ->
                {
                    return responseWriter
                        .dataWriter(subscription)
                        .tryWriteResponse(requestStreamId, requestId);
                });
            }
            else
            {
                sendResponse(actor, () ->
                {
                    return errorResponseWriter
                        .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
                        .errorMessage("Cannot increase task subscription credits. Capacities exhausted.")
                        .tryWriteResponseOrLogFailure(requestStreamId, requestId);
                });
            }
        }
    }

    private void sendResponse(ActorControl actor, BooleanSupplier supplier)
    {
        actor.runUntilDone(() ->
        {
            final boolean success = supplier.getAsBoolean();

            if (success)
            {
                actor.done();
            }
            else
            {
                actor.yield();
            }
        });
    }

}
