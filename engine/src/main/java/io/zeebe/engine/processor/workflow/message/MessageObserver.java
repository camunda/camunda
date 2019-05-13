/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.message;

import io.zeebe.engine.processor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processor.TypedStreamEnvironment;
import io.zeebe.engine.processor.TypedStreamProcessor;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.engine.state.message.MessageSubscriptionState;
import io.zeebe.util.sched.ActorControl;
import java.time.Duration;

public class MessageObserver implements StreamProcessorLifecycleAware {

  public static final Duration MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL = Duration.ofSeconds(60);

  public static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final SubscriptionCommandSender subscriptionCommandSender;
  private final MessageState messageState;
  private final MessageSubscriptionState subscriptionState;

  public MessageObserver(
      MessageState messageState,
      MessageSubscriptionState subscriptionState,
      SubscriptionCommandSender subscriptionCommandSender) {
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {

    final ActorControl actor = streamProcessor.getActor();
    final TypedStreamEnvironment env = streamProcessor.getEnvironment();

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
