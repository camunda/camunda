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
package io.zeebe.broker.workflow.state;

import static io.zeebe.logstreams.rocksdb.ZeebeStateConstants.STATE_BYTE_ORDER;

import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.BpmnTransformer;
import io.zeebe.logstreams.state.StateController;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.zeebe.protocol.impl.record.value.deployment.Workflow;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongHashSet;
import org.agrona.io.DirectBufferInputStream;
import org.rocksdb.ColumnFamilyHandle;

public class WorkflowPersistenceCache {
  private static final byte[] WORKFLOWS_FAMILY_NAME = "workflows".getBytes();
  private static final byte[] WORKFLOWS_BY_ID_AND_VERSION_FAMILY_NAME =
      "workflowsByIdAndVersion".getBytes();
  private static final byte[] LATEST_WORKFLOWS_FAMILY_NAME = "latestWorkflow".getBytes();

  public static final byte[][] COLUMN_FAMILY_NAMES = {
    WORKFLOWS_FAMILY_NAME, WORKFLOWS_BY_ID_AND_VERSION_FAMILY_NAME, LATEST_WORKFLOWS_FAMILY_NAME
  };

  private final BpmnTransformer transformer = new BpmnTransformer();

  private final Map<DirectBuffer, Int2ObjectHashMap<DeployedWorkflow>>
      workflowsByProcessIdAndVersion = new HashMap<>();

  private final StateController rocksDbWrapper;
  private final ColumnFamilyHandle workflowsHandle;
  private final ColumnFamilyHandle workflowsByIdAndVersionHandle;
  private final ColumnFamilyHandle latestWorkflowsHandle;

  private final ExpandableArrayBuffer keyBuffer = new ExpandableArrayBuffer();
  private final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();

  private final LongHashSet deployments;
  private final Long2ObjectHashMap<DeployedWorkflow> workflowsByKey;
  private final PersistenceHelper persistenceHelper;

  public WorkflowPersistenceCache(StateController rocksDbWrapper) throws Exception {
    this.rocksDbWrapper = rocksDbWrapper;
    persistenceHelper = new PersistenceHelper(rocksDbWrapper);

    workflowsHandle = rocksDbWrapper.getColumnFamilyHandle(WORKFLOWS_FAMILY_NAME);
    workflowsByIdAndVersionHandle =
        rocksDbWrapper.getColumnFamilyHandle(WORKFLOWS_BY_ID_AND_VERSION_FAMILY_NAME);
    latestWorkflowsHandle = rocksDbWrapper.getColumnFamilyHandle(LATEST_WORKFLOWS_FAMILY_NAME);

    deployments = new LongHashSet();
    workflowsByKey = new Long2ObjectHashMap<>();
  }

  protected boolean putDeployment(
      final long deploymentKey, final DeploymentRecord deploymentRecord) {
    final boolean isNewDeployment = !deployments.contains(deploymentKey);
    if (isNewDeployment) {
      for (final Workflow workflow : deploymentRecord.workflows()) {
        final long workflowKey = workflow.getKey();
        final DirectBuffer resourceName = workflow.getResourceName();
        for (final DeploymentResource resource : deploymentRecord.resources()) {
          if (resource.getResourceName().equals(resourceName)) {
            persistWorkflow(workflowKey, workflow, resource);
          }
        }
      }
      deployments.add(deploymentKey);
    }
    return isNewDeployment;
  }

  private void persistWorkflow(
      final long workflowKey, final Workflow workflow, final DeploymentResource resource) {
    final PersistedWorkflow persistedWorkflow =
        new PersistedWorkflow(
            workflow.getBpmnProcessId(),
            resource.getResourceName(),
            resource.getResource(),
            workflow.getVersion(),
            workflowKey);
    persistedWorkflow.write(valueBuffer, 0);
    final int keyLength = persistedWorkflow.writeKeyToBuffer(keyBuffer, 0);
    final int valueLength = persistedWorkflow.getLength();

    rocksDbWrapper.put(workflowsHandle, workflowKey, valueBuffer.byteArray(), 0, valueLength);

    rocksDbWrapper.put(
        workflowsByIdAndVersionHandle,
        keyBuffer.byteArray(),
        0,
        keyLength,
        valueBuffer.byteArray(),
        0,
        valueLength);

    // put latest workflow
    final int versionOffset = keyLength - Integer.BYTES;
    rocksDbWrapper.put(
        latestWorkflowsHandle,
        keyBuffer.byteArray(),
        0,
        versionOffset, // without version
        keyBuffer.byteArray(),
        versionOffset,
        Integer.BYTES);
  }

  // is called on getters, if workflow is not in memory
  private DeployedWorkflow updateInMemoryState(PersistedWorkflow persistedWorkflow) {

    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(new DirectBufferInputStream(persistedWorkflow.getResource()));
    final List<ExecutableWorkflow> definitions = transformer.transformDefinitions(modelInstance);

    final ExecutableWorkflow executableWorkflow =
        definitions
            .stream()
            .filter((w) -> BufferUtil.equals(persistedWorkflow.getBpmnProcessId(), w.getId()))
            .findFirst()
            .get();

    final DeployedWorkflow deployedWorkflow =
        new DeployedWorkflow(executableWorkflow, persistedWorkflow);

    addWorkflowToInMemoryState(deployedWorkflow);

    return deployedWorkflow;
  }

