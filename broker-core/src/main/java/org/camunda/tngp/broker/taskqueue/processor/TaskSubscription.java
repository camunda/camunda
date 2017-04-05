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
package org.camunda.tngp.broker.taskqueue.processor;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.IntegerProperty;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;
import org.camunda.tngp.broker.util.msgpack.property.StringProperty;

public class TaskSubscription extends UnpackedObject
{
    protected LongProperty subscriberKeyProp = new LongProperty("subscriberKey", -1);

    protected LongProperty topicIdProp = new LongProperty("topicId");
    protected StringProperty taskTypeProp = new StringProperty("taskType", "");

    protected LongProperty lockDurationProp = new LongProperty("lockDuration", -1);
    protected IntegerProperty lockOwnerProp = new IntegerProperty("lockOwner", -1);

    protected IntegerProperty creditsProp = new IntegerProperty("credits", -1);

    // transient field
    protected int channelId = -1;

    public TaskSubscription()
    {
        this.declareProperty(subscriberKeyProp)
            .declareProperty(topicIdProp)
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

    public TaskSubscription setChannelId(int channelId)
    {
        this.channelId =  channelId;
        return this;
    }

    public TaskSubscription setTopicId(long topicId)
    {
        this.topicIdProp.setValue(topicId);
        return this;
    }

    public TaskSubscription setTaskType(DirectBuffer taskType)
    {
        this.taskTypeProp.setValue(taskType, 0, taskType.capacity());
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

    public TaskSubscription setLockOwner(int lockOwner)
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

    public int getChannelId()
    {
        return channelId;
    }

    public long getTopicId()
    {
        return topicIdProp.getValue();
    }

    public int getLockOwner()
    {
        return lockOwnerProp.getValue();
    }

}
