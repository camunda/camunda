/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionListener;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.EngineMetricsDoc;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Caches deployed processes, i.e. persisted processes whose BPMN resource is parsed and transformed
 * into an executable process, which is the expensive part of reading a process.
 *
 * <p>The cache doesn't depend on how processes are stored: it loads missing processes through a
 * {@link PersistedProcessLoader}, and the store tells it about changes to its processes.
 *
 * <p>Entries are added while a transaction is open, from reads that see the transaction's own
 * changes. To never serve a process that was not committed, the cache follows the transactions of
 * the store: when a transaction is rolled back, it evicts every entry added during it, so the next
 * read loads the committed process again. Evictions need no undo, as they only cause a reload.
 */
@NullMarked
public final class ProcessDefinitionCache implements TransactionListener {

  private final BpmnTransformer transformer;
  private final PersistedProcessLoader loader;

  private final Cache<TenantIdAndProcessIdAndVersion, DeployedProcess>
      processesByTenantAndProcessIdAndVersionCache;
  private final Cache<TenantIdAndProcessDefinitionKey, DeployedProcess> processByTenantAndKeyCache;
  private final Cache<TenantIdAndProcessIdAndDeploymentKey, Long>
      processDefinitionKeyByTenantAndProcessIdAndDeploymentKeyCache;

  private final List<TenantIdAndProcessDefinitionKey> keysCachedInTransaction = new ArrayList<>();
  private final List<TenantIdAndProcessIdAndVersion> versionsCachedInTransaction =
      new ArrayList<>();
  private final List<TenantIdAndProcessIdAndDeploymentKey> deploymentKeysCachedInTransaction =
      new ArrayList<>();

  /**
   * @param transactionContext the context of the transactions the loader reads in; the cache
   *     listens to it to drop the entries of rolled back transactions
   */
  public ProcessDefinitionCache(
      final EngineConfiguration config,
      final BpmnTransformer transformer,
      final PersistedProcessLoader loader,
      final TransactionContext transactionContext,
      final MeterRegistry meterRegistry) {
    this.transformer = transformer;
    this.loader = loader;
    transactionContext.addTransactionListener(this);

    final var collectedDoc = EngineMetricsDoc.PROCESS_CACHE_COLLECTED;
    final var collectedCounter =
        Counter.builder(collectedDoc.getName())
            .description(collectedDoc.getDescription())
            .register(meterRegistry);

    processByTenantAndKeyCache =
        newProcessCacheBuilder(config)
            // both caches hold the same values, so counting one avoids double-counting
            .<TenantIdAndProcessDefinitionKey, DeployedProcess>removalListener(
                notification -> {
                  if (notification.getCause() == RemovalCause.COLLECTED) {
                    collectedCounter.increment();
                  }
                })
            .build();
    processesByTenantAndProcessIdAndVersionCache = newProcessCacheBuilder(config).build();
    processDefinitionKeyByTenantAndProcessIdAndDeploymentKeyCache =
        CacheBuilder.newBuilder().maximumSize(config.getProcessCacheCapacity()).build();
  }

  private static CacheBuilder<Object, Object> newProcessCacheBuilder(
      final EngineConfiguration config) {
    final var builder = CacheBuilder.newBuilder().maximumSize(config.getProcessCacheCapacity());
    // soft values: a cached process is a large object graph (parsed model, compiled FEEL
    // expressions) whose size we can't weigh, so let the GC reclaim entries under heap pressure
    return config.isProcessCacheSoftValues() ? builder.softValues() : builder;
  }

  /** Returns the process with the given key, loading it on a miss; {@code null} if none exists. */
  public @Nullable DeployedProcess getProcessByKey(
      final String tenantId, final long processDefinitionKey) {
    final var cachedProcess =
        processByTenantAndKeyCache.getIfPresent(
            new TenantIdAndProcessDefinitionKey(tenantId, processDefinitionKey));
    if (cachedProcess != null) {
      return cachedProcess;
    }

    final var persistedProcess = loader.findProcessByKey(tenantId, processDefinitionKey);
    return persistedProcess != null ? cache(persistedProcess) : null;
  }

