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
package io.zeebe.broker.event.processor;

import org.agrona.DirectBuffer;

import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.IntegerProperty;
import io.zeebe.broker.util.msgpack.property.LongProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;

public class CloseSubscriptionRequest extends UnpackedObject
{

    protected StringProperty topicNameProp = new StringProperty("topicName");
    protected IntegerProperty partitionIdProp = new IntegerProperty("partitionId");
    protected LongProperty subscriberKeyProp = new LongProperty("subscriberKey");

    public CloseSubscriptionRequest()
    {
        this.declareProperty(subscriberKeyProp)
            .declareProperty(topicNameProp)
            .declareProperty(partitionIdProp);
    }

    public long getSubscriberKey()
    {
        return subscriberKeyProp.getValue();
    }

    public CloseSubscriptionRequest setSubscriberKey(long subscriberKey)
    {
        this.subscriberKeyProp.setValue(subscriberKey);
        return this;
    }

    public DirectBuffer getTopicName()
    {
        return topicNameProp.getValue();
    }

    public CloseSubscriptionRequest setTopicName(final DirectBuffer topicName)
    {
        this.topicNameProp.setValue(topicName);
        return this;
    }

    public int getPartitionId()
    {
        return partitionIdProp.getValue();
    }

    public CloseSubscriptionRequest setPartitionId(int partitionId)
    {
        this.partitionIdProp.setValue(partitionId);
        return this;
    }
}
