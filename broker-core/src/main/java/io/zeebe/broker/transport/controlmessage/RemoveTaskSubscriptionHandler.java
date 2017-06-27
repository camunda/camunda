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
import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.broker.task.processor.TaskSubscription;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.transport.ServerOutput;

public class RemoveTaskSubscriptionHandler implements ControlMessageHandler
{
    protected final TaskSubscription subscription = new TaskSubscription();

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
    public CompletableFuture<Void> handle(DirectBuffer buffer, BrokerEventMetadata eventMetada)
    {
        subscription.reset();

        subscription.wrap(buffer);

        final CompletableFuture<Void> future = manager.removeSubscription(subscription.getSubscriberKey());

        return future.handle((v, failure) ->
        {
            if (failure == null)
            {
                final boolean success = responseWriter
                    .dataWriter(subscription)
                    .tryWriteResponse(eventMetada.getRequestStreamId(), eventMetada.getRequestId());
                // TODO: proper backpressure
            }
            else
            {
                final boolean success = errorResponseWriter
                    .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
                    .errorMessage("Cannot remove task subscription. %s", failure.getMessage())
                    .failedRequest(buffer, 0, buffer.capacity())
                    .tryWriteResponseOrLogFailure(eventMetada.getRequestStreamId(), eventMetada.getRequestId());
                // TODO: proper backpressure
            }
            return null;
        });
    }

}
