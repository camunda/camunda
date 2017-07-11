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
package io.zeebe.client.task.impl.subscription;

import static io.zeebe.util.VarDataUtil.readBytes;

import org.agrona.DirectBuffer;
import org.slf4j.Logger;

import io.zeebe.client.event.impl.EventTypeMapping;
import io.zeebe.client.event.impl.TopicEventImpl;
import io.zeebe.client.impl.Loggers;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.SubscribedEventDecoder;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.util.actor.Actor;

public class SubscribedEventCollector implements Actor, FragmentHandler
{
    protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;
    protected static final String NAME = "event-collector";

    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final SubscribedEventDecoder subscribedEventDecoder = new SubscribedEventDecoder();

    protected final Subscription receiveBufferSubscription;

    protected final SubscribedEventHandler taskSubscriptionHandler;
    protected final SubscribedEventHandler topicSubscriptionHandler;

    public SubscribedEventCollector(
            Subscription receiveBufferSubscription,
            SubscribedEventHandler taskSubscriptionHandler,
            SubscribedEventHandler topicSubscriptionHandler)
    {
        this.receiveBufferSubscription = receiveBufferSubscription;
        this.taskSubscriptionHandler = taskSubscriptionHandler;
        this.topicSubscriptionHandler = topicSubscriptionHandler;
    }

    @Override
    public int doWork()
    {
        return receiveBufferSubscription.peekAndConsume(this, Integer.MAX_VALUE);
    }

    @Override
    public String name()
    {
        return NAME;
    }


    @Override
    public int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed)
    {
        messageHeaderDecoder.wrap(buffer, offset);

        offset += MessageHeaderDecoder.ENCODED_LENGTH;

        final int templateId = messageHeaderDecoder.templateId();

        final boolean messageHandled;

        if (templateId == SubscribedEventDecoder.TEMPLATE_ID)
        {
            subscribedEventDecoder.wrap(buffer, offset, messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());

            final SubscriptionType subscriptionType = subscribedEventDecoder.subscriptionType();
            final SubscribedEventHandler eventHandler = getHandlerForEvent(subscriptionType);

            if (eventHandler != null)
            {
                final long key = subscribedEventDecoder.key();
                final long subscriberKey = subscribedEventDecoder.subscriberKey();
                final long position = subscribedEventDecoder.position();
                final int partitionId = subscribedEventDecoder.partitionId();
                final String topicName = subscribedEventDecoder.topicName();
                final byte[] eventBuffer = readBytes(subscribedEventDecoder::getEvent, subscribedEventDecoder::eventLength);

                final TopicEventImpl event = new TopicEventImpl(
                        topicName,
                        partitionId,
                        key,
                        position,
                        EventTypeMapping.mapEventType(subscribedEventDecoder.eventType()),
                        eventBuffer);

                messageHandled = eventHandler.onEvent(subscriberKey, event);
            }
            else
            {
                LOGGER.info("Ignoring event for unknown subscription type " + subscriptionType.toString());
                messageHandled = true;
            }
        }
        else
        {
            // ignoring
            messageHandled = true;
        }


        return messageHandled ? FragmentHandler.CONSUME_FRAGMENT_RESULT : FragmentHandler.POSTPONE_FRAGMENT_RESULT;
    }

    protected SubscribedEventHandler getHandlerForEvent(SubscriptionType subscriptionType)
    {
        if (subscriptionType == SubscriptionType.TASK_SUBSCRIPTION)
        {
            return taskSubscriptionHandler;
        }
        else if (subscriptionType == SubscriptionType.TOPIC_SUBSCRIPTION)
        {
            return topicSubscriptionHandler;
        }
        else
        {
            return null;
        }
    }

}
