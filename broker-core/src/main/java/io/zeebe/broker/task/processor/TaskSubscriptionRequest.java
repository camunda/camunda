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
import io.zeebe.msgpack.property.*;
import org.agrona.DirectBuffer;

public class TaskSubscriptionRequest extends UnpackedObject
{
    protected LongProperty subscriberKeyProp = new LongProperty("subscriberKey", -1);

    protected StringProperty taskTypeProp = new StringProperty("taskType", "");

    protected LongProperty lockDurationProp = new LongProperty("lockDuration", -1);
    protected StringProperty lockOwnerProp = new StringProperty("lockOwner", "");

    protected IntegerProperty creditsProp = new IntegerProperty("credits", -1);

    public TaskSubscriptionRequest()
    {
        this.declareProperty(subscriberKeyProp)
            .declareProperty(taskTypeProp)
            .declareProperty(lockDurationProp)
            .declareProperty(lockOwnerProp)
            .declareProperty(creditsProp);
    }

    public TaskSubscriptionRequest setSubscriberKey(long subscriberKey)
    {
        this.subscriberKeyProp.setValue(subscriberKey);
        return this;
    }

    public TaskSubscriptionRequest setTaskType(DirectBuffer taskType)
    {
        this.taskTypeProp.setValue(taskType);
        return this;
    }

    public TaskSubscriptionRequest setLockDuration(long lockDuration)
    {
        this.lockDurationProp.setValue(lockDuration);
        return this;
    }

    public TaskSubscriptionRequest setCredits(int credits)
    {
        this.creditsProp.setValue(credits);
        return this;
    }

    public TaskSubscriptionRequest setLockOwner(DirectBuffer lockOwner)
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

    public DirectBuffer getLockOwner()
    {
        return lockOwnerProp.getValue();
    }

}
