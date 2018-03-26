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
package io.zeebe.broker.topic;

import org.agrona.DirectBuffer;

import io.zeebe.broker.system.log.PartitionEvent;
import io.zeebe.broker.system.log.TopicEvent;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.workflow.data.DeploymentEvent;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.util.ReflectUtil;
import io.zeebe.util.buffer.BufferUtil;

public class Events
{

    public static DeploymentEvent asDeploymentEvent(LoggedEvent event)
    {
        return readValueAs(event, DeploymentEvent.class);
    }

    public static PartitionEvent asPartitionEvent(LoggedEvent event)
    {
        return readValueAs(event, PartitionEvent.class);
    }

    public static TopicEvent asTopicEvent(LoggedEvent event)
    {
        return readValueAs(event, TopicEvent.class);
    }

    public static TaskEvent asTaskEvent(LoggedEvent event)
    {
        return readValueAs(event, TaskEvent.class);
    }

    protected static <T extends UnpackedObject> T readValueAs(LoggedEvent event, Class<T> valueClass)
    {
        final DirectBuffer copy = BufferUtil.cloneBuffer(event.getValueBuffer(), event.getValueOffset(), event.getValueLength());
        final T valuePojo = ReflectUtil.newInstance(valueClass);
        valuePojo.wrap(copy);
        return valuePojo;
    }

    public static boolean isDeploymentEvent(LoggedEvent event)
    {
        return isEventOfType(event, EventType.DEPLOYMENT_EVENT);
    }

    public static boolean isPartitionEvent(LoggedEvent event)
    {
        return isEventOfType(event, EventType.PARTITION_EVENT);
    }

    public static boolean isTopicEvent(LoggedEvent event)
    {
        return isEventOfType(event, EventType.TOPIC_EVENT);
    }

    public static boolean isTaskEvent(LoggedEvent event)
    {
        return isEventOfType(event, EventType.TASK_EVENT);
    }

    public static boolean isIncidentEvent(LoggedEvent event)
    {
        return isEventOfType(event, EventType.INCIDENT_EVENT);
    }

    protected static boolean isEventOfType(LoggedEvent event, EventType type)
    {
        if (event == null)
        {
            return false;
        }

        final BrokerEventMetadata metadata = new BrokerEventMetadata();
        event.readMetadata(metadata);

        return metadata.getEventType() == type;
    }
}
