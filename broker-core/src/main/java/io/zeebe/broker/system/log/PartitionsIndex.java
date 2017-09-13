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

import io.zeebe.map.Bytes2LongZbMap;

/**
 * Maps
 *
 * (topic name, partition id) => partition key
 *
 * Entries are only contained while a partition is pending to be created.
 */
public class PartitionsIndex
{

    protected static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
    protected static final int PENDING_PARTITION_KEY_LENGTH = TopicsIndex.MAX_TOPIC_NAME_LENGTH + BitUtil.SIZE_OF_INT;

    protected final Bytes2LongZbMap pendingPartitions;

    protected final MutableDirectBuffer indexKey = new UnsafeBuffer(new byte[PENDING_PARTITION_KEY_LENGTH]);

    public PartitionsIndex()
    {
        this.pendingPartitions = new Bytes2LongZbMap(PENDING_PARTITION_KEY_LENGTH);
    }

    public Bytes2LongZbMap getRawMap()
    {
        return pendingPartitions;
    }

    public void close()
    {
        pendingPartitions.close();
    }

    public long getPartitionKey(DirectBuffer topicName, int partitionId)
    {
        final int nameLength = topicName.capacity();
        initIndexKey(topicName, 0, nameLength, partitionId);

        return pendingPartitions.get(indexKey, 0, BitUtil.SIZE_OF_INT + nameLength, -1);
    }

    public void putPartitionKey(DirectBuffer topicName, int partitionId, long partitionKey)
    {
        initIndexKey(topicName, 0, topicName.capacity(), partitionId);

        pendingPartitions.put(indexKey, 0, BitUtil.SIZE_OF_INT + topicName.capacity(), partitionKey);
    }

    public long removePartitionKey(DirectBuffer topicName, int partitionId)
    {
        initIndexKey(topicName, 0, topicName.capacity(), partitionId);

        return pendingPartitions.remove(indexKey, 0, BitUtil.SIZE_OF_INT + topicName.capacity(), -1);
    }

    private void initIndexKey(DirectBuffer topicName, int offset, int length, int partitionId)
    {
        indexKey.putInt(0, partitionId, BYTE_ORDER);
        indexKey.putBytes(BitUtil.SIZE_OF_INT, topicName, offset, length);
    }

    public boolean isEmpty()
    {
        return !pendingPartitions.iterator().hasNext();
    }
}
