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
package io.zeebe.client.task.impl;

import io.zeebe.client.impl.RequestManager;
import io.zeebe.protocol.clientapi.ControlMessageType;

public class IncreaseTaskSubscriptionCreditsCmdImpl extends ControlMessageRequest<Void>
{
    protected final TaskSubscription subscription;

    public IncreaseTaskSubscriptionCreditsCmdImpl(final RequestManager commandManager, int partition)
    {
        super(commandManager, ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS, partition, Void.class);
        this.subscription = new TaskSubscription();
    }

    public IncreaseTaskSubscriptionCreditsCmdImpl subscriberKey(long subscriberKey)
    {
        this.subscription.setSubscriberKey(subscriberKey);
        return this;
    }

    public IncreaseTaskSubscriptionCreditsCmdImpl credits(final int credits)
    {
        this.subscription.setCredits(credits);
        return this;
    }

    @Override
    public Object getRequest()
    {
        return subscription;
    }


}
