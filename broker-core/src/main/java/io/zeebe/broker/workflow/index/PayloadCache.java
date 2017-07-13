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
package io.zeebe.broker.workflow.index;

import static io.zeebe.hashindex.HashIndex.OPTIMAL_BUCKET_COUNT;
import static io.zeebe.hashindex.HashIndex.OPTIMAL_INDEX_SIZE;

import org.agrona.DirectBuffer;

import io.zeebe.broker.logstreams.processor.HashIndexSnapshotSupport;
import io.zeebe.broker.workflow.data.WorkflowInstanceEvent;
import io.zeebe.hashindex.Long2LongHashIndex;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.util.cache.ExpandableBufferCache;

/**
 * Cache of workflow instance payload. It contains an LRU cache of the payload
 * and an index which holds the position of the payload events.
 *
 * <p>
 * When a payload is requested then the it is returned from the cache. If it is
 * not present in the cache then the payload event is seek in the log stream.
 */
public class PayloadCache implements AutoCloseable
{
    private final WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();

    private final Long2LongHashIndex index;
    private final HashIndexSnapshotSupport<Long2LongHashIndex> snapshotSupport;

    private final ExpandableBufferCache cache;
    private final LogStreamReader logStreamReader;

    public PayloadCache(int cacheSize, LogStreamReader logStreamReader)
    {
        this.index = new Long2LongHashIndex(OPTIMAL_INDEX_SIZE, OPTIMAL_BUCKET_COUNT);
        this.snapshotSupport = new HashIndexSnapshotSupport<>(index);

        this.logStreamReader = logStreamReader;
        this.cache = new ExpandableBufferCache(cacheSize, 1024, this::lookupPayload);
    }

    private DirectBuffer lookupPayload(long position)
    {
        DirectBuffer payload = null;

        final boolean found = logStreamReader.seek(position);
        if (found && logStreamReader.hasNext())
        {
            final LoggedEvent event = logStreamReader.next();

            workflowInstanceEvent.reset();
            event.readValue(workflowInstanceEvent);

            payload = workflowInstanceEvent.getPayload();
        }

        return payload;
    }

    public DirectBuffer getPayload(long workflowInstanceKey)
    {
        DirectBuffer payload = null;

        final long position = index.get(workflowInstanceKey, -1L);

        if (position > 0)
        {
            payload = cache.get(position);
        }
        return payload == null ? WorkflowInstanceEvent.NO_PAYLOAD : payload;
    }

    public void addPayload(long workflowInstanceKey, long payloadEventPosition, DirectBuffer payload)
    {
        index.put(workflowInstanceKey, payloadEventPosition);
        cache.put(payloadEventPosition, payload);
    }

    public void remove(long workflowInstanceKey)
    {
        index.remove(workflowInstanceKey, -1L);
    }

    public SnapshotSupport getSnapshotSupport()
    {
        return snapshotSupport;
    }

    @Override
    public void close()
    {
        index.close();
    }

}
