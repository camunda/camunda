/**
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

import static org.agrona.BitUtil.SIZE_OF_CHAR;
import static org.agrona.BitUtil.SIZE_OF_INT;

import java.nio.ByteOrder;

import org.agrona.DirectBuffer;
import org.agrona.collections.LongLruCache;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.logstreams.processor.HashIndexSnapshotSupport;
import io.zeebe.broker.workflow.data.WorkflowDeploymentEvent;
import io.zeebe.broker.workflow.graph.model.ExecutableWorkflow;
import io.zeebe.broker.workflow.graph.transformer.BpmnTransformer;
import io.zeebe.hashindex.Bytes2LongHashIndex;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.SnapshotSupport;

/**
 * Cache of deployed workflows. It contains an LRU cache of the parsed workflows
 * and an index which holds the position of the deployed workflow events.
 *
 * <p>
 * When a workflow is requested then the parsed workflow is returned from the
 * cache. If it is not present in the cache then the deployed event is seek in
 * the log stream.
 */
public class WorkflowDeploymentCache implements AutoCloseable
{
    private static final int SIZE_OF_PROCESS_ID = BpmnTransformer.ID_MAX_LENGTH * SIZE_OF_CHAR;
    private static final int SIZE_OF_COMPOSITE_KEY = SIZE_OF_PROCESS_ID + SIZE_OF_INT;

    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[SIZE_OF_COMPOSITE_KEY]);

    private final WorkflowDeploymentEvent deploymentEvent = new WorkflowDeploymentEvent();
    private final BpmnTransformer bpmnTransformer = new BpmnTransformer();

    private final Bytes2LongHashIndex index;
    private final HashIndexSnapshotSupport<Bytes2LongHashIndex> snapshotSupport;

    private final LongLruCache<ExecutableWorkflow> cache;
    private final LogStreamReader logStreamReader;

    public WorkflowDeploymentCache(int cacheSize, LogStreamReader logStreamReader)
    {
        this.index = new Bytes2LongHashIndex(Short.MAX_VALUE, 64, SIZE_OF_COMPOSITE_KEY);
        this.snapshotSupport = new HashIndexSnapshotSupport<>(index);

        this.logStreamReader = logStreamReader;
        this.cache = new LongLruCache<>(cacheSize, this::lookupWorkflow, (workflow) ->
        { });
    }

    public SnapshotSupport getSnapshotSupport()
    {
        return snapshotSupport;
    }

    private void wrap(DirectBuffer bpmnProcessId, int version)
    {
        bpmnProcessId.getBytes(0, buffer, 0, bpmnProcessId.capacity());
        buffer.putInt(bpmnProcessId.capacity(), version, ByteOrder.LITTLE_ENDIAN);
    }

    public void addDeployedWorkflow(DirectBuffer bpmnProcessId, int version, long deploymentEventPosition)
    {
        wrap(bpmnProcessId, version);

        index.put(buffer.byteArray(), deploymentEventPosition);
    }

    public boolean hasDeployedWorkflow(DirectBuffer bpmnProcessId, int version)
    {
        return getDeployedWorkflowPosition(bpmnProcessId, version) > 0;
    }

    private long getDeployedWorkflowPosition(DirectBuffer bpmnProcessId, int version)
    {
        wrap(bpmnProcessId, version);

        return index.get(buffer, 0, buffer.capacity(), -1L);
    }

    public ExecutableWorkflow getWorkflow(DirectBuffer bpmnProcessId, int version)
    {
        ExecutableWorkflow workflow = null;

        final long position = getDeployedWorkflowPosition(bpmnProcessId, version);
        if (position >= 0)
        {
            workflow = cache.lookup(position);
        }

        if (workflow == null)
        {
            throw new RuntimeException("No workflow deployment event found.");
        }

        return workflow;
    }

    private ExecutableWorkflow lookupWorkflow(long position)
    {
        ExecutableWorkflow workflow = null;

        final boolean found = logStreamReader.seek(position);
        if (found && logStreamReader.hasNext())
        {
            final LoggedEvent workflowEvent = logStreamReader.next();

            deploymentEvent.reset();
            workflowEvent.readValue(deploymentEvent);

            // currently, it can only be one
            workflow = bpmnTransformer.transform(deploymentEvent.getBpmnXml()).get(0);
        }
        return workflow;
    }

    @Override
    public void close()
    {
        index.close();
    }

}
