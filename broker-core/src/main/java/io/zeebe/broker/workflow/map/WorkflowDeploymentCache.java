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
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.snapshot.ZbMapSnapshotSupport;
import io.zeebe.map.Bytes2LongZbMap;
import io.zeebe.map.Long2LongZbMap;
import io.zeebe.model.bpmn.BpmnModelApi;
import io.zeebe.model.bpmn.impl.ZeebeConstraints;
import io.zeebe.model.bpmn.instance.Workflow;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
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

    private static final int SIZE_OF_PROCESS_ID = ZeebeConstraints.ID_MAX_LENGTH * SIZE_OF_CHAR;
    private static final int SIZE_OF_COMPOSITE_KEY = SIZE_OF_PROCESS_ID + SIZE_OF_INT;

    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[SIZE_OF_COMPOSITE_KEY]);
    private int bufferLength;

    private final WorkflowEvent workflowEvent = new WorkflowEvent();

    private final Bytes2LongZbMap idVersionToKeyMap;
    private final Long2LongZbMap keyToPositionMap;

    private final ZbMapSnapshotSupport<Bytes2LongZbMap> idVersionSnapshot;
    private final ZbMapSnapshotSupport<Long2LongZbMap> keyPositionSnapshot;

    private final LongLruCache<DeployedWorkflow> cache;
    private final LogStreamReader logStreamReader;

    private final BpmnModelApi bpmn = new BpmnModelApi();

    public WorkflowDeploymentCache(int cacheSize, LogStreamReader logStreamReader)
    {
        this.idVersionToKeyMap = new Bytes2LongZbMap(SIZE_OF_COMPOSITE_KEY);
        this.keyToPositionMap = new Long2LongZbMap();

        this.idVersionSnapshot = new ZbMapSnapshotSupport<>(idVersionToKeyMap);
        this.keyPositionSnapshot = new ZbMapSnapshotSupport<>(keyToPositionMap);

        this.logStreamReader = logStreamReader;
        this.cache = new LongLruCache<>(cacheSize, this::lookupWorkflow, (workflow) ->
        { });
    }

    public ZbMapSnapshotSupport<Bytes2LongZbMap> getIdVersionSnapshot()
    {
        return idVersionSnapshot;
    }

    public ZbMapSnapshotSupport<Long2LongZbMap> getKeyPositionSnapshot()
    {
        return keyPositionSnapshot;
    }

    private void wrap(DirectBuffer bpmnProcessId, int version)
    {
        bpmnProcessId.getBytes(0, buffer, 0, bpmnProcessId.capacity());
        buffer.putInt(bpmnProcessId.capacity(), version, ByteOrder.LITTLE_ENDIAN);

        bufferLength = bpmnProcessId.capacity() + SIZE_OF_INT;
    }

    public void addDeployedWorkflow(long eventPosition, long workflowKey, DirectBuffer bpmnProcessId, int version)
    {
        keyToPositionMap.put(workflowKey, eventPosition);

        wrap(bpmnProcessId, version);
        idVersionToKeyMap.put(buffer, 0, bufferLength, workflowKey);

        // override the latest version by the given key
        wrap(bpmnProcessId, LATEST_VERSION);
        idVersionToKeyMap.put(buffer, 0, bufferLength, workflowKey);
    }

    public void removeDeployedWorkflow(long workflowKey, DirectBuffer bpmnProcessId, int version)
    {
        keyToPositionMap.remove(workflowKey, -1L);

        wrap(bpmnProcessId, version);
        idVersionToKeyMap.remove(buffer, 0, bufferLength, -1L);

        // override the latest version by the key of the previous version
        final long workflowKeyOfPreviousVersion = getWorkflowKeyByIdAndVersion(bpmnProcessId, version - 1);

        wrap(bpmnProcessId, LATEST_VERSION);
        idVersionToKeyMap.put(buffer, 0, bufferLength, workflowKeyOfPreviousVersion);
    }

    public long getWorkflowKeyByIdAndLatestVersion(DirectBuffer bpmnProcessId)
    {
        return getWorkflowKeyByIdAndVersion(bpmnProcessId, LATEST_VERSION);
    }

    public long getWorkflowKeyByIdAndVersion(DirectBuffer bpmnProcessId, int version)
    {
        wrap(bpmnProcessId, version);

        return idVersionToKeyMap.get(buffer, 0, bufferLength, -1L);
    }

    public DeployedWorkflow getWorkflow(long workflowKey)
    {
        DeployedWorkflow workflow = null;

        if (workflowKey >= 0)
        {
            workflow = cache.lookup(workflowKey);
        }

        return workflow;
    }

    private DeployedWorkflow lookupWorkflow(long key)
    {
        DeployedWorkflow deployedWorkflow = null;

        final long position = keyToPositionMap.get(key, -1L);

        final boolean found = logStreamReader.seek(position);
        if (found && logStreamReader.hasNext())
        {
            final LoggedEvent event = logStreamReader.next();

            workflowEvent.reset();
            event.readValue(workflowEvent);

            // currently, it can only be one
            final WorkflowDefinition workflowDefinition = bpmn.readFromXmlBuffer(workflowEvent.getBpmnXml());
            final Workflow workflow = workflowDefinition.getWorkflows().iterator().next();

            deployedWorkflow = new DeployedWorkflow(workflow, workflowEvent.getVersion());
        }
        return deployedWorkflow;
    }

    @Override
    public void close()
    {
        idVersionToKeyMap.close();
        keyToPositionMap.close();
    }

}
