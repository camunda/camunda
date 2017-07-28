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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.zeebe.client.impl.cmd.ReceiverAwareResponseResult;
import io.zeebe.client.task.impl.subscription.EventSubscriptionCreationResult;
import io.zeebe.transport.RemoteAddress;

public class TaskSubscription implements EventSubscriptionCreationResult, ReceiverAwareResponseResult
{
    private final String topicName;
    private final int partitionId;

    private long subscriberKey;

    private String taskType;

    private long lockDuration;
    private String lockOwner;
    private int credits;

    protected RemoteAddress receiver;

    @JsonCreator
    public TaskSubscription(@JsonProperty("topicName") String topicName, @JsonProperty("partitionId") int partitionId)
    {
        this.topicName = topicName;
        this.partitionId = partitionId;
    }

    public TaskSubscription(TaskSubscription other)
    {
        this.subscriberKey = other.subscriberKey;
        this.topicName = other.topicName;
        this.partitionId = other.partitionId;
        this.taskType = other.taskType;
        this.lockDuration = other.lockDuration;
        this.lockOwner = other.lockOwner;
        this.credits = other.credits;
        this.receiver = other.receiver;
    }

    public long getSubscriberKey()
    {
        return subscriberKey;
    }

    public void setSubscriberKey(final long subscriberKey)
    {
        this.subscriberKey = subscriberKey;
    }

    public String getTopicName()
    {
        return topicName;
    }

    public int getPartitionId()
    {
        return partitionId;
    }

    public String getTaskType()
    {
        return taskType;
    }

    public void setTaskType(final String taskType)
    {
        this.taskType = taskType;
    }

    public long getLockDuration()
    {
        return lockDuration;
    }

    public void setLockDuration(final long lockDuration)
    {
        this.lockDuration = lockDuration;
    }

    public int getCredits()
    {
        return credits;
    }

    public void setCredits(final int credits)
    {
        this.credits = credits;
    }

    public String getLockOwner()
    {
        return lockOwner;
    }

    public void setLockOwner(final String lockOwner)
    {
        this.lockOwner = lockOwner;
    }

    @Override
    public void setReceiver(RemoteAddress receiver)
    {
        this.receiver = receiver;
    }

    @Override
    public RemoteAddress getEventPublisher()
    {
        return receiver;
    }

}