  private void addWorkflowToInMemoryState(final DeployedWorkflow deployedWorkflow) {
    final DirectBuffer bpmnProcessId = deployedWorkflow.getBpmnProcessId();
    workflowsByKey.put(deployedWorkflow.getKey(), deployedWorkflow);

    Int2ObjectHashMap<DeployedWorkflow> versionMap =
        workflowsByProcessIdAndVersion.get(bpmnProcessId);

    if (versionMap == null) {
      versionMap = new Int2ObjectHashMap<>();
      workflowsByProcessIdAndVersion.put(bpmnProcessId, versionMap);
    }

    final int version = deployedWorkflow.getVersion();
    versionMap.put(version, deployedWorkflow);
  }

  public DeployedWorkflow getLatestWorkflowVersionByProcessId(final DirectBuffer processId) {
    final Int2ObjectHashMap<DeployedWorkflow> versionMap =
        workflowsByProcessIdAndVersion.get(processId);

    final int keyLength = PersistedWorkflow.writeWorkflowKey(keyBuffer, 0, processId, -1);
    final PersistedInt latestVersion =
        persistenceHelper.getValueInstance(
            PersistedInt.class,
            latestWorkflowsHandle,
            keyBuffer,
            0,
            keyLength - Integer.BYTES,
            valueBuffer);

    DeployedWorkflow deployedWorkflow;
    if (versionMap == null) {
      deployedWorkflow = lookupWorkflowByIdAndPersistedVersion(processId, latestVersion);
    } else {
      deployedWorkflow = versionMap.get(latestVersion.getValue());
      if (deployedWorkflow == null) {
        deployedWorkflow = lookupWorkflowByIdAndPersistedVersion(processId, latestVersion);
      }
    }
    return deployedWorkflow;
  }

  private DeployedWorkflow lookupWorkflowByIdAndPersistedVersion(
      DirectBuffer processId, PersistedInt version) {
    final int latestVersion = version != null ? version.getValue() : -1;
    final int keyLength =
        PersistedWorkflow.writeWorkflowKey(keyBuffer, 0, processId, latestVersion);
    final PersistedWorkflow persistedWorkflow =
        persistenceHelper.getValueInstance(
            PersistedWorkflow.class,
            workflowsByIdAndVersionHandle,
            keyBuffer,
            0,
            keyLength,
            valueBuffer);

    if (persistedWorkflow != null) {
      final DeployedWorkflow deployedWorkflow = updateInMemoryState(persistedWorkflow);
      return deployedWorkflow;
    }
    return null;
  }

  public DeployedWorkflow getWorkflowByProcessIdAndVersion(
      final DirectBuffer processId, final int version) {
    final Int2ObjectHashMap<DeployedWorkflow> versionMap =
        workflowsByProcessIdAndVersion.get(processId);

    if (versionMap != null) {
      final DeployedWorkflow deployedWorkflow = versionMap.get(version);
      return deployedWorkflow != null
          ? deployedWorkflow
          : lookupPersistenceState(processId, version);
    } else {
      return lookupPersistenceState(processId, version);
    }
  }

  private DeployedWorkflow lookupPersistenceState(DirectBuffer processId, int version) {
    final int keyLength = PersistedWorkflow.writeWorkflowKey(keyBuffer, 0, processId, version);
    final PersistedWorkflow persistedWorkflow =
        persistenceHelper.getValueInstance(
            PersistedWorkflow.class,
            workflowsByIdAndVersionHandle,
            keyBuffer,
            0,
            keyLength,
            valueBuffer);

    if (persistedWorkflow != null) {
      updateInMemoryState(persistedWorkflow);

      final Int2ObjectHashMap<DeployedWorkflow> newVersionMap =
          workflowsByProcessIdAndVersion.get(processId);

      if (newVersionMap != null) {
        return newVersionMap.get(version);
      }
    }
    // does not exist in persistence and in memory state
    return null;
  }

  public DeployedWorkflow getWorkflowByKey(final long key) {
    final DeployedWorkflow deployedWorkflow = workflowsByKey.get(key);

    if (deployedWorkflow != null) {
      return deployedWorkflow;
    } else {
      return lookupPersistenceStateForWorkflowByKey(key);
    }
  }

  private DeployedWorkflow lookupPersistenceStateForWorkflowByKey(long workflowKey) {
    keyBuffer.putLong(0, workflowKey, STATE_BYTE_ORDER);
    final PersistedWorkflow persistedWorkflow =
        persistenceHelper.getValueInstance(
            PersistedWorkflow.class, workflowsHandle, keyBuffer, 0, Long.BYTES, valueBuffer);

    if (persistedWorkflow != null) {
      updateInMemoryState(persistedWorkflow);

      final DeployedWorkflow deployedWorkflow = workflowsByKey.get(workflowKey);
      if (deployedWorkflow != null) {
        return deployedWorkflow;
      }
    }
    // does not exist in persistence and in memory state
    return null;
  }

  public Collection<DeployedWorkflow> getWorkflows() {
    updateCompleteInMemoryState();
    return workflowsByKey.values();
  }

  public Collection<DeployedWorkflow> getWorkflowsByBpmnProcessId(
      final DirectBuffer bpmnProcessId) {
    updateCompleteInMemoryState();

    final Int2ObjectHashMap<DeployedWorkflow> workflowsByVersions =
        workflowsByProcessIdAndVersion.get(bpmnProcessId);

    if (workflowsByVersions != null) {
      return workflowsByVersions.values();
    }
    return Collections.EMPTY_LIST;
  }

  private void updateCompleteInMemoryState() {
    // update in memory state
    rocksDbWrapper.foreach(
        workflowsHandle,
        (key, value) -> {
          valueBuffer.putBytes(0, value);
          final PersistedWorkflow persistedWorkflow = new PersistedWorkflow();
          persistedWorkflow.wrap(valueBuffer, 0, value.length);
          updateInMemoryState(persistedWorkflow);
        });
  }
}
