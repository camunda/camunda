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

import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.broker.task.processor.TaskSubscriptionRequest;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;

public class RemoveTaskSubscriptionHandler extends AbstractControlMessageHandler
{
    protected final TaskSubscriptionRequest subscription = new TaskSubscriptionRequest();

    protected final TaskSubscriptionManager manager;

    public RemoveTaskSubscriptionHandler(final ServerOutput output, final TaskSubscriptionManager manager)
    {
        super(output);
        this.manager = manager;
    }

    @Override
    public ControlMessageType getMessageType()
    {
        return ControlMessageType.REMOVE_TASK_SUBSCRIPTION;
    }

    @Override
    public void handle(final ActorControl actor, final int partitionId, final DirectBuffer buffer, final RecordMetadata eventMetadata)
    {
        final int requestStreamId = eventMetadata.getRequestStreamId();
        final long requestId = eventMetadata.getRequestId();

        subscription.reset();
        subscription.wrap(buffer);

        final long subscriberKey = subscription.getSubscriberKey();
        final ActorFuture<Void> future = manager.removeSubscription(subscriberKey);
        actor.runOnCompletion(future, (aVoid, throwable) ->
        {
            if (throwable == null)
            {
                sendResponse(actor, requestStreamId, requestId, subscription);
            }
            else
            {
                sendErrorResponse(actor, requestStreamId, requestId, "Cannot remove task subscription. %s", throwable.getMessage());
            }
        });
    }

}
