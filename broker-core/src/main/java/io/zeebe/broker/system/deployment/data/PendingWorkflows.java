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

import io.zeebe.map.Bytes2BytesZbMap;
import io.zeebe.map.iterator.Bytes2BytesZbMapEntry;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * (workflow-key, partition-id) -> (state, deployment-key)
 */
public class PendingWorkflows
{
    public static final short STATE_CREATE = 0;
    public static final short STATE_CREATED = 1;

    private static final int KEY_LENGTH = SIZE_OF_LONG + SIZE_OF_INT;
    private static final int VALUE_LENGTH = SIZE_OF_SHORT + SIZE_OF_LONG;

    private static final int WORKFLOW_KEY_OFFSET = 0;
    private static final int PARTITION_ID_OFFSET = WORKFLOW_KEY_OFFSET + SIZE_OF_INT;

    private static final int STATE_OFFSET = 0;
    private static final int DEPLOYMENT_KEY_OFFSET = STATE_OFFSET + SIZE_OF_SHORT;

    private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private final Bytes2BytesZbMap map = new Bytes2BytesZbMap(KEY_LENGTH, VALUE_LENGTH);

    private final UnsafeBuffer keyBuffer = new UnsafeBuffer(new byte[KEY_LENGTH]);
    private final UnsafeBuffer valueBuffer = new UnsafeBuffer(new byte[VALUE_LENGTH]);

    private final PendingWorkflow pendingWorkflow = new PendingWorkflow();
    private final PendingWorkflowIterator iterator = new PendingWorkflowIterator();

    public Bytes2BytesZbMap getRawMap()
    {
        return map;
    }

    public PendingWorkflow get(long workflowKey, int partitionId)
    {
        keyBuffer.putLong(WORKFLOW_KEY_OFFSET, workflowKey, BYTE_ORDER);
        keyBuffer.putLong(PARTITION_ID_OFFSET, partitionId, BYTE_ORDER);

        final DirectBuffer currentValue = map.get(keyBuffer);

        if (currentValue != null)
        {
            pendingWorkflow.wrap(keyBuffer, currentValue);
            return pendingWorkflow;
        }
        else
        {
            return null;
        }
    }

    public void put(long workflowKey, int partitionId, short state, long deploymentKey)
    {
        keyBuffer.putLong(WORKFLOW_KEY_OFFSET, workflowKey, BYTE_ORDER);
        keyBuffer.putLong(PARTITION_ID_OFFSET, partitionId, BYTE_ORDER);

        valueBuffer.putShort(STATE_OFFSET, state, BYTE_ORDER);
        valueBuffer.putLong(DEPLOYMENT_KEY_OFFSET, deploymentKey, BYTE_ORDER);

        map.put(keyBuffer, valueBuffer);
    }

    public void remove(long workflowKey, int partitionId)
    {
        keyBuffer.putLong(WORKFLOW_KEY_OFFSET, workflowKey, BYTE_ORDER);
        keyBuffer.putLong(PARTITION_ID_OFFSET, partitionId, BYTE_ORDER);

        map.remove(keyBuffer);
    }

    public PendingWorkflowIterator iterator()
    {
        iterator.reset();
        return iterator;
    }


    public class PendingWorkflowIterator implements Iterator<PendingWorkflow>
    {
        private Iterator<Bytes2BytesZbMapEntry> iterator;
        private PendingWorkflow pendingWorkflow = new PendingWorkflow();

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
        public PendingWorkflow next()
        {
            final Bytes2BytesZbMapEntry entry = iterator.next();
            pendingWorkflow.wrap(entry.getKey(), entry.getValue());

            return pendingWorkflow;
        }
    }

    public class PendingWorkflow
    {
        private DirectBuffer key;
        private DirectBuffer value;

        public void wrap(DirectBuffer key, DirectBuffer value)
        {
            this.key = key;
            this.value = value;
        }

        public long getWorkflowKey()
        {
            return key.getLong(WORKFLOW_KEY_OFFSET, BYTE_ORDER);
        }

        public int getPartitionId()
        {
            return key.getInt(PARTITION_ID_OFFSET, BYTE_ORDER);
        }

        public short getState()
        {
            return value.getShort(STATE_OFFSET, BYTE_ORDER);
        }

        public long getDeploymentKey()
        {
            return value.getLong(DEPLOYMENT_KEY_OFFSET, BYTE_ORDER);
        }
    }

}
