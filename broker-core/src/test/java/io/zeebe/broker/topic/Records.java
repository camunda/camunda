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

import io.zeebe.broker.clustering.orchestration.topic.TopicEvent;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.workflow.data.DeploymentEvent;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.util.ReflectUtil;
import io.zeebe.util.buffer.BufferUtil;

public class Records
{

    public static DeploymentEvent asDeploymentRecord(LoggedEvent event)
    {
        return readValueAs(event, DeploymentEvent.class);
    }

    public static TopicEvent asTopicRecord(LoggedEvent event)
    {
        return readValueAs(event, TopicEvent.class);
    }

    public static TaskEvent asTaskRecord(LoggedEvent event)
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

    public static boolean isDeploymentRecord(LoggedEvent event)
    {
        return isRecordOfType(event, ValueType.DEPLOYMENT);
    }

    public static boolean isPartitionRecord(LoggedEvent event)
    {
        return isRecordOfType(event, ValueType.PARTITION);
    }

    public static boolean isTopicRecord(LoggedEvent event)
    {
        return isRecordOfType(event, ValueType.TOPIC);
    }

    public static boolean isTaskRecord(LoggedEvent event)
    {
        return isRecordOfType(event, ValueType.TASK);
    }

    public static boolean isIncidentRecord(LoggedEvent event)
    {
        return isRecordOfType(event, ValueType.INCIDENT);
    }

    public static boolean hasIntent(LoggedEvent event, Intent intent)
    {
        if (event == null)
        {
            return false;
        }

        final RecordMetadata metadata = getMetadata(event);

        return metadata.getIntent() == intent;
    }

    private static RecordMetadata getMetadata(LoggedEvent event)
    {
        final RecordMetadata metadata = new RecordMetadata();
        event.readMetadata(metadata);

        return metadata;
    }

    public static boolean isRejection(LoggedEvent event)
    {
        final RecordMetadata metadata = getMetadata(event);
        return metadata.getRecordType() == RecordType.COMMAND_REJECTION;
    }

    public static boolean isRejection(LoggedEvent event, ValueType valueType, Intent intent)
    {
        return isRejection(event) && isRecordOfType(event, valueType) && hasIntent(event, intent);
    }

    public static boolean isEvent(LoggedEvent event)
    {
        final RecordMetadata metadata = getMetadata(event);
        return metadata.getRecordType() == RecordType.EVENT;
    }

    public static boolean isEvent(LoggedEvent event, ValueType valueType, Intent intent)
    {
        return isEvent(event) && isRecordOfType(event, valueType) && hasIntent(event, intent);
    }

    public static boolean isCommand(LoggedEvent event)
    {
        final RecordMetadata metadata = getMetadata(event);
        return metadata.getRecordType() == RecordType.COMMAND;
    }

    public static boolean isCommand(LoggedEvent event, ValueType valueType, Intent intent)
    {
        return isCommand(event) && isRecordOfType(event, valueType) && hasIntent(event, intent);
    }

    protected static boolean isRecordOfType(LoggedEvent event, ValueType type)
    {
        if (event == null)
        {
            return false;
        }

        final RecordMetadata metadata = getMetadata(event);

        return metadata.getValueType() == type;
    }
}
