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
package io.zeebe.broker.workflow.map;

import static org.agrona.BitUtil.SIZE_OF_CHAR;
import static org.agrona.BitUtil.SIZE_OF_INT;

import java.nio.ByteOrder;

import io.zeebe.broker.workflow.data.WorkflowEvent;
import io.zeebe.broker.workflow.graph.model.ExecutableWorkflow;
import io.zeebe.broker.workflow.graph.transformer.BpmnTransformer;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.snapshot.ZbMapSnapshotSupport;
import io.zeebe.map.Bytes2LongZbMap;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongLruCache;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Cache of deployed workflows. It contains an LRU cache which maps the workflow
 * key to the parsed workflow. Additionally, it holds an map which maps BPMN
 * process id + version to workflow key.
 *
 * <p>
 * When a workflow is requested then the parsed workflow is returned from the
 * cache. If it is not present in the cache then the deployed event is seek in
 * the log stream.
 */
public class WorkflowDeploymentCache implements AutoCloseable
{
    private static final int LATEST_VERSION = -1;

    private static final int SIZE_OF_PROCESS_ID = BpmnTransformer.ID_MAX_LENGTH * SIZE_OF_CHAR;
    private static final int SIZE_OF_COMPOSITE_KEY = SIZE_OF_PROCESS_ID + SIZE_OF_INT;

    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[SIZE_OF_COMPOSITE_KEY]);

    private final WorkflowEvent workflowEvent = new WorkflowEvent();
    private final BpmnTransformer bpmnTransformer = new BpmnTransformer();

    private final Bytes2LongZbMap idVersionToKeyMap;

    private final ZbMapSnapshotSupport<Bytes2LongZbMap> snapshotSupport;

    private final LongLruCache<ExecutableWorkflow> cache;
    private final LogStreamReader logStreamReader;

    public WorkflowDeploymentCache(int cacheSize, LogStreamReader logStreamReader)
    {
        this.idVersionToKeyMap = new Bytes2LongZbMap(SIZE_OF_COMPOSITE_KEY);

        this.snapshotSupport = new ZbMapSnapshotSupport<>(idVersionToKeyMap);

        this.logStreamReader = logStreamReader;
        this.cache = new LongLruCache<>(cacheSize, this::lookupWorkflow, (workflow) ->
        { });
    }

    public ZbMapSnapshotSupport<Bytes2LongZbMap> getSnapshotSupport()
    {
        return snapshotSupport;
    }

    private void wrap(DirectBuffer bpmnProcessId, int version)
    {
        bpmnProcessId.getBytes(0, buffer, 0, bpmnProcessId.capacity());
        buffer.putInt(bpmnProcessId.capacity(), version, ByteOrder.LITTLE_ENDIAN);
    }

    public void addDeployedWorkflow(long workflowKey, DirectBuffer bpmnProcessId, int version)
    {
        wrap(bpmnProcessId, version);
        idVersionToKeyMap.put(buffer.byteArray(), workflowKey);

        // override the latest version by the given key
        wrap(bpmnProcessId, LATEST_VERSION);
        idVersionToKeyMap.put(buffer.byteArray(), workflowKey);
    }

    public long getWorkflowKeyByIdAndLatestVersion(DirectBuffer bpmnProcessId)
    {
        return getWorkflowKeyByIdAndVersion(bpmnProcessId, LATEST_VERSION);
    }

    public long getWorkflowKeyByIdAndVersion(DirectBuffer bpmnProcessId, int version)
    {
        wrap(bpmnProcessId, version);

        return idVersionToKeyMap.get(buffer.byteArray(), -1L);
    }

    public ExecutableWorkflow getWorkflow(long workflowKey)
    {
        ExecutableWorkflow workflow = null;

        if (workflowKey >= 0)
        {
            workflow = cache.lookup(workflowKey);
        }

        return workflow;
    }

    private ExecutableWorkflow lookupWorkflow(long position)
    {
        ExecutableWorkflow workflow = null;

        final boolean found = logStreamReader.seek(position);
        if (found && logStreamReader.hasNext())
        {
            final LoggedEvent event = logStreamReader.next();

            workflowEvent.reset();
            event.readValue(workflowEvent);

            // currently, it can only be one
            workflow = bpmnTransformer.transform(workflowEvent.getBpmnXml()).get(0);

            workflow.setVersion(workflowEvent.getVersion());
        }
        return workflow;
    }

    @Override
    public void close()
    {
        idVersionToKeyMap.close();
    }

}
