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
package io.zeebe.broker.system.deployment.data;

import static org.agrona.BitUtil.*;

import java.nio.ByteOrder;
import java.util.Iterator;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.map.Long2BytesZbMap;
import io.zeebe.map.iterator.Long2BytesZbMapEntry;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * partition-id => (state, topicName)
 */
public class TopicPartitions
{
    public static final short STATE_CREATING = 0;
    public static final short STATE_CREATED = 1;

    private static final int VALUE_LENGTH = SIZE_OF_SHORT + SIZE_OF_INT + LogStream.MAX_TOPIC_NAME_LENGTH;

    private static final int STATE_OFFSET = 0;
    private static final int TOPIC_NAME_LENGTH_OFFSET = STATE_OFFSET + SIZE_OF_LONG;
    private static final int TOPIC_NAME_OFFSET = TOPIC_NAME_LENGTH_OFFSET + SIZE_OF_INT;

    private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[VALUE_LENGTH]);
    private final UnsafeBuffer topicNameBuffer = new UnsafeBuffer(0, 0);

    private final TopicPartition topicPartition = new TopicPartition();
    private final TopicPartitionIterator iterator = new TopicPartitionIterator();

    private final Long2BytesZbMap map = new Long2BytesZbMap(VALUE_LENGTH);

    public Long2BytesZbMap getRawMap()
    {
        return map;
    }

    public TopicPartition get(int partitionId)
    {
        final DirectBuffer currentValue = map.get(partitionId);

        if (currentValue != null)
        {
            topicPartition.wrap(partitionId, currentValue);
            return topicPartition;
        }
        else
        {
            return null;
        }
    }

    public void put(int partitionId, DirectBuffer topicName, short state)
    {
        buffer.putShort(STATE_OFFSET, state, BYTE_ORDER);

        final int topicNameLength = topicName.capacity();
        buffer.putInt(TOPIC_NAME_LENGTH_OFFSET, topicNameLength, BYTE_ORDER);
        buffer.putBytes(TOPIC_NAME_OFFSET, topicName, 0, topicNameLength);

        map.put(partitionId, buffer);
    }

    public TopicPartitionIterator iterator()
    {
        iterator.reset();
        return iterator;
    }

    public class TopicPartitionIterator implements Iterator<TopicPartition>
    {
        private Iterator<Long2BytesZbMapEntry> iterator;
        private TopicPartition topicPartition = new TopicPartition();

        public void reset()
        {
            iterator = map.iterator();
        }

        @Override
        public boolean hasNext()
        {
            return iterator.hasNext();
        }

        @Override
        public TopicPartition next()
        {
            final Long2BytesZbMapEntry entry = iterator.next();
            topicPartition.wrap((int) entry.getKey(), entry.getValue());

            return topicPartition;
        }
    }

    public class TopicPartition
    {
        private int partitionId;
        private DirectBuffer currentValue;

        public void wrap(int partitionId, DirectBuffer currentValue)
        {
            this.partitionId = partitionId;
            this.currentValue = currentValue;
        }

        public int getPartitionId()
        {
            return partitionId;
        }

        public short getState()
        {
            return currentValue.getShort(STATE_OFFSET, BYTE_ORDER);
        }

        public DirectBuffer getTopicName()
        {
            final int length = currentValue.getInt(TOPIC_NAME_LENGTH_OFFSET, BYTE_ORDER);
            topicNameBuffer.wrap(currentValue, TOPIC_NAME_OFFSET, length);

            return topicNameBuffer;
        }

    }

}
