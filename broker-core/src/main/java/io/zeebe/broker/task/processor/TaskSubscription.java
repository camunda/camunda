/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.task.processor;

import org.agrona.DirectBuffer;

import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.IntegerProperty;
import io.zeebe.broker.util.msgpack.property.LongProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;

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
