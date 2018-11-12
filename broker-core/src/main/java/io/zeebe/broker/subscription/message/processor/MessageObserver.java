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
package io.zeebe.broker.subscription.message.processor;

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.state.MessageState;
import io.zeebe.broker.subscription.message.state.MessageSubscriptionState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.util.sched.ActorControl;
import java.time.Duration;

public class MessageObserver implements StreamProcessorLifecycleAware {

  public static final Duration MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL = Duration.ofSeconds(60);

  public static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final TopologyManager topologyManager;
  private final SubscriptionCommandSender subscriptionCommandSender;
  private final MessageState messageState;
  private final MessageSubscriptionState subscriptionState;

  public MessageObserver(
      MessageState messageState,
      MessageSubscriptionState subscriptionState,
      SubscriptionCommandSender subscriptionCommandSender,
      TopologyManager topologyManager) {
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.topologyManager = topologyManager;
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {

    final ActorControl actor = streamProcessor.getActor();
    final TypedStreamEnvironment env = streamProcessor.getEnvironment();
    final LogStream logStream = env.getStream();

    subscriptionCommandSender.init(topologyManager, actor, logStream);

    final MessageTimeToLiveChecker timeToLiveChecker =
        new MessageTimeToLiveChecker(env.buildCommandWriter(), messageState);
    streamProcessor
        .getActor()
        .runAtFixedRate(MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL, timeToLiveChecker);

    final PendingMessageSubscriptionChecker pendingSubscriptionChecker =
        new PendingMessageSubscriptionChecker(
            subscriptionCommandSender, subscriptionState, SUBSCRIPTION_TIMEOUT.toMillis());
    actor.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);
  }
}
