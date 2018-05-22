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

import java.util.concurrent.Future;

import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.client.impl.subscription.SubscriptionManager;
import io.zeebe.util.EnsureUtil;

public class JobSubscriberGroupBuilder
{
    protected String jobType;
    protected long timeout = -1L;
    protected String worker;
    protected JobHandler jobHandler;
    protected int bufferSize;

    protected final SubscriptionManager jobAcquisition;
    protected final String topic;

    public JobSubscriberGroupBuilder(
            String topic,
            SubscriptionManager taskAcquisition)
    {
        this.topic = topic;
        this.jobAcquisition = taskAcquisition;
    }

    public JobSubscriberGroupBuilder jobType(String taskType)
    {
        this.jobType = taskType;
        return this;
    }

    public JobSubscriberGroupBuilder timeout(long timeout)
    {
        this.timeout = timeout;
        return this;
    }

    public JobSubscriberGroupBuilder worker(String worker)
    {
        this.worker = worker;
        return this;
    }

    public JobSubscriberGroupBuilder jobHandler(JobHandler jobHandler)
    {
        this.jobHandler = jobHandler;
        return this;
    }

    public JobSubscriberGroupBuilder bufferSize(int bufferSize)
    {
        this.bufferSize = bufferSize;
        return this;
    }

    public Future<JobSubscriberGroup> build()
    {
        EnsureUtil.ensureNotNullOrEmpty("jobType", jobType);
        EnsureUtil.ensureGreaterThan("timeout", timeout, 0L);
        EnsureUtil.ensureNotNullOrEmpty("worker", worker);
        EnsureUtil.ensureGreaterThan("jobFetchSize", bufferSize, 0);

        final JobSubscriptionSpec subscription =
                new JobSubscriptionSpec(topic, jobHandler, jobType, timeout, worker, bufferSize);

        return jobAcquisition.openJobSubscription(subscription);
    }
}
