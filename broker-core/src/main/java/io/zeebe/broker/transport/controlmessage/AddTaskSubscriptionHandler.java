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

import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;

import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.broker.task.processor.TaskSubscription;
import io.zeebe.broker.task.processor.TaskSubscriptionRequest;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;

public class AddTaskSubscriptionHandler extends AbstractControlMessageHandler
{
    protected final TaskSubscriptionManager manager;

    public AddTaskSubscriptionHandler(final ServerOutput output, final TaskSubscriptionManager manager)
    {
        super(output);
        this.manager = manager;
    }

    @Override
    public ControlMessageType getMessageType()
    {
        return ControlMessageType.ADD_TASK_SUBSCRIPTION;
    }

    @Override
    public void handle(final ActorControl actor, final int partitionId, final DirectBuffer buffer, final RecordMetadata eventMetada)
    {
        final TaskSubscriptionRequest request = new TaskSubscriptionRequest();
        request.wrap(cloneBuffer(buffer));

        final long requestId = eventMetada.getRequestId();
        final int requestStreamId = eventMetada.getRequestStreamId();

        final TaskSubscription taskSubscription = new TaskSubscription(partitionId, request.getLockTaskType(),
                request.getLockDuration(), request.getLockOwner(), requestStreamId);
        taskSubscription.setCredits(request.getCredits());

        final ActorFuture<Void> future = manager.addSubscription(taskSubscription);

        actor.runOnCompletion(future, ((aVoid, throwable) ->
        {
            if (throwable == null)
            {
                final long subscriberKey = taskSubscription.getSubscriberKey();
                request.setSubscriberKey(subscriberKey);

                sendResponse(actor, requestStreamId, requestId, request);
            }
            else
            {
                sendErrorResponse(actor, requestStreamId, requestId, "Cannot add task subscription. %s", throwable.getMessage());
            }
        }));
    }

}
