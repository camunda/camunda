/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.deployment;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbString;
import io.zeebe.engine.processing.deployment.model.BpmnFactory;
import io.zeebe.engine.processing.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
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
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;

public final class WorkflowPersistenceCache {

  private final BpmnTransformer transformer = BpmnFactory.createTransformer();

  private final Map<DirectBuffer, Long2ObjectHashMap<DeployedWorkflow>>
      workflowsByProcessIdAndVersion = new HashMap<>();
  private final Long2ObjectHashMap<DeployedWorkflow> workflowsByKey;

  // workflow
  private final ColumnFamily<DbLong, PersistedWorkflow> workflowColumnFamily;
  private final DbLong workflowKey;
  private final PersistedWorkflow persistedWorkflow;

  private final ColumnFamily<DbCompositeKey, PersistedWorkflow> workflowByIdAndVersionColumnFamily;
  private final DbLong workflowVersion;
  private final DbCompositeKey<DbString, DbLong> idAndVersionKey;

  private final ColumnFamily<DbString, LatestWorkflowVersion> latestWorkflowColumnFamily;
  private final DbString workflowId;
  private final LatestWorkflowVersion latestVersion = new LatestWorkflowVersion();

  private final ColumnFamily<DbString, Digest> digestByIdColumnFamily;
  private final Digest digest = new Digest();

  public WorkflowPersistenceCache(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final DbContext dbContext) {
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
            ZbColumnFamilies.WORKFLOW_CACHE_LATEST_KEY, dbContext, workflowId, latestVersion);

    digestByIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.WORKFLOW_CACHE_DIGEST_BY_ID, dbContext, workflowId, digest);

    workflowsByKey = new Long2ObjectHashMap<>();
  }

  void putDeployment(final DeploymentRecord deploymentRecord) {
    for (final Workflow workflow : deploymentRecord.workflows()) {
      final long workflowKey = workflow.getKey();
      final DirectBuffer resourceName = workflow.getResourceNameBuffer();
      for (final DeploymentResource resource : deploymentRecord.resources()) {
        if (resource.getResourceNameBuffer().equals(resourceName)) {
          persistWorkflow(workflowKey, workflow, resource);
          updateLatestVersion(workflow);
        }
      }
    }
  }

  private void persistWorkflow(
      final long workflowKey, final Workflow workflow, final DeploymentResource resource) {
    persistedWorkflow.wrap(resource, workflow, workflowKey);
    this.workflowKey.wrapLong(workflowKey);
    workflowColumnFamily.put(this.workflowKey, persistedWorkflow);

    workflowId.wrapBuffer(workflow.getBpmnProcessIdBuffer());
    workflowVersion.wrapLong(workflow.getVersion());

    workflowByIdAndVersionColumnFamily.put(idAndVersionKey, persistedWorkflow);
  }

  private void updateLatestVersion(final Workflow workflow) {
    workflowId.wrapBuffer(workflow.getBpmnProcessIdBuffer());
    final LatestWorkflowVersion storedVersion = latestWorkflowColumnFamily.get(workflowId);
    final long latestVersion = storedVersion == null ? -1 : storedVersion.get();

    if (workflow.getVersion() > latestVersion) {
      this.latestVersion.set(workflow.getVersion());
      latestWorkflowColumnFamily.put(workflowId, this.latestVersion);
    }
  }

  // is called on getters, if workflow is not in memory
  private DeployedWorkflow updateInMemoryState(final PersistedWorkflow persistedWorkflow) {

    // we have to copy to store this in cache
    final byte[] bytes = new byte[persistedWorkflow.getLength()];
    final MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
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

  private BpmnModelInstance readModelInstanceFromBuffer(final DirectBuffer buffer) {
    try (final DirectBufferInputStream stream = new DirectBufferInputStream(buffer)) {
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
    final LatestWorkflowVersion latestVersion = latestWorkflowColumnFamily.get(workflowId);

    DeployedWorkflow deployedWorkflow;
    if (versionMap == null) {
      deployedWorkflow = lookupWorkflowByIdAndPersistedVersion(latestVersion);
    } else {
      deployedWorkflow = versionMap.get(latestVersion.get());
      if (deployedWorkflow == null) {
        deployedWorkflow = lookupWorkflowByIdAndPersistedVersion(latestVersion);
      }
    }
    return deployedWorkflow;
  }

  private DeployedWorkflow lookupWorkflowByIdAndPersistedVersion(
      final LatestWorkflowVersion version) {
    final long latestVersion = version != null ? version.get() : -1;
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

  private DeployedWorkflow lookupPersistenceState(final DirectBuffer processId, final int version) {
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

  private DeployedWorkflow lookupPersistenceStateForWorkflowByKey(final long workflowKey) {
    this.workflowKey.wrapLong(workflowKey);

    final PersistedWorkflow persistedWorkflow = workflowColumnFamily.get(this.workflowKey);
    if (persistedWorkflow != null) {
      updateInMemoryState(persistedWorkflow);

      final DeployedWorkflow deployedWorkflow = workflowsByKey.get(workflowKey);
      return deployedWorkflow;
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

  public void putLatestVersionDigest(final DirectBuffer processId, final DirectBuffer digest) {
    workflowId.wrapBuffer(processId);
    this.digest.set(digest);

    digestByIdColumnFamily.put(workflowId, this.digest);
  }

  public DirectBuffer getLatestVersionDigest(final DirectBuffer processId) {
    workflowId.wrapBuffer(processId);
    final Digest latestDigest = digestByIdColumnFamily.get(workflowId);
    return latestDigest == null || digest.get().byteArray() == null ? null : latestDigest.get();
  }
}