  /** Returns the given version of a process, loading it on a miss; {@code null} if none exists. */
  public @Nullable DeployedProcess getProcessByProcessIdAndVersion(
      final String tenantId, final DirectBuffer bpmnProcessId, final long version) {
    final var cachedProcess =
        processesByTenantAndProcessIdAndVersionCache.getIfPresent(
            new TenantIdAndProcessIdAndVersion(tenantId, bpmnProcessId, version));
    if (cachedProcess != null) {
      return cachedProcess;
    }

    final var persistedProcess =
        loader.findProcessByProcessIdAndVersion(tenantId, bpmnProcessId, version);
    return persistedProcess != null ? cache(persistedProcess) : null;
  }

  /**
   * Returns the process with the given id deployed by the given deployment, loading it on a miss;
   * {@code null} if there is none.
   */
  public @Nullable DeployedProcess getProcessByProcessIdAndDeploymentKey(
      final String tenantId, final DirectBuffer bpmnProcessId, final long deploymentKey) {
    final var key =
        new TenantIdAndProcessIdAndDeploymentKey(tenantId, bpmnProcessId, deploymentKey);
    var processDefinitionKey =
        processDefinitionKeyByTenantAndProcessIdAndDeploymentKeyCache.getIfPresent(key);

    if (processDefinitionKey == null) {
      processDefinitionKey =
          loader.findProcessDefinitionKeyByDeploymentKey(tenantId, bpmnProcessId, deploymentKey);
      if (processDefinitionKey == null) {
        return null;
      }
      processDefinitionKeyByTenantAndProcessIdAndDeploymentKeyCache.put(key, processDefinitionKey);
      deploymentKeysCachedInTransaction.add(key);
    }

    return getProcessByKey(tenantId, processDefinitionKey);
  }

  /**
   * Transforms the given persisted process and caches the result, replacing any cached version of
   * it. The store calls this after it changed a process, to keep the cache up to date.
   *
   * @param persistedProcess the persisted process; it is copied, so it may be backed by a reused
   *     buffer
   * @return the cached process
   */
  public DeployedProcess cache(final PersistedProcess persistedProcess) {
    final PersistedProcess copiedProcess = copy(persistedProcess);

    final BpmnModelInstance modelInstance =
        readModelInstanceFromBuffer(copiedProcess.getResource());
    // pin the pipeline to the versions frozen at deploy time (absent slots resolve to v1)
    final List<ExecutableProcess> definitions =
        transformer.transformDefinitions(modelInstance, copiedProcess.getTransformerVersions());

    final ExecutableProcess executableProcess =
        definitions.stream()
            .filter(process -> BufferUtil.equals(copiedProcess.getBpmnProcessId(), process.getId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        String.format(
                            "Expected to find executable process in persisted process with key '%s',"
                                + " but after transformation no such executable process could be found.",
                            copiedProcess.getKey())));

    final DeployedProcess deployedProcess = new DeployedProcess(executableProcess, copiedProcess);
    put(deployedProcess);
    return deployedProcess;
  }

  /**
   * Caches an already transformed process for a process that was just persisted, so the next lookup
   * doesn't need to parse and transform the resource again.
   *
   * @see io.camunda.zeebe.engine.state.immutable.ProcessState#cacheProcess(long, String,
   *     ExecutableProcess)
   */
  public void cache(
      final String tenantId,
      final long processDefinitionKey,
      final ExecutableProcess executableProcess) {
    final var persistedProcess = loader.findProcessByKey(tenantId, processDefinitionKey);
    if (persistedProcess == null) {
      throw new IllegalStateException(
          "Expected to cache process with key '%d' for tenant '%s', but it is not persisted"
              .formatted(processDefinitionKey, tenantId));
    }
    if (!BufferUtil.equals(executableProcess.getId(), persistedProcess.getBpmnProcessId())) {
      throw new IllegalStateException(
          "Expected to cache process with key '%d' for tenant '%s' and bpmnProcessId '%s', but the given executable process has bpmnProcessId '%s'"
              .formatted(
                  processDefinitionKey,
                  tenantId,
                  bufferAsString(persistedProcess.getBpmnProcessId()),
                  bufferAsString(executableProcess.getId())));
    }

    put(new DeployedProcess(executableProcess, copy(persistedProcess)));
  }

