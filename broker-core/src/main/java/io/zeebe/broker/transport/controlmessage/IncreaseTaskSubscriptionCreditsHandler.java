/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.transport.controlmessage;

import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;

import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.broker.task.CreditsRequest;
import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.broker.task.processor.TaskSubscription;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.transport.ServerOutput;

public class IncreaseTaskSubscriptionCreditsHandler implements ControlMessageHandler
{
    protected static final CompletableFuture<Void> COMPLETED_FUTURE = CompletableFuture.completedFuture(null);

    protected final TaskSubscription subscription = new TaskSubscription();
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
    public CompletableFuture<Void> handle(DirectBuffer buffer, BrokerEventMetadata eventMetadata)
    {
        subscription.reset();

        subscription.wrap(buffer);

        if (subscription.getCredits() <= 0)
        {
            sendError(eventMetadata, buffer, "Cannot increase task subscription credits. Credits must be positive.");
            return COMPLETED_FUTURE;
        }

        creditsRequest.setCredits(subscription.getCredits());
        creditsRequest.setSubscriberKey(subscription.getSubscriberKey());

        final boolean success = manager.increaseSubscriptionCreditsAsync(creditsRequest);

        if (success)
        {
            final boolean responseScheduled = responseWriter
                .dataWriter(subscription)
                .tryWriteResponse(eventMetadata.getRequestStreamId(), eventMetadata.getRequestId());
            // TODO: proper backpressure

            return COMPLETED_FUTURE;
        }
        else
        {
            sendError(eventMetadata, buffer, "Cannot increase task subscription credits. Capacities exhausted.");
            return COMPLETED_FUTURE;
        }
    }

    protected void sendError(BrokerEventMetadata metadata, DirectBuffer request, String errorMessage)
    {
        final boolean success = errorResponseWriter
            .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
            .errorMessage(errorMessage)
            .failedRequest(request, 0, request.capacity())
            .tryWriteResponseOrLogFailure(metadata.getRequestStreamId(), metadata.getRequestId());
        // TODO: proper backpressure
    }

}
