/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.state.deployment;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbString;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.BpmnTransformer;
import io.zeebe.engine.state.ZbColumnFamilies;
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
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongHashSet;
import org.agrona.io.DirectBufferInputStream;

public class WorkflowPersistenceCache {
  private final BpmnTransformer transformer = new BpmnTransformer();

  private final Map<DirectBuffer, Long2ObjectHashMap<DeployedWorkflow>>
      workflowsByProcessIdAndVersion = new HashMap<>();
  private final LongHashSet deployments;
  private final Long2ObjectHashMap<DeployedWorkflow> workflowsByKey;

  // workflow
  private final ColumnFamily<DbLong, PersistedWorkflow> workflowColumnFamily;
  private final DbLong workflowKey;
  private final PersistedWorkflow persistedWorkflow;

  private final ColumnFamily<DbCompositeKey, PersistedWorkflow> workflowByIdAndVersionColumnFamily;
  private final DbCompositeKey<DbString, DbLong> idAndVersionKey;

  private final ColumnFamily<DbString, DbLong> latestWorkflowColumnFamily;
  private final DbString workflowId;
  private final DbLong workflowVersion;

  public WorkflowPersistenceCache(ZeebeDb<ZbColumnFamilies> zeebeDb, DbContext dbContext) {
    workflowKey = new DbLong();
    persistedWorkflow = new PersistedWorkflow();
    workflowColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.WORKFLOW_CACHE, dbContext, workflowKey, persistedWorkflow);

