/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.impl.subscription.topic;

import io.zeebe.gateway.impl.CommandImpl;
import io.zeebe.gateway.impl.RequestManager;
import io.zeebe.gateway.impl.command.TopicSubscriptionCommandImpl;
import io.zeebe.gateway.impl.event.TopicSubscriptionEventImpl;
import io.zeebe.gateway.impl.record.RecordImpl;
import io.zeebe.protocol.intent.SubscriptionIntent;

public class AcknowledgeSubscribedEventCommandImpl extends CommandImpl<TopicSubscriptionEventImpl> {
  private final TopicSubscriptionCommandImpl command =
      new TopicSubscriptionCommandImpl(SubscriptionIntent.ACKNOWLEDGE);

  public AcknowledgeSubscribedEventCommandImpl(
      final RequestManager commandManager, final int partitionId) {
    super(commandManager);

    command.setPartitionId(partitionId);
  }

  public AcknowledgeSubscribedEventCommandImpl subscriptionName(final String subscriptionName) {
    command.setName(subscriptionName);
    return this;
  }

  public AcknowledgeSubscribedEventCommandImpl ackPosition(final long position) {
    this.command.setAckPosition(position);
    return this;
  }

  @Override
  public RecordImpl getCommand() {
    return command;
  }
}
