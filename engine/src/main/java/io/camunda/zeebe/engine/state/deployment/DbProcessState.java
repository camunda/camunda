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
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.processing.deployment.model.BpmnFactory;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessMetadata;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;

public final class DbProcessState implements MutableProcessState {

  private static final int DEFAULT_VERSION_VALUE = 0;

  private final BpmnTransformer transformer = BpmnFactory.createTransformer();
  private final ProcessRecord processRecordForDeployments = new ProcessRecord();

  private final Map<String, Map<DirectBuffer, Long2ObjectHashMap<DeployedProcess>>>
      processesByTenantAndProcessIdAndVersionCache = new HashMap<>();
  private final Map<String, Long2ObjectHashMap<DeployedProcess>> processByTenantAndKeyCache;

  /** [tenant id | process definition key] => process */
  private final ColumnFamily<DbTenantAwareKey<DbLong>, PersistedProcess> processColumnFamily;

  private final DbLong processDefinitionKey;
  private final PersistedProcess persistedProcess;
  private final DbString tenantIdKey;
  private final DbTenantAwareKey<DbLong> tenantAwareProcessDefinitionKey;

  private final ColumnFamily<DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>, PersistedProcess>
      processByIdAndVersionColumnFamily;
  private final DbLong processVersion;
  private final DbCompositeKey<DbString, DbLong> idAndVersionKey;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>
      tenantAwareProcessIdAndVersionKey;

  private final DbString processId;
  private final DbTenantAwareKey<DbString> tenantAwareProcessId;
  private final DbForeignKey<DbTenantAwareKey<DbString>> fkTenantAwareProcessId;

  private final ColumnFamily<DbForeignKey<DbTenantAwareKey<DbString>>, Digest>
      digestByIdColumnFamily;
  private final Digest digest = new Digest();

  private final VersionManager versionManager;

