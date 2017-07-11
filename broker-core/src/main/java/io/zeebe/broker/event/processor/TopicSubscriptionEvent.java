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
import io.zeebe.broker.util.msgpack.property.EnumProperty;
import io.zeebe.broker.util.msgpack.property.LongProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;

public class TopicSubscriptionEvent extends UnpackedObject
{
    protected StringProperty nameProp = new StringProperty("name");
    protected EnumProperty<TopicSubscriptionEventType> eventProp = new EnumProperty<>("eventType", TopicSubscriptionEventType.class);
    protected LongProperty ackPositionProp = new LongProperty("ackPosition");

    public TopicSubscriptionEvent()
    {
        declareProperty(nameProp)
            .declareProperty(eventProp)
            .declareProperty(ackPositionProp);
    }

    public TopicSubscriptionEvent setName(DirectBuffer nameBuffer, int offset, int length)
    {
        this.nameProp.setValue(nameBuffer, offset, length);
        return this;
    }

    public DirectBuffer getName()
    {
        return nameProp.getValue();
    }

    public long getAckPosition()
    {
        return ackPositionProp.getValue();
    }

    public TopicSubscriptionEvent setAckPosition(long ackPosition)
    {
        this.ackPositionProp.setValue(ackPosition);
        return this;
    }

    public TopicSubscriptionEventType getEventType()
    {
        return eventProp.getValue();
    }

    public TopicSubscriptionEvent setEventType(TopicSubscriptionEventType event)
    {
        this.eventProp.setValue(event);
        return this;
    }

}
