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
package org.camunda.tngp.broker.transport.controlmessage;

import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.task.TaskSubscriptionManager;
import org.camunda.tngp.broker.task.processor.TaskSubscription;
import org.camunda.tngp.broker.transport.clientapi.ErrorResponseWriter;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.ErrorCode;

public class RemoveTaskSubscriptionHandler implements ControlMessageHandler
{
    protected final TaskSubscription subscription = new TaskSubscription();

    protected final TaskSubscriptionManager manager;

    protected final ControlMessageResponseWriter responseWriter;
    protected final ErrorResponseWriter errorResponseWriter;

    public RemoveTaskSubscriptionHandler(TaskSubscriptionManager manager, ControlMessageResponseWriter responseWriter, ErrorResponseWriter errorResponseWriter)
    {
        this.manager = manager;
        this.responseWriter = responseWriter;
        this.errorResponseWriter = errorResponseWriter;
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
                responseWriter
                    .brokerEventMetadata(eventMetada)
                    .dataWriter(subscription)
                    .tryWriteResponse();
            }
            else
            {
                errorResponseWriter
                    .metadata(eventMetada)
                    .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
                    .errorMessage("Cannot remove task subscription. %s", failure.getMessage())
                    .failedRequest(buffer, 0, buffer.capacity())
                    .tryWriteResponseOrLogFailure();
            }
            return null;
        });
    }

}
