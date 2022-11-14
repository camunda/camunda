/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbForeignKey.MatchType;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.processing.deployment.model.BpmnFactory;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessMetadata;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.util.buffer.BufferUtil;
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

public final class DbProcessState implements MutableProcessState {

  private static final int DEFAULT_VERSION_VALUE = 0;

  private final BpmnTransformer transformer = BpmnFactory.createTransformer();
  private final ProcessRecord processRecordForDeployments = new ProcessRecord();

  private final Map<DirectBuffer, Long2ObjectHashMap<DeployedProcess>>
      processesByProcessIdAndVersion = new HashMap<>();
  private final Long2ObjectHashMap<DeployedProcess> processesByKey;

  // process
  private final ColumnFamily<DbLong, PersistedProcess> processColumnFamily;
  private final DbLong processDefinitionKey;
  private final PersistedProcess persistedProcess;

  private final ColumnFamily<DbCompositeKey<DbString, DbLong>, PersistedProcess>
      processByIdAndVersionColumnFamily;
  private final DbLong processVersion;
  private final DbCompositeKey<DbString, DbLong> idAndVersionKey;

  private final DbString processId;
  private final DbForeignKey<DbString> fkProcessId;

  private final ColumnFamily<DbForeignKey<DbString>, Digest> digestByIdColumnFamily;
  private final Digest digest = new Digest();

  private final NextValueManager versionManager;

