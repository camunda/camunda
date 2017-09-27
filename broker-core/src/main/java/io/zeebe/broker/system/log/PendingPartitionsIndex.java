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
import java.util.Iterator;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.system.log.PendingPartitionsIndex.PendingPartition;
import io.zeebe.map.Bytes2BytesZbMap;
import io.zeebe.map.iterator.Bytes2BytesZbMapEntry;

/**
 * Maps
 *
 * (topic name, partition id) => (partition key, creationExpiration)
 *
 * Entries are only contained while a partition is pending to be created.
 */
public class PendingPartitionsIndex implements Iterable<PendingPartition>
{

    protected static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
    protected static final int KEY_LENGTH = TopicsIndex.MAX_TOPIC_NAME_LENGTH + BitUtil.SIZE_OF_INT;
    protected static final int KEY_PARTITION_ID_OFFSET = 0;
    protected static final int KEY_TOPIC_NAME_LENGTH_OFFSET = BitUtil.SIZE_OF_INT;
    protected static final int KEY_TOPIC_NAME_OFFSET = KEY_TOPIC_NAME_LENGTH_OFFSET + BitUtil.SIZE_OF_INT;

    protected static final int VALUE_LENGTH = 2 * BitUtil.SIZE_OF_LONG;
    protected static final int VALUE_KEY_OFFSET = 0;
    protected static final int VALUE_TIME_OFFSET = BitUtil.SIZE_OF_LONG;

    protected final Bytes2BytesZbMap pendingPartitions;

    protected PendingPartitionImpl partition = new PendingPartitionImpl();

    protected final MutableDirectBuffer indexKey = new UnsafeBuffer(new byte[KEY_LENGTH]);
    protected final MutableDirectBuffer indexValue = new UnsafeBuffer(new byte[VALUE_LENGTH]);

    protected PendingPartitionIterator iterator = new PendingPartitionIterator();

    public PendingPartitionsIndex()
    {
        this.pendingPartitions = new Bytes2BytesZbMap(KEY_LENGTH, VALUE_LENGTH);
    }

    public Bytes2BytesZbMap getRawMap()
    {
        return pendingPartitions;
    }

    public void close()
    {
        pendingPartitions.close();
    }

    public PendingPartition get(DirectBuffer topicName, int partitionId)
    {
        final int nameLength = topicName.capacity();
        initIndexKey(topicName, 0, nameLength, partitionId);

        final DirectBuffer currentValue = pendingPartitions.get(indexKey, 0, BitUtil.SIZE_OF_INT + nameLength);

        if (currentValue != null)
        {
            partition.wrap(indexKey, currentValue);
            return partition;
        }
        else
        {
            return null;
        }
    }

    public void putPartitionKey(DirectBuffer topicName, int partitionId, long partitionKey, long creationTimeout)
    {
        initIndexKey(topicName, 0, topicName.capacity(), partitionId);
        indexValue.putLong(VALUE_KEY_OFFSET, partitionKey, BYTE_ORDER);
        indexValue.putLong(VALUE_TIME_OFFSET, creationTimeout, BYTE_ORDER);

        pendingPartitions.put(indexKey, 0, BitUtil.SIZE_OF_INT + topicName.capacity(), indexValue);
    }

    public void removePartitionKey(DirectBuffer topicName, int partitionId)
    {
        initIndexKey(topicName, 0, topicName.capacity(), partitionId);

        pendingPartitions.remove(indexKey, 0, BitUtil.SIZE_OF_INT + topicName.capacity());
    }

    private void initIndexKey(DirectBuffer topicName, int offset, int length, int partitionId)
    {
        indexKey.putInt(KEY_PARTITION_ID_OFFSET, partitionId, BYTE_ORDER);
        indexKey.putInt(KEY_TOPIC_NAME_LENGTH_OFFSET, length);
        indexKey.putBytes(KEY_TOPIC_NAME_OFFSET, topicName, offset, length);
    }

    public boolean isEmpty()
    {
        return !iterator().hasNext();
    }

    @Override
    public Iterator<PendingPartition> iterator()
    {
        iterator.reset();

        return iterator;
    }

    protected class PendingPartitionIterator implements Iterator<PendingPartition>
    {

        protected Iterator<Bytes2BytesZbMapEntry> iterator;
        protected DirectBuffer currentKey;
        protected DirectBuffer currentValue;
        protected PendingPartitionImpl partition = new PendingPartitionImpl();

        public void reset()
        {
            iterator = pendingPartitions.iterator();
        }

        @Override
        public boolean hasNext()
        {
            return iterator.hasNext();
        }

        @Override
        public PendingPartition next()
        {
            final Bytes2BytesZbMapEntry entry = iterator.next();
            partition.wrap(entry.getKey(), entry.getValue());
            return partition;
        }
    }

    public interface PendingPartition
    {
        DirectBuffer getTopicName();

        int getPartitionId();

        long getPartitionKey();

        long getCreationTimeout();
    }

    protected class PendingPartitionImpl implements PendingPartition
    {
        protected DirectBuffer currentKey;
        protected DirectBuffer currentTopicName = new UnsafeBuffer(0, 0);
        protected DirectBuffer currentValue;

        public void wrap(DirectBuffer currentKey, DirectBuffer currentValue)
        {
            this.currentKey = currentKey;
            this.currentValue = currentValue;
        }

        @Override
        public DirectBuffer getTopicName()
        {
            final int topicNameLength = currentKey.getInt(KEY_TOPIC_NAME_LENGTH_OFFSET, BYTE_ORDER);
            currentTopicName.wrap(currentKey, KEY_TOPIC_NAME_OFFSET, topicNameLength);
            return currentTopicName;
        }

        @Override
        public int getPartitionId()
        {
            return currentKey.getInt(KEY_PARTITION_ID_OFFSET, BYTE_ORDER);
        }

        @Override
        public long getPartitionKey()
        {
            return currentValue.getLong(VALUE_KEY_OFFSET, BYTE_ORDER);
        }

        @Override
        public long getCreationTimeout()
        {
            return currentValue.getLong(VALUE_TIME_OFFSET, BYTE_ORDER);
        }

    }

}
