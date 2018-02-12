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

import java.util.concurrent.Future;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.task.TaskHandler;
import io.zeebe.util.EnsureUtil;

public class TaskSubscriberGroupBuilder
{
    public static final int DEFAULT_TASK_FETCH_SIZE = 32;

    protected String taskType;
    protected long lockTime = -1L;
    protected String lockOwner;
    protected TaskHandler taskHandler;
    protected int taskFetchSize = DEFAULT_TASK_FETCH_SIZE;

    protected final ZeebeClient client;
    protected final SubscriptionManager taskAcquisition;
    protected final String topic;

    public TaskSubscriberGroupBuilder(
            ZeebeClient client,
            String topic,
            SubscriptionManager taskAcquisition)
    {
        this.topic = topic;
        this.client = client;
        this.taskAcquisition = taskAcquisition;
    }

    public TaskSubscriberGroupBuilder taskType(String taskType)
    {
        this.taskType = taskType;
        return this;
    }

    public TaskSubscriberGroupBuilder lockTime(long lockTime)
    {
        this.lockTime = lockTime;
        return this;
    }

    public TaskSubscriberGroupBuilder lockOwner(String lockOwner)
    {
        this.lockOwner = lockOwner;
        return this;
    }

    public TaskSubscriberGroupBuilder taskHandler(TaskHandler taskHandler)
    {
        this.taskHandler = taskHandler;
        return this;
    }

    public TaskSubscriberGroupBuilder taskFetchSize(int taskFetchSize)
    {
        this.taskFetchSize = taskFetchSize;
        return this;
    }

    public Future<TaskSubscriberGroup> build()
    {
        EnsureUtil.ensureNotNullOrEmpty("taskType", taskType);
        EnsureUtil.ensureGreaterThan("lockTime", lockTime, 0L);
        EnsureUtil.ensureNotNullOrEmpty("lockOwner", lockOwner);
        EnsureUtil.ensureGreaterThan("taskFetchSize", taskFetchSize, 0);

        final TaskSubscriptionSpec subscription =
                new TaskSubscriptionSpec(topic, taskHandler, taskType, lockTime, lockOwner, taskFetchSize);

        return taskAcquisition.openTaskSubscription(subscription);
    }
}
