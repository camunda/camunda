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
import io.zeebe.client.task.TaskHandler;
import io.zeebe.client.task.TaskSubscriptionBuilder;
import io.zeebe.util.EnsureUtil;

public class TaskSubscriptionBuilderImpl implements TaskSubscriptionBuilder
{
    public static final int DEFAULT_TASK_FETCH_SIZE = 32;

    protected String taskType;
    protected long lockTime = -1L;
    protected String lockOwner;
    protected TaskHandler taskHandler;
    protected int taskFetchSize = DEFAULT_TASK_FETCH_SIZE;

    protected final TasksClientImpl client;
    protected final EventAcquisition<TaskSubscriptionImpl> taskAcquisition;
    protected final MsgPackMapper msgPackMapper;
    protected final String topic;
    protected final ClientTopologyManager topologyManager;
    protected int partition;

    public TaskSubscriptionBuilderImpl(
            TasksClientImpl client,
            ClientTopologyManager topologyManager,
            String topic,
            EventAcquisition<TaskSubscriptionImpl> taskAcquisition,
            MsgPackMapper msgPackMapper)
    {
        this.client = client;
        this.topic = topic;
        this.partition = -1;
        this.taskAcquisition = taskAcquisition;
        this.msgPackMapper = msgPackMapper;
        this.topologyManager = topologyManager;
    }

    @Override
    public TaskSubscriptionBuilder partitionId(int partition)
    {
        this.partition = partition;
        return this;
    }

    @Override
    public TaskSubscriptionBuilder taskType(String taskType)
    {
        this.taskType = taskType;
        return this;
    }

    @Override
    public TaskSubscriptionBuilder lockTime(long lockDuration)
    {
        this.lockTime = lockDuration;
        return this;
    }

    @Override
    public TaskSubscriptionBuilder lockTime(Duration lockDuration)
    {
        return lockTime(lockDuration.toMillis());
    }

    @Override
    public TaskSubscriptionBuilder handler(TaskHandler handler)
    {
        this.taskHandler = handler;
        return this;
    }

    @Override
    public TaskSubscriptionBuilder taskFetchSize(int numTasks)
    {
        this.taskFetchSize = numTasks;
        return this;
    }

    @Override
    public TaskSubscriptionBuilder lockOwner(String lockOwner)
    {
        this.lockOwner = lockOwner;
        return this;
    }

    @Override
    public TaskSubscriptionImpl open()
    {
        EnsureUtil.ensureNotNull("taskHandler", taskHandler);
        EnsureUtil.ensureNotNullOrEmpty("lockOwner", lockOwner);
        EnsureUtil.ensureNotNullOrEmpty("taskType", taskType);
        EnsureUtil.ensureGreaterThan("lockTime", lockTime, 0L);
        EnsureUtil.ensureGreaterThan("taskFetchSize", taskFetchSize, 0);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(
                client,
                topic,
                partition,
                taskHandler,
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
