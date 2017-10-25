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
import io.zeebe.map.Long2BytesZbMap;
import io.zeebe.map.iterator.Long2BytesZbMapEntry;

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

    protected static final int VALUE_LENGTH = 2 * BitUtil.SIZE_OF_LONG;
    protected static final int VALUE_POSITION_OFFSET = 0;
    protected static final int VALUE_TIME_OFFSET = BitUtil.SIZE_OF_LONG;

    protected final Long2BytesZbMap pendingPartitions;

    protected PendingPartitionImpl partition = new PendingPartitionImpl();

    protected final MutableDirectBuffer indexValue = new UnsafeBuffer(new byte[VALUE_LENGTH]);

    protected PendingPartitionIterator iterator = new PendingPartitionIterator();

    public PendingPartitionsIndex()
    {
        this.pendingPartitions = new Long2BytesZbMap(VALUE_LENGTH);
    }

    public Long2BytesZbMap getRawMap()
    {
        return pendingPartitions;
    }

    public PendingPartition get(int partitionId)
    {
        final DirectBuffer currentValue = pendingPartitions.get(partitionId);

        if (currentValue != null)
        {
            partition.wrap(partitionId, currentValue);
            return partition;
        }
        else
        {
            return null;
        }
    }

    public void putPartition(int partitionId, long position, long creationTimeout)
    {
        indexValue.putLong(VALUE_POSITION_OFFSET, position, BYTE_ORDER);
        indexValue.putLong(VALUE_TIME_OFFSET, creationTimeout, BYTE_ORDER);

        pendingPartitions.put(partitionId, indexValue);
    }

    public void removePartitionKey(int partitionId)
    {
        pendingPartitions.remove(partitionId);
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

        protected Iterator<Long2BytesZbMapEntry> iterator;
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
            final Long2BytesZbMapEntry entry = iterator.next();
            partition.wrap((int) entry.getKey(), entry.getValue());
            return partition;
        }
    }

    public interface PendingPartition
    {
        int getPartitionId();

        long getPosition();

        long getCreationTimeout();
    }

    protected class PendingPartitionImpl implements PendingPartition
    {
        protected int partitionId;
        protected DirectBuffer currentValue;

        public void wrap(int partitionId, DirectBuffer currentValue)
        {
            this.partitionId = partitionId;
            this.currentValue = currentValue;
        }

        @Override
        public int getPartitionId()
        {
            return partitionId;
        }

        @Override
        public long getPosition()
        {
            return currentValue.getLong(VALUE_POSITION_OFFSET, BYTE_ORDER);
        }

        @Override
        public long getCreationTimeout()
        {
            return currentValue.getLong(VALUE_TIME_OFFSET, BYTE_ORDER);
        }

    }

}
