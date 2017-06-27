/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.workflow.index;

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
        this.index = new Long2LongHashIndex(Short.MAX_VALUE, 256);
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
