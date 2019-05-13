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

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.TypedEventStreamProcessorBuilder;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.EventScopeInstanceState;
import io.zeebe.engine.state.message.MessageStartEventSubscriptionState;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.engine.state.message.MessageSubscriptionState;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.protocol.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;

public class MessageEventProcessors {

  public static void addMessageProcessors(
      TypedEventStreamProcessorBuilder typedProcessorBuilder,
      ZeebeState zeebeState,
      SubscriptionCommandSender subscriptionCommandSender) {

    final MessageState messageState = zeebeState.getMessageState();
    final MessageSubscriptionState subscriptionState = zeebeState.getMessageSubscriptionState();
    final MessageStartEventSubscriptionState startEventSubscriptionState =
        zeebeState.getMessageStartEventSubscriptionState();
    final EventScopeInstanceState eventScopeInstanceState =
        zeebeState.getWorkflowState().getEventScopeInstanceState();
    final KeyGenerator keyGenerator = zeebeState.getKeyGenerator();

    typedProcessorBuilder
        .onCommand(
            ValueType.MESSAGE,
            MessageIntent.PUBLISH,
            new PublishMessageProcessor(
                messageState,
                subscriptionState,
                startEventSubscriptionState,
                eventScopeInstanceState,
                subscriptionCommandSender,
                keyGenerator))
        .onCommand(
            ValueType.MESSAGE, MessageIntent.DELETE, new DeleteMessageProcessor(messageState))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.OPEN,
            new OpenMessageSubscriptionProcessor(
                messageState, subscriptionState, subscriptionCommandSender))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CORRELATE,
            new CorrelateMessageSubscriptionProcessor(
                messageState, subscriptionState, subscriptionCommandSender))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CLOSE,
            new CloseMessageSubscriptionProcessor(subscriptionState, subscriptionCommandSender))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.REJECT,
            new RejectMessageCorrelationProcessor(
                messageState, subscriptionState, subscriptionCommandSender))
        .onCommand(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            MessageStartEventSubscriptionIntent.OPEN,
            new OpenMessageStartEventSubscriptionProcessor(
                startEventSubscriptionState, zeebeState.getWorkflowState()))
        .onCommand(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            MessageStartEventSubscriptionIntent.CLOSE,
            new CloseMessageStartEventSubscriptionProcessor(startEventSubscriptionState))
        .withListener(
            new MessageObserver(messageState, subscriptionState, subscriptionCommandSender));
  }
}