  public DbProcessState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    processDefinitionKey = new DbLong();
    persistedProcess = new PersistedProcess();
    processColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_CACHE,
            transactionContext,
            processDefinitionKey,
            persistedProcess);

    processId = new DbString();
    processVersion = new DbLong();
    idAndVersionKey = new DbCompositeKey<>(processId, processVersion);
    processByIdAndVersionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_CACHE_BY_ID_AND_VERSION,
            transactionContext,
            idAndVersionKey,
            persistedProcess);

    fkProcessId =
        new DbForeignKey<>(
            processId, ZbColumnFamilies.PROCESS_CACHE_BY_ID_AND_VERSION, MatchType.Prefix);
    digestByIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_CACHE_DIGEST_BY_ID, transactionContext, fkProcessId, digest);

    processesByKey = new Long2ObjectHashMap<>();

    versionManager =
        new NextValueManager(
            DEFAULT_VERSION_VALUE, zeebeDb, transactionContext, ZbColumnFamilies.PROCESS_VERSION);
  }

  @Override
  public void putDeployment(final DeploymentRecord deploymentRecord) {
    for (final ProcessMetadata metadata : deploymentRecord.processesMetadata()) {
      for (final DeploymentResource resource : deploymentRecord.getResources()) {
        if (resource.getResourceName().equals(metadata.getResourceName())) {
          processRecordForDeployments.reset();
          processRecordForDeployments.wrap(metadata, resource.getResource());
          putProcess(metadata.getKey(), processRecordForDeployments);
        }
      }
    }
  }

  @Override
  public void putLatestVersionDigest(
      final DirectBuffer processIdBuffer, final DirectBuffer digest) {
    processId.wrapBuffer(processIdBuffer);
    this.digest.set(digest);

    digestByIdColumnFamily.upsert(fkProcessId, this.digest);
  }

  @Override
  public void putProcess(final long key, final ProcessRecord processRecord) {
    persistProcess(key, processRecord);
    updateLatestVersion(processRecord);
    putLatestVersionDigest(
        processRecord.getBpmnProcessIdBuffer(), processRecord.getChecksumBuffer());
  }

  private void persistProcess(final long processDefinitionKey, final ProcessRecord processRecord) {
    persistedProcess.wrap(processRecord, processDefinitionKey);
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    processColumnFamily.upsert(this.processDefinitionKey, persistedProcess);

    processId.wrapBuffer(processRecord.getBpmnProcessIdBuffer());
    processVersion.wrapLong(processRecord.getVersion());

    processByIdAndVersionColumnFamily.upsert(idAndVersionKey, persistedProcess);
  }

  private void updateLatestVersion(final ProcessRecord processRecord) {
    processId.wrapBuffer(processRecord.getBpmnProcessIdBuffer());
    final var bpmnProcessId = processRecord.getBpmnProcessId();

    final var currentVersion = versionManager.getCurrentValue(bpmnProcessId);
    final var nextVersion = processRecord.getVersion();

    if (nextVersion > currentVersion) {
      versionManager.setValue(bpmnProcessId, nextVersion);
    }
  }

  // is called on getters, if process is not in memory
  private DeployedProcess updateInMemoryState(final PersistedProcess persistedProcess) {

    // we have to copy to store this in cache
    final byte[] bytes = new byte[persistedProcess.getLength()];
    final MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
    persistedProcess.write(buffer, 0);

    final PersistedProcess copiedProcess = new PersistedProcess();
    copiedProcess.wrap(buffer, 0, persistedProcess.getLength());

    final BpmnModelInstance modelInstance =
        readModelInstanceFromBuffer(copiedProcess.getResource());
    final List<ExecutableProcess> definitions = transformer.transformDefinitions(modelInstance);

    final ExecutableProcess executableProcess =
        definitions.stream()
            .filter(w -> BufferUtil.equals(persistedProcess.getBpmnProcessId(), w.getId()))
            .findFirst()
            .orElseThrow();

    final DeployedProcess deployedProcess = new DeployedProcess(executableProcess, copiedProcess);

    addProcessToInMemoryState(deployedProcess);

    return deployedProcess;
  }

  private BpmnModelInstance readModelInstanceFromBuffer(final DirectBuffer buffer) {
    try (final DirectBufferInputStream stream = new DirectBufferInputStream(buffer)) {
      return Bpmn.readModelFromStream(stream);
    }
  }

  private void addProcessToInMemoryState(final DeployedProcess deployedProcess) {
    final DirectBuffer bpmnProcessId = deployedProcess.getBpmnProcessId();
    processesByKey.put(deployedProcess.getKey(), deployedProcess);

    final Long2ObjectHashMap<DeployedProcess> versionMap =
        processesByProcessIdAndVersion.computeIfAbsent(
            bpmnProcessId, id -> new Long2ObjectHashMap<>());

    final int version = deployedProcess.getVersion();
    versionMap.put(version, deployedProcess);
  }

  @Override
  public DeployedProcess getLatestProcessVersionByProcessId(final DirectBuffer processIdBuffer) {
    final Long2ObjectHashMap<DeployedProcess> versionMap =
        processesByProcessIdAndVersion.get(processIdBuffer);

    processId.wrapBuffer(processIdBuffer);
    final long latestVersion = versionManager.getCurrentValue(processIdBuffer);

    DeployedProcess deployedProcess;
    if (versionMap == null) {
      deployedProcess = lookupProcessByIdAndPersistedVersion(latestVersion);
    } else {
      deployedProcess = versionMap.get(latestVersion);
      if (deployedProcess == null) {
        deployedProcess = lookupProcessByIdAndPersistedVersion(latestVersion);
      }
    }
    return deployedProcess;
  }

  @Override
  public DeployedProcess getProcessByProcessIdAndVersion(
      final DirectBuffer processId, final int version) {
    final Long2ObjectHashMap<DeployedProcess> versionMap =
        processesByProcessIdAndVersion.get(processId);

    if (versionMap != null) {
      final DeployedProcess deployedProcess = versionMap.get(version);
      return deployedProcess != null ? deployedProcess : lookupPersistenceState(processId, version);
    } else {
      return lookupPersistenceState(processId, version);
    }
  }

  @Override
  public DeployedProcess getProcessByKey(final long key) {
    final DeployedProcess deployedProcess = processesByKey.get(key);

    if (deployedProcess != null) {
      return deployedProcess;
    } else {
      return lookupPersistenceStateForProcessByKey(key);
    }
  }

  @Override
  public Collection<DeployedProcess> getProcesses() {
    updateCompleteInMemoryState();
    return processesByKey.values();
  }

  @Override
  public Collection<DeployedProcess> getProcessesByBpmnProcessId(final DirectBuffer bpmnProcessId) {
    updateCompleteInMemoryState();

    final Long2ObjectHashMap<DeployedProcess> processesByVersions =
        processesByProcessIdAndVersion.get(bpmnProcessId);

    if (processesByVersions != null) {
      return processesByVersions.values();
    }
    return Collections.emptyList();
  }

  @Override
  public DirectBuffer getLatestVersionDigest(final DirectBuffer processIdBuffer) {
    processId.wrapBuffer(processIdBuffer);
    final Digest latestDigest = digestByIdColumnFamily.get(fkProcessId);
    return latestDigest == null || digest.get().byteArray() == null ? null : latestDigest.get();
  }

  @Override
  public int getProcessVersion(final String bpmnProcessId) {
    return (int) versionManager.getCurrentValue(bpmnProcessId);
  }

  @Override
  public <T extends ExecutableFlowElement> T getFlowElement(
      final long processDefinitionKey, final DirectBuffer elementId, final Class<T> elementType) {

    final var deployedProcess = getProcessByKey(processDefinitionKey);
    if (deployedProcess == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to find a process deployed with key '%d' but not found.",
              processDefinitionKey));
    }

    final var process = deployedProcess.getProcess();
    final var element = process.getElementById(elementId, elementType);
    if (element == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to find a flow element with id '%s' in process with key '%d' but not found.",
              bufferAsString(elementId), processDefinitionKey));
    }

    return element;
  }

  private DeployedProcess lookupProcessByIdAndPersistedVersion(final long latestVersion) {
    processVersion.wrapLong(latestVersion);

    final PersistedProcess processWithVersionAndId =
        processByIdAndVersionColumnFamily.get(idAndVersionKey);

    if (processWithVersionAndId != null) {
      return updateInMemoryState(processWithVersionAndId);
    }
    return null;
  }

  private DeployedProcess lookupPersistenceState(
      final DirectBuffer processIdBuffer, final int version) {
    processId.wrapBuffer(processIdBuffer);
    processVersion.wrapLong(version);

    final PersistedProcess processWithVersionAndId =
        processByIdAndVersionColumnFamily.get(idAndVersionKey);

    if (processWithVersionAndId != null) {
      updateInMemoryState(processWithVersionAndId);

      final Long2ObjectHashMap<DeployedProcess> newVersionMap =
          processesByProcessIdAndVersion.get(processIdBuffer);

      if (newVersionMap != null) {
        return newVersionMap.get(version);
      }
    }
    // does not exist in persistence and in memory state
    return null;
  }

  private DeployedProcess lookupPersistenceStateForProcessByKey(final long processDefinitionKey) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);

    final PersistedProcess processWithKey = processColumnFamily.get(this.processDefinitionKey);
    if (processWithKey != null) {
      updateInMemoryState(processWithKey);

      return processesByKey.get(processDefinitionKey);
    }
    // does not exist in persistence and in memory state
    return null;
  }

  private void updateCompleteInMemoryState() {
    processColumnFamily.forEach(this::updateInMemoryState);
  }
}
