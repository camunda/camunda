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
package io.zeebe.client.impl;

import io.zeebe.client.api.clients.SubscriptionClient;
import io.zeebe.client.api.subscription.JobSubscriptionBuilderStep1;
import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1;
import io.zeebe.client.impl.subscription.job.JobSubcriptionBuilder;
import io.zeebe.client.impl.subscription.topic.TopicSubscriptionBuilderImpl;

public class SubscriptionClientImpl implements SubscriptionClient
{
    private final TopicClientImpl client;

    public SubscriptionClientImpl(TopicClientImpl client)
    {
        this.client = client;
    }

    @Override
    public TopicSubscriptionBuilderStep1 newTopicSubscription()
    {
        return new TopicSubscriptionBuilderImpl(client);
    }

    @Override
    public JobSubscriptionBuilderStep1 newJobSubscription()
    {
        return new JobSubcriptionBuilder(client);
    }

}
