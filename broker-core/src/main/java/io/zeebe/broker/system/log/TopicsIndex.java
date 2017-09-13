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
package io.zeebe.broker.system.log;

import java.nio.ByteOrder;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.map.Bytes2BytesZbMap;

/**
 * Maps
 *
 * topic name => (remaining partitions, request position)
 *
 * where remaining partitions is the number of partitions that are still pending to be created.
 */
public class TopicsIndex
{

    protected static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
    public static final int MAX_TOPIC_NAME_LENGTH = LogStream.MAX_TOPIC_NAME_LENGTH; // TODO: could be configurable

    protected static final int REMAINING_PARTITIONS_OFFSET = 0;
    protected static final int REQUEST_OFFSET = BitUtil.SIZE_OF_INT;

    protected static final int VALUE_LENGTH = REQUEST_OFFSET + BitUtil.SIZE_OF_LONG;

    protected final Bytes2BytesZbMap topics;

    protected MutableDirectBuffer value = new UnsafeBuffer(new byte[VALUE_LENGTH]);

    public TopicsIndex()
    {
        this.topics = new Bytes2BytesZbMap(MAX_TOPIC_NAME_LENGTH, VALUE_LENGTH);
    }

    public void close()
    {
        topics.close();
    }

    public Bytes2BytesZbMap getRawMap()
    {
        return topics;
    }

    public boolean moveTo(DirectBuffer topicName)
    {
        return topics.get(topicName, value);
    }

    public int getRemainingPartitions()
    {
        return value.getInt(REMAINING_PARTITIONS_OFFSET, BYTE_ORDER);
    }

    public void put(DirectBuffer topicName, int remainingPartitions, long requestPosition)
    {
        value.putInt(REMAINING_PARTITIONS_OFFSET, remainingPartitions, BYTE_ORDER);
        value.putLong(REQUEST_OFFSET, requestPosition, BYTE_ORDER);

        topics.put(topicName, value);
    }

    public void putRemainingPartitions(DirectBuffer topicName, int partitions)
    {
        value.putInt(REMAINING_PARTITIONS_OFFSET, partitions, BYTE_ORDER);

        topics.put(topicName, value);
    }

    public long getRequestPosition()
    {
        return value.getLong(REQUEST_OFFSET, BYTE_ORDER);
    }

}
