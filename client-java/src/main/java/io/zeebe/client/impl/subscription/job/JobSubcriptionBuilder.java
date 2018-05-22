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
package io.zeebe.client.impl.subscription.job;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.api.subscription.*;
import io.zeebe.client.api.subscription.JobSubscriptionBuilderStep1.JobSubscriptionBuilderStep2;
import io.zeebe.client.api.subscription.JobSubscriptionBuilderStep1.JobSubscriptionBuilderStep3;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.TopicClientImpl;
import io.zeebe.util.EnsureUtil;

public class JobSubcriptionBuilder implements JobSubscriptionBuilderStep1, JobSubscriptionBuilderStep2, JobSubscriptionBuilderStep3
{
    private final JobSubscriberGroupBuilder subscriberBuilder;

    public JobSubcriptionBuilder(TopicClientImpl client)
    {
        this.subscriberBuilder = new JobSubscriberGroupBuilder(client.getTopic(), client.getSubscriptionManager());

        // apply defaults from configuration
        final ZeebeClientConfiguration configuration = client.getConfiguration();
        this.subscriberBuilder.worker(configuration.getDefaultJobWorkerName());
        this.subscriberBuilder.timeout(configuration.getDefaultJobTimeout().toMillis());
        this.subscriberBuilder.bufferSize(configuration.getDefaultJobSubscriptionBufferSize());
    }

    @Override
    public JobSubscriptionBuilderStep2 jobType(String type)
    {
        subscriberBuilder.jobType(type);
        return this;
    }

    @Override
    public JobSubscriptionBuilderStep3 timeout(long lockTime)
    {
        subscriberBuilder.timeout(lockTime);
        return this;
    }

    @Override
    public JobSubscriptionBuilderStep3 timeout(Duration lockTime)
    {
        subscriberBuilder.timeout(lockTime.toMillis());
        return this;
    }

    @Override
    public JobSubscriptionBuilderStep3 name(String lockOwner)
    {
        subscriberBuilder.worker(lockOwner);
        return this;
    }

    @Override
    public JobSubscriptionBuilderStep3 bufferSize(int fetchSize)
    {
        subscriberBuilder.bufferSize(fetchSize);
        return this;
    }

    @Override
    public JobSubscriptionBuilderStep3 handler(JobHandler handler)
    {
        EnsureUtil.ensureNotNull("handler", handler);
        subscriberBuilder.jobHandler(handler);
        return this;
    }

    @Override
    public JobSubscription open()
    {
        final Future<JobSubscriberGroup> subscriberGroup = subscriberBuilder.build();

        try
        {
            return subscriberGroup.get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            throw new ClientException("Could not open subscription", e);
        }
    }

}
