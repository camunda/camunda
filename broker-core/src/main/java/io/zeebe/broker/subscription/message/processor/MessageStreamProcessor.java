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
import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.state.MessageDataStore;
import io.zeebe.broker.subscription.message.state.MessageSubscriptionDataStore;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.util.sched.ActorControl;
import java.time.Duration;

public class MessageStreamProcessor implements StreamProcessorLifecycleAware {

  public static final Duration MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL = Duration.ofSeconds(60);

  public static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final MessageDataStore messageStore = new MessageDataStore();
  private final MessageSubscriptionDataStore subscriptionStore = new MessageSubscriptionDataStore();

  private final TopologyManager topologyManager;
  private final SubscriptionCommandSender subscriptionCommandSender;

  public MessageStreamProcessor(
      SubscriptionCommandSender subscriptionCommandSender, TopologyManager topologyManager) {
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.topologyManager = topologyManager;
  }

  public TypedStreamProcessor createStreamProcessors(TypedStreamEnvironment env) {

    return env.newStreamProcessor()
        .keyGenerator(new KeyGenerator(0, 1))
        .onCommand(
            ValueType.MESSAGE,
            MessageIntent.PUBLISH,
            new PublishMessageProcessor(messageStore, subscriptionStore, subscriptionCommandSender))
        .onCommand(
            ValueType.MESSAGE, MessageIntent.DELETE, new DeleteMessageProcessor(messageStore))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.OPEN,
            new OpenMessageSubscriptionProcessor(
                messageStore, subscriptionStore, subscriptionCommandSender))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CORRELATE,
            new CorrelateMessageSubscriptionProcessor(subscriptionStore))
        .withStateResource(messageStore)
        .withStateResource(subscriptionStore)
        .withListener(this)
        .build();
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {

    final ActorControl actor = streamProcessor.getActor();
    final TypedStreamEnvironment env = streamProcessor.getEnvironment();
    final LogStream logStream = env.getStream();

    subscriptionCommandSender.init(topologyManager, actor, logStream);

    final MessageTimeToLiveChecker timeToLiveChecker =
        new MessageTimeToLiveChecker(env.buildCommandWriter(), messageStore);
    streamProcessor
        .getActor()
        .runAtFixedRate(MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL, timeToLiveChecker);

    final PendingMessageSubscriptionChecker pendingSubscriptionChecker =
        new PendingMessageSubscriptionChecker(
            subscriptionCommandSender, subscriptionStore, SUBSCRIPTION_TIMEOUT.toMillis());
    actor.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);
  }
}
