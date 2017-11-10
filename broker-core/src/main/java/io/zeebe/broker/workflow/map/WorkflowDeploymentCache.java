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

import static org.agrona.BitUtil.*;

import java.nio.ByteOrder;
import java.util.Iterator;

import io.zeebe.broker.workflow.data.WorkflowEvent;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.snapshot.ZbMapSnapshotSupport;
import io.zeebe.map.Bytes2LongZbMap;
import io.zeebe.map.Long2BytesZbMap;
import io.zeebe.model.bpmn.BpmnModelApi;
import io.zeebe.model.bpmn.impl.ZeebeConstraints;
import io.zeebe.model.bpmn.instance.Workflow;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.util.buffer.BufferUtil;
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

    private static final int PROCESS_ID_LENGTH = ZeebeConstraints.ID_MAX_LENGTH * SIZE_OF_CHAR;
    private static final int ID_VERSION_KEY_LENGTH = PROCESS_ID_LENGTH + SIZE_OF_INT;

    private static final int POSITION_WORKFLOW_VALUE_LENGTH = SIZE_OF_LONG + SIZE_OF_INT;

    private static final int POSITION_OFFSET = 0;
    private static final int WORKFLOW_INDEX_OFFSET = POSITION_OFFSET + SIZE_OF_LONG;

    private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private final UnsafeBuffer positionWorkflowValueBuffer = new UnsafeBuffer(new byte[POSITION_WORKFLOW_VALUE_LENGTH]);

    private final UnsafeBuffer idVersionKeyBuffer = new UnsafeBuffer(new byte[ID_VERSION_KEY_LENGTH]);
    private int idVersionKeyBufferLength;

    private final WorkflowEvent workflowEvent = new WorkflowEvent();

    private final Bytes2LongZbMap idVersionToKeyMap;
    private final Long2BytesZbMap keyToPositionWorkflowMap;

    private final ZbMapSnapshotSupport<Bytes2LongZbMap> idVersionSnapshot;
    private final ZbMapSnapshotSupport<Long2BytesZbMap> keyPositionSnapshot;

    private final LongLruCache<DeployedWorkflow> cache;
    private final LogStreamReader logStreamReader;

    private final BpmnModelApi bpmn = new BpmnModelApi();

    public WorkflowDeploymentCache(int cacheSize, LogStreamReader logStreamReader)
    {
        this.idVersionToKeyMap = new Bytes2LongZbMap(ID_VERSION_KEY_LENGTH);
        this.keyToPositionWorkflowMap = new Long2BytesZbMap(POSITION_WORKFLOW_VALUE_LENGTH);

        this.idVersionSnapshot = new ZbMapSnapshotSupport<>(idVersionToKeyMap);
        this.keyPositionSnapshot = new ZbMapSnapshotSupport<>(keyToPositionWorkflowMap);

        this.logStreamReader = logStreamReader;
        this.cache = new LongLruCache<>(cacheSize, this::lookupWorkflow, (workflow) ->
        { });
    }

    public ZbMapSnapshotSupport<Bytes2LongZbMap> getIdVersionSnapshot()
    {
        return idVersionSnapshot;
    }

    public ZbMapSnapshotSupport<Long2BytesZbMap> getKeyPositionSnapshot()
    {
        return keyPositionSnapshot;
    }

    private void wrapIdVersionKey(DirectBuffer bpmnProcessId, int version)
    {
        bpmnProcessId.getBytes(0, idVersionKeyBuffer, 0, bpmnProcessId.capacity());
        idVersionKeyBuffer.putInt(bpmnProcessId.capacity(), version, BYTE_ORDER);

        idVersionKeyBufferLength = bpmnProcessId.capacity() + SIZE_OF_INT;
    }

    public void addDeployedWorkflow(long eventPosition, long workflowKey, WorkflowEvent event)
    {
        positionWorkflowValueBuffer.putLong(POSITION_OFFSET, eventPosition, BYTE_ORDER);
        positionWorkflowValueBuffer.putInt(WORKFLOW_INDEX_OFFSET, getWorkflowIndex(event), BYTE_ORDER);
        keyToPositionWorkflowMap.put(workflowKey, positionWorkflowValueBuffer);

        wrapIdVersionKey(event.getBpmnProcessId(), event.getVersion());
        idVersionToKeyMap.put(idVersionKeyBuffer, 0, idVersionKeyBufferLength, workflowKey);

        // override the latest version by the given key
        wrapIdVersionKey(event.getBpmnProcessId(), LATEST_VERSION);
        idVersionToKeyMap.put(idVersionKeyBuffer, 0, idVersionKeyBufferLength, workflowKey);
    }

    private int getWorkflowIndex(WorkflowEvent event)
    {
        final DirectBuffer bpmnProcessId = event.getBpmnProcessId();
        final DirectBuffer bpmnXml = event.getBpmnXml();

        int index = 0;

        final WorkflowDefinition workflowDefinition = bpmn.readFromXmlBuffer(bpmnXml);
        final Iterator<Workflow> workflows = workflowDefinition.getWorkflows().iterator();
        while (workflows.hasNext())
        {
            final Workflow workflow = workflows.next();
            if (BufferUtil.equals(bpmnProcessId, workflow.getBpmnProcessId()))
            {
                return index;
            }
            index += 1;
        }
        throw new RuntimeException("workflow not found");
    }

    public void removeDeployedWorkflow(long workflowKey, DirectBuffer bpmnProcessId, int version)
    {
        keyToPositionWorkflowMap.remove(workflowKey);

        wrapIdVersionKey(bpmnProcessId, version);
        idVersionToKeyMap.remove(idVersionKeyBuffer, 0, idVersionKeyBufferLength, -1L);

        // override the latest version by the key of the previous version
        final long workflowKeyOfPreviousVersion = getWorkflowKeyByIdAndVersion(bpmnProcessId, version - 1);

        wrapIdVersionKey(bpmnProcessId, LATEST_VERSION);
        idVersionToKeyMap.put(idVersionKeyBuffer, 0, idVersionKeyBufferLength, workflowKeyOfPreviousVersion);
    }

    public long getWorkflowKeyByIdAndLatestVersion(DirectBuffer bpmnProcessId)
    {
        return getWorkflowKeyByIdAndVersion(bpmnProcessId, LATEST_VERSION);
    }

    public long getWorkflowKeyByIdAndVersion(DirectBuffer bpmnProcessId, int version)
    {
        wrapIdVersionKey(bpmnProcessId, version);

        return idVersionToKeyMap.get(idVersionKeyBuffer, 0, idVersionKeyBufferLength, -1L);
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

        final DirectBuffer positionWorkflowBuffer = keyToPositionWorkflowMap.get(key);

        if (positionWorkflowBuffer != null)
        {
            final long eventPosition = positionWorkflowBuffer.getLong(POSITION_OFFSET, BYTE_ORDER);
            final int workflowIndex = positionWorkflowBuffer.getInt(WORKFLOW_INDEX_OFFSET, BYTE_ORDER);

            final boolean found = logStreamReader.seek(eventPosition);
            if (found && logStreamReader.hasNext())
            {
                final LoggedEvent event = logStreamReader.next();

                workflowEvent.reset();
                event.readValue(workflowEvent);

                final WorkflowDefinition workflowDefinition = bpmn.readFromXmlBuffer(workflowEvent.getBpmnXml());
                final Workflow workflow = getWorkflowAt(workflowDefinition, workflowIndex);

                deployedWorkflow = new DeployedWorkflow(workflow, workflowEvent.getVersion());
            }
        }
        return deployedWorkflow;
    }

    private Workflow getWorkflowAt(final WorkflowDefinition workflowDefinition, final int index)
    {
        int i = 0;

        final Iterator<Workflow> workflows = workflowDefinition.getWorkflows().iterator();
        while (workflows.hasNext())
        {
            final Workflow workflow = workflows.next();

            if (index == i)
            {
                return workflow;
            }
            i += 1;
        }
        throw new RuntimeException("no workflow found");
    }

    @Override
    public void close()
    {
        idVersionToKeyMap.close();
        keyToPositionWorkflowMap.close();
    }

}