    workflowId = new DbString();
    workflowVersion = new DbLong();
    idAndVersionKey = new DbCompositeKey<>(workflowId, workflowVersion);
    workflowByIdAndVersionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.WORKFLOW_CACHE_BY_ID_AND_VERSION,
            dbContext,
            idAndVersionKey,
            persistedWorkflow);

    latestWorkflowColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.WORKFLOW_CACHE_LATEST_KEY, dbContext, workflowId, workflowVersion);

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
            updateLatestVersion(workflow);
          }
        }
      }
      deployments.add(deploymentKey);
    }
    return isNewDeployment;
  }

  private void persistWorkflow(
      final long workflowKey, final Workflow workflow, final DeploymentResource resource) {
    persistedWorkflow.wrap(resource, workflow, workflowKey);
    this.workflowKey.wrapLong(workflowKey);
    workflowColumnFamily.put(this.workflowKey, persistedWorkflow);

    workflowId.wrapBuffer(workflow.getBpmnProcessId());
    workflowVersion.wrapLong(workflow.getVersion());

    workflowByIdAndVersionColumnFamily.put(idAndVersionKey, persistedWorkflow);
  }

  private void updateLatestVersion(final Workflow workflow) {
    workflowId.wrapBuffer(workflow.getBpmnProcessId());
    final DbLong storedVersion = latestWorkflowColumnFamily.get(workflowId);
    final long latestVersion = storedVersion == null ? -1 : storedVersion.getValue();

    if (workflow.getVersion() > latestVersion) {
      workflowVersion.wrapLong(workflow.getVersion());
      latestWorkflowColumnFamily.put(workflowId, workflowVersion);
    }
  }

  private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
  // is called on getters, if workflow is not in memory
  private DeployedWorkflow updateInMemoryState(PersistedWorkflow persistedWorkflow) {

    // we have to copy to store this in cache
    persistedWorkflow.write(buffer, 0);
    final PersistedWorkflow copiedWorkflow = new PersistedWorkflow();
    copiedWorkflow.wrap(buffer, 0, persistedWorkflow.getLength());

    final BpmnModelInstance modelInstance =
        readModelInstanceFromBuffer(copiedWorkflow.getResource());
    final List<ExecutableWorkflow> definitions = transformer.transformDefinitions(modelInstance);

    final ExecutableWorkflow executableWorkflow =
        definitions.stream()
            .filter((w) -> BufferUtil.equals(persistedWorkflow.getBpmnProcessId(), w.getId()))
            .findFirst()
            .get();

    final DeployedWorkflow deployedWorkflow =
        new DeployedWorkflow(executableWorkflow, copiedWorkflow);

    addWorkflowToInMemoryState(deployedWorkflow);

    return deployedWorkflow;
  }

  private BpmnModelInstance readModelInstanceFromBuffer(DirectBuffer buffer) {
    try (DirectBufferInputStream stream = new DirectBufferInputStream(buffer)) {
      return Bpmn.readModelFromStream(stream);
    }
  }

  private void addWorkflowToInMemoryState(final DeployedWorkflow deployedWorkflow) {
    final DirectBuffer bpmnProcessId = deployedWorkflow.getBpmnProcessId();
    workflowsByKey.put(deployedWorkflow.getKey(), deployedWorkflow);

    Long2ObjectHashMap<DeployedWorkflow> versionMap =
        workflowsByProcessIdAndVersion.get(bpmnProcessId);

    if (versionMap == null) {
      versionMap = new Long2ObjectHashMap<>();
      workflowsByProcessIdAndVersion.put(bpmnProcessId, versionMap);
    }

    final int version = deployedWorkflow.getVersion();
    versionMap.put(version, deployedWorkflow);
  }

  public DeployedWorkflow getLatestWorkflowVersionByProcessId(final DirectBuffer processId) {
    final Long2ObjectHashMap<DeployedWorkflow> versionMap =
        workflowsByProcessIdAndVersion.get(processId);

    workflowId.wrapBuffer(processId);
    final DbLong latestVersion = latestWorkflowColumnFamily.get(workflowId);

    DeployedWorkflow deployedWorkflow;
    if (versionMap == null) {
      deployedWorkflow = lookupWorkflowByIdAndPersistedVersion(latestVersion);
    } else {
      deployedWorkflow = versionMap.get(latestVersion.getValue());
      if (deployedWorkflow == null) {
        deployedWorkflow = lookupWorkflowByIdAndPersistedVersion(latestVersion);
      }
    }
    return deployedWorkflow;
  }

  private DeployedWorkflow lookupWorkflowByIdAndPersistedVersion(DbLong version) {
    final long latestVersion = version != null ? version.getValue() : -1;
    workflowVersion.wrapLong(latestVersion);

    final PersistedWorkflow persistedWorkflow =
        workflowByIdAndVersionColumnFamily.get(idAndVersionKey);

    if (persistedWorkflow != null) {
      final DeployedWorkflow deployedWorkflow = updateInMemoryState(persistedWorkflow);
      return deployedWorkflow;
    }
    return null;
  }

  public DeployedWorkflow getWorkflowByProcessIdAndVersion(
      final DirectBuffer processId, final int version) {
    final Long2ObjectHashMap<DeployedWorkflow> versionMap =
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
    workflowId.wrapBuffer(processId);
    workflowVersion.wrapLong(version);

    final PersistedWorkflow persistedWorkflow =
        workflowByIdAndVersionColumnFamily.get(idAndVersionKey);

    if (persistedWorkflow != null) {
      updateInMemoryState(persistedWorkflow);

      final Long2ObjectHashMap<DeployedWorkflow> newVersionMap =
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
    this.workflowKey.wrapLong(workflowKey);

    final PersistedWorkflow persistedWorkflow = workflowColumnFamily.get(this.workflowKey);
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

    final Long2ObjectHashMap<DeployedWorkflow> workflowsByVersions =
        workflowsByProcessIdAndVersion.get(bpmnProcessId);

    if (workflowsByVersions != null) {
      return workflowsByVersions.values();
    }
    return Collections.emptyList();
  }

  private void updateCompleteInMemoryState() {
    workflowColumnFamily.forEach((workflow) -> updateInMemoryState(persistedWorkflow));
  }
}
