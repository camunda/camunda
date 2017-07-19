/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.task.processor;

import io.zeebe.msgpack.UnpackedObject;
import org.agrona.DirectBuffer;

import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;

public class TaskSubscription extends UnpackedObject
{
    public static final int LOCK_OWNER_MAX_LENGTH = 64;

    protected LongProperty subscriberKeyProp = new LongProperty("subscriberKey", -1);

    protected StringProperty topicNameProp = new StringProperty("topicName");
    protected IntegerProperty partitionIdProp = new IntegerProperty("partitionId");
    protected StringProperty taskTypeProp = new StringProperty("taskType", "");

    protected LongProperty lockDurationProp = new LongProperty("lockDuration", -1);
    protected StringProperty lockOwnerProp = new StringProperty("lockOwner", "default");

    protected IntegerProperty creditsProp = new IntegerProperty("credits", -1);

    // transient field
    protected int streamId = -1;

    public TaskSubscription()
    {
        this.declareProperty(subscriberKeyProp)
            .declareProperty(topicNameProp)
            .declareProperty(partitionIdProp)
            .declareProperty(taskTypeProp)
            .declareProperty(lockDurationProp)
            .declareProperty(lockOwnerProp)
            .declareProperty(creditsProp);
    }

    public TaskSubscription setSubscriberKey(long subscriberKey)
    {
        this.subscriberKeyProp.setValue(subscriberKey);
        return this;
    }

    public TaskSubscription setStreamId(int streamId)
    {
        this.streamId =  streamId;
        return this;
    }

    public TaskSubscription setTopicName(final DirectBuffer topicName)
    {
        this.topicNameProp.setValue(topicName);
        return this;
    }

    public TaskSubscription setPartitionId(final int partitionId)
    {
        this.partitionIdProp.setValue(partitionId);
        return this;
    }

    public TaskSubscription setTaskType(DirectBuffer taskType)
    {
        this.taskTypeProp.setValue(taskType);
        return this;
    }

    public TaskSubscription setLockDuration(long lockDuration)
    {
        this.lockDurationProp.setValue(lockDuration);
        return this;
    }

    public TaskSubscription setCredits(int credits)
    {
        this.creditsProp.setValue(credits);
        return this;
    }

    public TaskSubscription setLockOwner(DirectBuffer lockOwner)
    {
        this.lockOwnerProp.setValue(lockOwner);
        return this;
    }

    public long getSubscriberKey()
    {
        return subscriberKeyProp.getValue();
    }

    public DirectBuffer getLockTaskType()
    {
        return taskTypeProp.getValue();
    }

    public long getLockDuration()
    {
        return lockDurationProp.getValue();
    }

    public int getCredits()
    {
        return creditsProp.getValue();
    }

    public int getStreamId()
    {
        return streamId;
    }

    public DirectBuffer getTopicName()
    {
        return topicNameProp.getValue();
    }

    public int getPartitionId()
    {
        return partitionIdProp.getValue();
    }

    public DirectBuffer getLockOwner()
    {
        return lockOwnerProp.getValue();
    }

}
