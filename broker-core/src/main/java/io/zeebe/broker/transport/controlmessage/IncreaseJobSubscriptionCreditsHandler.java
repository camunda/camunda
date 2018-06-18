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

import io.zeebe.broker.job.CreditsRequest;
import io.zeebe.broker.job.JobSubscriptionManager;
import io.zeebe.broker.job.processor.JobSubscriptionRequest;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.sched.ActorControl;
import org.agrona.DirectBuffer;

public class IncreaseJobSubscriptionCreditsHandler extends AbstractControlMessageHandler {

  protected final JobSubscriptionRequest subscription = new JobSubscriptionRequest();
  protected final CreditsRequest creditsRequest = new CreditsRequest();

  protected final JobSubscriptionManager manager;

  public IncreaseJobSubscriptionCreditsHandler(
      final ServerOutput output, final JobSubscriptionManager manager) {
    super(output);
    this.manager = manager;
  }

  @Override
  public ControlMessageType getMessageType() {
    return ControlMessageType.INCREASE_JOB_SUBSCRIPTION_CREDITS;
  }

  @Override
  public void handle(
      final ActorControl actor,
      final int partitionId,
      final DirectBuffer buffer,
      final RecordMetadata eventMetadata) {
    final long requestId = eventMetadata.getRequestId();
    final int requestStreamId = eventMetadata.getRequestStreamId();
    subscription.reset();
    subscription.wrap(buffer);

    if (subscription.getCredits() <= 0) {
      sendErrorResponse(
          actor,
          requestStreamId,
          requestId,
          "Cannot increase job subscription credits. Credits must be positive.");
    } else {
      creditsRequest.setCredits(subscription.getCredits());
      creditsRequest.setSubscriberKey(subscription.getSubscriberKey());

      final boolean success = manager.increaseSubscriptionCreditsAsync(creditsRequest);
      if (success) {
        sendResponse(actor, requestStreamId, requestId, subscription);
      } else {
        sendErrorResponse(
            actor,
            requestStreamId,
            requestId,
            "Cannot increase job subscription credits. Capacities exhausted.");
      }
    }
  }
}
