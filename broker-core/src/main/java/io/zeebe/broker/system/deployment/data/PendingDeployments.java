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

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import java.nio.ByteOrder;
import java.util.Iterator;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.system.deployment.data.PendingDeployments.PendingDeployment;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.map.Long2BytesZbMap;
import io.zeebe.map.iterator.Long2BytesZbMapEntry;

/**
 * deployment-key -> (deployment-event-position, timeout, topic-name)
 */
public class PendingDeployments implements Iterable<PendingDeployment>
{
    private static final int VALUE_LENGTH = SIZE_OF_LONG + (2 * SIZE_OF_INT) + LogStream.MAX_TOPIC_NAME_LENGTH;

    private static final int DEPLOYMENT_EVENT_POSITION_OFFSET = 0;
    private static final int STATE_OFFSET = DEPLOYMENT_EVENT_POSITION_OFFSET + SIZE_OF_LONG;
    private static final int TOPIC_NAME_LENGTH_OFFSET = STATE_OFFSET + SIZE_OF_INT;
    private static final int TOPIC_NAME_OFFSET = TOPIC_NAME_LENGTH_OFFSET + SIZE_OF_INT;

    private static final int STATE_UNRESOLVED = 0;
    private static final int STATE_RESOLVED = 1;

    private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private final Long2BytesZbMap map = new Long2BytesZbMap(VALUE_LENGTH);

    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[VALUE_LENGTH]);
    private final UnsafeBuffer topicNameBuffer = new UnsafeBuffer(0, 0);

    private final PendingDeployment pendingDeployment = new PendingDeployment();
    private final PendingDeploymentIterator iterator = new PendingDeploymentIterator();

    public Long2BytesZbMap getRawMap()
    {
        return map;
    }

    public PendingDeployment get(long deploymentKey)
    {
        final DirectBuffer currentValue = map.get(deploymentKey);

        if (currentValue != null)
        {
            pendingDeployment.wrap(deploymentKey, currentValue);
            return pendingDeployment;
        }
        else
        {
            return null;
        }
    }

    public void put(long deploymentKey, long deploymentEventPosition, DirectBuffer topicName)
    {
        buffer.putLong(DEPLOYMENT_EVENT_POSITION_OFFSET, deploymentEventPosition, BYTE_ORDER);
        buffer.putInt(STATE_OFFSET, STATE_UNRESOLVED, BYTE_ORDER);

        final int topicNameLength = topicName.capacity();
        buffer.putInt(TOPIC_NAME_LENGTH_OFFSET, topicNameLength, BYTE_ORDER);
        buffer.putBytes(TOPIC_NAME_OFFSET, topicName, 0, topicNameLength);

        map.put(deploymentKey, buffer);
    }

    public void markResolved(long deploymentKey)
    {
        final DirectBuffer currentValue = map.get(deploymentKey);
        buffer.putBytes(0, currentValue, 0, VALUE_LENGTH);
        buffer.putInt(STATE_OFFSET, STATE_RESOLVED, BYTE_ORDER);

        map.put(deploymentKey, buffer);
    }

    public void remove(long deploymentKey)
    {
        map.remove(deploymentKey);
    }

    public boolean isEmpty()
    {
        return !iterator().hasNext();
    }

    public PendingDeploymentIterator iterator()
    {
        iterator.reset();
        return iterator;
    }

    public class PendingDeploymentIterator implements Iterator<PendingDeployment>
    {
        private Iterator<Long2BytesZbMapEntry> iterator;
        private PendingDeployment pendingDeployment = new PendingDeployment();

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
        public PendingDeployment next()
        {
            final Long2BytesZbMapEntry entry = iterator.next();
            pendingDeployment.wrap(entry.getKey(), entry.getValue());

            return pendingDeployment;
        }
    }

    public class PendingDeployment
    {
        private long deploymentKey;
        private DirectBuffer currentValue;

        public void wrap(long deploymentKey, DirectBuffer value)
        {
            this.deploymentKey = deploymentKey;
            this.currentValue = value;
        }

        public long getDeploymentKey()
        {
            return deploymentKey;
        }

        public long getDeploymentEventPosition()
        {
            return currentValue.getLong(DEPLOYMENT_EVENT_POSITION_OFFSET, BYTE_ORDER);
        }

        public DirectBuffer getTopicName()
        {
            final int length = currentValue.getInt(TOPIC_NAME_LENGTH_OFFSET, BYTE_ORDER);
            topicNameBuffer.wrap(currentValue, TOPIC_NAME_OFFSET, length);

            return topicNameBuffer;
        }

        public boolean isResolved()
        {
            return currentValue.getInt(STATE_OFFSET, BYTE_ORDER) == STATE_RESOLVED;
        }
    }

}
