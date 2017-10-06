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

import java.time.Duration;

import io.zeebe.client.clustering.impl.ClientTopologyManager;
import io.zeebe.client.impl.TasksClientImpl;
import io.zeebe.client.impl.data.MsgPackMapper;
import io.zeebe.client.task.PollableTaskSubscription;
import io.zeebe.client.task.PollableTaskSubscriptionBuilder;
import io.zeebe.util.EnsureUtil;

public class PollableTaskSubscriptionBuilderImpl implements PollableTaskSubscriptionBuilder
{

    protected int taskFetchSize = TaskSubscriptionBuilderImpl.DEFAULT_TASK_FETCH_SIZE;
    protected String taskType;
    protected long lockTime = Duration.ofMinutes(1).toMillis();
    protected String lockOwner;

    protected final String topic;
    protected int partition;
    protected final TasksClientImpl taskClient;
    protected final ClientTopologyManager topologyManager;
    protected final EventAcquisition<TaskSubscriptionImpl> taskAcquisition;
    protected final MsgPackMapper msgPackMapper;

    public PollableTaskSubscriptionBuilderImpl(
            TasksClientImpl taskClient,
            ClientTopologyManager topologyManager,
            String topic,
            EventAcquisition<TaskSubscriptionImpl> taskAcquisition,
            MsgPackMapper msgPackMapper)
    {
        this.topic = topic;
        this.partition = -1;
        this.taskClient = taskClient;
        this.taskAcquisition = taskAcquisition;
        this.msgPackMapper = msgPackMapper;
        this.topologyManager = topologyManager;
    }

    @Override
    public PollableTaskSubscriptionBuilder partitionId(int partition)
    {
        this.partition = partition;
        return this;
    }

    @Override
    public PollableTaskSubscriptionBuilder taskType(String taskType)
    {
        this.taskType = taskType;
        return this;
    }

    @Override
    public PollableTaskSubscriptionBuilder lockTime(long lockDuration)
    {
        this.lockTime = lockDuration;
        return this;
    }

    @Override
    public PollableTaskSubscriptionBuilder lockTime(Duration lockDuration)
    {
        return lockTime(lockDuration.toMillis());
    }

    @Override
    public PollableTaskSubscriptionBuilder lockOwner(String lockOwner)
    {
        this.lockOwner = lockOwner;
        return this;
    }

    @Override
    public PollableTaskSubscriptionBuilderImpl taskFetchSize(int numTasks)
    {
        this.taskFetchSize = numTasks;
        return this;
    }

    @Override
    public PollableTaskSubscription open()
    {
        EnsureUtil.ensureNotNullOrEmpty("taskType", taskType);
        EnsureUtil.ensureGreaterThan("lockTime", lockTime, 0L);
        EnsureUtil.ensureNotNullOrEmpty("lockOwner", lockOwner);
        EnsureUtil.ensureGreaterThan("taskFetchSize", taskFetchSize, 0);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(
                taskClient,
                topic,
                partition,
                null,
                taskType,
                lockTime,
                lockOwner,
                taskFetchSize,
                msgPackMapper,
                taskAcquisition);

        taskAcquisition.registerSubscriptionAsync(subscription);

        subscription.open();

        return subscription;
    }
}
