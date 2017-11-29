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
package io.zeebe.client.event.impl;

import java.util.concurrent.atomic.AtomicBoolean;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.PollableTopicSubscription;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.client.event.UniversalEventHandler;
import io.zeebe.client.task.impl.subscription.EventAcquisition;
import io.zeebe.client.task.impl.subscription.EventSubscriberGroup;
import io.zeebe.util.CheckedConsumer;

public class TopicSubscriberGroup extends EventSubscriberGroup<TopicSubscriber>
    implements TopicSubscription, PollableTopicSubscription
{

    protected static final int MAX_HANDLING_RETRIES = 2;

    protected AtomicBoolean processingFlag = new AtomicBoolean(false);
    protected final TopicSubscriptionSpec subscription;

    public TopicSubscriberGroup(
            ZeebeClient client,
            EventAcquisition acquisition,
            TopicSubscriptionSpec subscription)
    {
        super(acquisition, client, subscription.getTopic());
        this.subscription = subscription;
    }

    @Override
    public int poll()
    {
        return pollEvents(subscription.getHandler());
    }

    @Override
    public int poll(UniversalEventHandler taskHandler)
    {
        return pollEvents((e) -> taskHandler.handle(e));
    }

    @Override
    public int pollEvents(CheckedConsumer<GeneralEventImpl> pollHandler)
    {

        // ensuring at most one thread polls at a time which is the guarantee we give for
        // topic subscriptions
        if (processingFlag.compareAndSet(false, true))
        {
            try
            {
                return super.pollEvents(pollHandler);
            }
            finally
            {
                processingFlag.set(false);
            }
        }
        else
        {
            return 0;
        }
    }

    @Override
    public boolean isManagedGroup()
    {
        return subscription.isManaged();
    }

    @Override
    protected TopicSubscriber buildSubscriber(int partition)
    {
        return new TopicSubscriber((TopicClientImpl) client.topics(), subscription, partition, acquisition);
    }

    @Override
    protected String describeGroupSpec()
    {
        return subscription.toString();
    }
}
