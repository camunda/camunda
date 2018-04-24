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

import io.zeebe.client.impl.ControlMessageRequest;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.protocol.clientapi.ControlMessageType;

public class CreateJobSubscriptionCommandImpl extends ControlMessageRequest<JobSubscriptionImpl>
{
    protected JobSubscriptionImpl subscription;

    public CreateJobSubscriptionCommandImpl(RequestManager client, int partition)
    {
        super(client, ControlMessageType.ADD_JOB_SUBSCRIPTION, partition, JobSubscriptionImpl.class);
        this.subscription = new JobSubscriptionImpl();
        this.subscription.setPartitionId(partition);
    }

    public CreateJobSubscriptionCommandImpl(RequestManager client, String topic)
    {
        super(client, ControlMessageType.ADD_JOB_SUBSCRIPTION, topic, JobSubscriptionImpl.class);
        this.subscription = new JobSubscriptionImpl();
        this.subscription.setPartitionId(-1);
    }

    public CreateJobSubscriptionCommandImpl lockOwner(final String lockOwner)
    {
        this.subscription.setLockOwner(lockOwner);
        return this;
    }

    public CreateJobSubscriptionCommandImpl initialCredits(final int initialCredits)
    {
        this.subscription.setCredits(initialCredits);
        return this;
    }

    public CreateJobSubscriptionCommandImpl lockDuration(final long lockDuration)
    {
        this.subscription.setLockDuration(lockDuration);
        return this;
    }

    public CreateJobSubscriptionCommandImpl jobType(final String jobType)
    {
        this.subscription.setJobType(jobType);
        return this;
    }

    @Override
    public void setTargetPartition(int targetPartition)
    {
        super.setTargetPartition(targetPartition);
        subscription.setPartitionId(targetPartition);
    }

    @Override
    public void onResponse(JobSubscriptionImpl response)
    {
        response.setPartitionId(targetPartition);
    }

    @Override
    public Object getRequest()
    {
        return subscription;
    }

}