  public DbProcessState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    processDefinitionKey = new DbLong();
    persistedProcess = new PersistedProcess();
    tenantIdKey = new DbString();
    tenantAwareProcessDefinitionKey =
        new DbTenantAwareKey<>(tenantIdKey, processDefinitionKey, PlacementType.PREFIX);
    processColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_CACHE,
            transactionContext,
            tenantAwareProcessDefinitionKey,
            persistedProcess);

    processId = new DbString();
    processVersion = new DbLong();
    idAndVersionKey = new DbCompositeKey<>(processId, processVersion);
    tenantAwareProcessIdAndVersionKey =
        new DbTenantAwareKey<>(tenantIdKey, idAndVersionKey, PlacementType.PREFIX);
    processByIdAndVersionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_CACHE_BY_ID_AND_VERSION,
            transactionContext,
            tenantAwareProcessIdAndVersionKey,
            persistedProcess);

    tenantAwareProcessId = new DbTenantAwareKey<>(tenantIdKey, processId, PlacementType.PREFIX);
    fkTenantAwareProcessId =
        new DbForeignKey<>(
            tenantAwareProcessId,
            ZbColumnFamilies.PROCESS_CACHE_BY_ID_AND_VERSION,
            MatchType.Prefix);
    digestByIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_CACHE_DIGEST_BY_ID,
            transactionContext,
            fkTenantAwareProcessId,
            digest);

    processByTenantAndKeyCache = new HashMap<>();

    versionManager =
        new VersionManager(
            DEFAULT_VERSION_VALUE, zeebeDb, ZbColumnFamilies.PROCESS_VERSION, transactionContext);
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
  public void putLatestVersionDigest(final ProcessRecord processRecord) {
    tenantIdKey.wrapString(processRecord.getTenantId());
    processId.wrapBuffer(processRecord.getBpmnProcessIdBuffer());
    digest.set(processRecord.getChecksumBuffer());

    digestByIdColumnFamily.upsert(fkTenantAwareProcessId, digest);
  }

  @Override
  public void putProcess(final long key, final ProcessRecord processRecord) {
    persistProcess(key, processRecord);
    updateLatestVersion(processRecord);
    putLatestVersionDigest(processRecord);
  }

  @Override
  public void updateProcessState(
      final ProcessRecord processRecord, final PersistedProcessState state) {
    tenantIdKey.wrapString(processRecord.getTenantId());
    processDefinitionKey.wrapLong(processRecord.getProcessDefinitionKey());

    final var process = processColumnFamily.get(tenantAwareProcessDefinitionKey);
    process.setState(state);
    processColumnFamily.update(tenantAwareProcessDefinitionKey, process);
    updateInMemoryState(process);
  }

  @Override
  public void deleteProcess(final ProcessRecord processRecord) {
    tenantIdKey.wrapString(processRecord.getTenantId());
    processDefinitionKey.wrapLong(processRecord.getProcessDefinitionKey());
    processId.wrapString(processRecord.getBpmnProcessId());
    processVersion.wrapLong(processRecord.getVersion());

    processColumnFamily.deleteExisting(tenantAwareProcessDefinitionKey);
    processByIdAndVersionColumnFamily.deleteExisting(tenantAwareProcessIdAndVersionKey);

    processesByTenantAndProcessIdAndVersionCache
        .getOrDefault(processRecord.getTenantId(), new HashMap<>())
        .remove(processRecord.getBpmnProcessIdBuffer());
    processByTenantAndKeyCache
        .getOrDefault(processRecord.getTenantId(), new Long2ObjectHashMap<>())
        .remove(processRecord.getProcessDefinitionKey());

    final long latestVersion =
        versionManager.getLatestResourceVersion(
            processRecord.getBpmnProcessId(), processRecord.getTenantId());
    if (latestVersion == processRecord.getVersion()) {
      // As we don't set the digest to the digest of the previous there is a chance it does not
      // exist. This happens when deleting the latest version two times in a row. To be safe we must
      // use deleteIfExists.
      digestByIdColumnFamily.deleteIfExists(fkTenantAwareProcessId);
    }

    versionManager.deleteResourceVersion(
        processRecord.getBpmnProcessId(), processRecord.getVersion(), processRecord.getTenantId());
  }

  private void persistProcess(final long processDefinitionKey, final ProcessRecord processRecord) {
    tenantIdKey.wrapString(processRecord.getTenantId());
    persistedProcess.wrap(processRecord, processDefinitionKey);
    this.processDefinitionKey.wrapLong(processDefinitionKey);

    processColumnFamily.upsert(tenantAwareProcessDefinitionKey, persistedProcess);

    processId.wrapBuffer(processRecord.getBpmnProcessIdBuffer());
    processVersion.wrapLong(processRecord.getVersion());

    processByIdAndVersionColumnFamily.upsert(tenantAwareProcessIdAndVersionKey, persistedProcess);
  }

  private void updateLatestVersion(final ProcessRecord processRecord) {
    processId.wrapBuffer(processRecord.getBpmnProcessIdBuffer());
    final var bpmnProcessId = processRecord.getBpmnProcessId();
    final var version = processRecord.getVersion();
    versionManager.addResourceVersion(bpmnProcessId, version, processRecord.getTenantId());
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
            .filter(
                process -> BufferUtil.equals(persistedProcess.getBpmnProcessId(), process.getId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        String.format(
                            "Expected to find executable process in persisted process with key '%s',"
                                + " but after transformation no such executable process could be found.",
                            persistedProcess.getKey())));

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

    final Long2ObjectHashMap<DeployedProcess> keyMap =
        processByTenantAndKeyCache.computeIfAbsent(
            deployedProcess.getTenantId(), key -> new Long2ObjectHashMap<>());
    keyMap.put(deployedProcess.getKey(), deployedProcess);

    final Long2ObjectHashMap<DeployedProcess> versionMap =
        processesByTenantAndProcessIdAndVersionCache
            .computeIfAbsent(deployedProcess.getTenantId(), key -> new HashMap<>())
            .computeIfAbsent(bpmnProcessId, key -> new Long2ObjectHashMap<>());

    final int version = deployedProcess.getVersion();
    versionMap.put(version, deployedProcess);
  }

  @Override
  public DeployedProcess getLatestProcessVersionByProcessId(
      final DirectBuffer processIdBuffer, final String tenantId) {
    final Long2ObjectHashMap<DeployedProcess> versionMap =
        processesByTenantAndProcessIdAndVersionCache
            .getOrDefault(tenantId, new HashMap<>())
            .get(processIdBuffer);

    processId.wrapBuffer(processIdBuffer);
    final long latestVersion = versionManager.getLatestResourceVersion(processIdBuffer, tenantId);

    DeployedProcess deployedProcess;
    if (versionMap == null) {
      deployedProcess = lookupProcessByIdAndPersistedVersion(latestVersion, tenantId);
    } else {
      deployedProcess = versionMap.get(latestVersion);
      if (deployedProcess == null) {
        deployedProcess = lookupProcessByIdAndPersistedVersion(latestVersion, tenantId);
      }
    }
    return deployedProcess;
  }

  @Override
  public DeployedProcess getProcessByProcessIdAndVersion(
      final DirectBuffer processId, final int version, final String tenantId) {
    final Long2ObjectHashMap<DeployedProcess> versionMap =
        processesByTenantAndProcessIdAndVersionCache
            .getOrDefault(tenantId, new HashMap<>())
            .get(processId);

    if (versionMap != null) {
      final DeployedProcess deployedProcess = versionMap.get(version);
      return deployedProcess != null
          ? deployedProcess
          : lookupPersistenceState(processId, version, tenantId);
    } else {
      return lookupPersistenceState(processId, version, tenantId);
    }
  }

  @Override
  public DeployedProcess getProcessByKeyAndTenant(final long key, final String tenantId) {
    final DeployedProcess deployedProcess =
        processByTenantAndKeyCache.getOrDefault(tenantId, new Long2ObjectHashMap<>()).get(key);

    if (deployedProcess != null) {
      return deployedProcess;
    } else {
      return lookupPersistenceStateForProcessByKey(key, tenantId);
    }
  }

  @Override
  public DirectBuffer getLatestVersionDigest(
      final DirectBuffer processIdBuffer, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    processId.wrapBuffer(processIdBuffer);
    final Digest latestDigest = digestByIdColumnFamily.get(fkTenantAwareProcessId);
    return latestDigest == null || digest.get().byteArray() == null ? null : latestDigest.get();
  }

  @Override
  public int getLatestProcessVersion(final String bpmnProcessId, final String tenantId) {
    return (int) versionManager.getLatestResourceVersion(bpmnProcessId, tenantId);
  }

  @Override
  public int getNextProcessVersion(final String bpmnProcessId, final String tenantId) {
    return (int) versionManager.getHighestResourceVersion(bpmnProcessId, tenantId) + 1;
  }

  @Override
  public Optional<Integer> findProcessVersionBefore(
      final String bpmnProcessId, final long version, final String tenantId) {
    return versionManager.findResourceVersionBefore(bpmnProcessId, version, tenantId);
  }

  @Override
  public <T extends ExecutableFlowElement> T getFlowElement(
      final long processDefinitionKey,
      final String tenantId,
      final DirectBuffer elementId,
      final Class<T> elementType) {

    final var deployedProcess = getProcessByKeyAndTenant(processDefinitionKey, tenantId);
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

  @Override
  public void clearCache() {
    processByTenantAndKeyCache.clear();
    processesByTenantAndProcessIdAndVersionCache.clear();
    versionManager.clear();
  }

  private DeployedProcess lookupProcessByIdAndPersistedVersion(
      final long latestVersion, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    processVersion.wrapLong(latestVersion);

    final PersistedProcess processWithVersionAndId =
        processByIdAndVersionColumnFamily.get(tenantAwareProcessIdAndVersionKey);

    if (processWithVersionAndId != null) {
      return updateInMemoryState(processWithVersionAndId);
    }
    return null;
  }

  private DeployedProcess lookupPersistenceState(
      final DirectBuffer processIdBuffer, final int version, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    processId.wrapBuffer(processIdBuffer);
    processVersion.wrapLong(version);

    final PersistedProcess processWithVersionAndId =
        processByIdAndVersionColumnFamily.get(tenantAwareProcessIdAndVersionKey);

    if (processWithVersionAndId != null) {
      updateInMemoryState(processWithVersionAndId);

      final Long2ObjectHashMap<DeployedProcess> newVersionMap =
          processesByTenantAndProcessIdAndVersionCache
              .getOrDefault(tenantId, new HashMap<>())
              .get(processIdBuffer);

      if (newVersionMap != null) {
        return newVersionMap.get(version);
      }
    }
    // does not exist in persistence and in memory state
    return null;
  }

  private DeployedProcess lookupPersistenceStateForProcessByKey(
      final long processDefinitionKey, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    this.processDefinitionKey.wrapLong(processDefinitionKey);

    final PersistedProcess processWithKey =
        processColumnFamily.get(tenantAwareProcessDefinitionKey);
    if (processWithKey != null) {
      updateInMemoryState(processWithKey);

      return processByTenantAndKeyCache
          .getOrDefault(tenantId, new Long2ObjectHashMap<>())
          .get(processDefinitionKey);
    }
    // does not exist in persistence and in memory state
    return null;
  }
}