  /** Removes a process from the cache. The store calls this after it changed or deleted it. */
  public void invalidate(
      final String tenantId,
      final DirectBuffer bpmnProcessId,
      final long processDefinitionKey,
      final long deploymentKey,
      final int version) {
    processesByTenantAndProcessIdAndVersionCache.invalidate(
        new TenantIdAndProcessIdAndVersion(tenantId, bpmnProcessId, version));
    processByTenantAndKeyCache.invalidate(
        new TenantIdAndProcessDefinitionKey(tenantId, processDefinitionKey));
    processDefinitionKeyByTenantAndProcessIdAndDeploymentKeyCache.invalidate(
        new TenantIdAndProcessIdAndDeploymentKey(tenantId, bpmnProcessId, deploymentKey));
  }

  public void invalidateAll() {
    processByTenantAndKeyCache.invalidateAll();
    processesByTenantAndProcessIdAndVersionCache.invalidateAll();
    processDefinitionKeyByTenantAndProcessIdAndDeploymentKeyCache.invalidateAll();
  }

  @Override
  public void onCommit() {
    forgetEntriesCachedInTransaction();
  }

  @Override
  public void onRollback() {
    processByTenantAndKeyCache.invalidateAll(keysCachedInTransaction);
    processesByTenantAndProcessIdAndVersionCache.invalidateAll(versionsCachedInTransaction);
    processDefinitionKeyByTenantAndProcessIdAndDeploymentKeyCache.invalidateAll(
        deploymentKeysCachedInTransaction);
    forgetEntriesCachedInTransaction();
  }

  private void forgetEntriesCachedInTransaction() {
    keysCachedInTransaction.clear();
    versionsCachedInTransaction.clear();
    deploymentKeysCachedInTransaction.clear();
  }

  private void put(final DeployedProcess deployedProcess) {
    final var key =
        new TenantIdAndProcessDefinitionKey(
            deployedProcess.getTenantId(), deployedProcess.getKey());
    processByTenantAndKeyCache.put(key, deployedProcess);
    keysCachedInTransaction.add(key);

    final var version =
        new TenantIdAndProcessIdAndVersion(
            deployedProcess.getTenantId(),
            deployedProcess.getBpmnProcessId(),
            deployedProcess.getVersion());
    processesByTenantAndProcessIdAndVersionCache.put(version, deployedProcess);
    versionsCachedInTransaction.add(version);
  }

  private static PersistedProcess copy(final PersistedProcess persistedProcess) {
    final byte[] bytes = new byte[persistedProcess.getLength()];
    final MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
    persistedProcess.write(buffer, 0);

    final PersistedProcess copiedProcess = new PersistedProcess();
    copiedProcess.wrap(buffer, 0, persistedProcess.getLength());
    return copiedProcess;
  }

  private static BpmnModelInstance readModelInstanceFromBuffer(final DirectBuffer buffer) {
    try (final DirectBufferInputStream stream = new DirectBufferInputStream(buffer)) {
      return Bpmn.readModelFromStream(stream);
    }
  }

  private record TenantIdAndProcessIdAndVersion(
      String tenantId, DirectBuffer processId, long version) {}

  private record TenantIdAndProcessDefinitionKey(String tenantId, long processDefinitionKey) {}

  private record TenantIdAndProcessIdAndDeploymentKey(
      String tenantId, DirectBuffer processId, long deploymentKey) {}
}
