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

import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;

import io.zeebe.broker.event.processor.CloseSubscriptionRequest;
import io.zeebe.broker.event.processor.TopicSubscriptionService;
import io.zeebe.broker.transport.controlmessage.AbstractControlMessageHandler;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;

public class RemoveTopicSubscriptionHandler extends AbstractControlMessageHandler {
  protected final CloseSubscriptionRequest request = new CloseSubscriptionRequest();

  protected final TopicSubscriptionService subscriptionService;

  public RemoveTopicSubscriptionHandler(
      final ServerOutput output, final TopicSubscriptionService subscriptionService) {
    super(output);
    this.subscriptionService = subscriptionService;
  }

  @Override
  public ControlMessageType getMessageType() {
    return ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION;
  }

  @Override
  public void handle(
      final ActorControl actor,
      final int partitionId,
      final DirectBuffer buffer,
      final RecordMetadata metadata) {
    final int requestStreamId = metadata.getRequestStreamId();
    final long requestId = metadata.getRequestId();

    final CloseSubscriptionRequest request = new CloseSubscriptionRequest();
    request.wrap(cloneBuffer(buffer));

    final ActorFuture<Void> future =
        subscriptionService.closeSubscriptionAsync(partitionId, request.getSubscriberKey());
    actor.runOnCompletion(
        future,
        ((aVoid, throwable) -> {
          if (throwable == null) {
            sendResponse(actor, requestStreamId, requestId, request);
          } else {
            sendErrorResponse(
                actor,
                requestStreamId,
                requestId,
                "Cannot close topic subscription. %s",
                throwable.getMessage());
          }
        }));
  }
}
