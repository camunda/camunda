/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.BatchOperationDbReader;
import io.camunda.db.rdbms.read.service.DecisionRequirementsDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionDbReader;
import io.camunda.exporter.rdbms.ExporterConfiguration;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.zeebe.exporter.common.cache.BulkExporterEntityCacheLoader;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCacheImpl;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.exporter.common.cache.decisionRequirements.CachedDecisionRequirementsEntity;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.common.utils.ProcessCacheUtil;
import io.camunda.zeebe.util.cache.CaffeineCacheStatsCounter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.List;

/**
 * Central registry for the RDBMS exporter caches.
 *
 * <p>The registry owns cache construction, metric naming, and typed access to each cache used by
 * the exporter. To add a new cache, register it once here and expose it through a dedicated
 * accessor.
 */
public final class RdbmsCacheRegistry {

  private enum CacheId {
    PROCESS,
    DECISION_REQUIREMENTS,
    BATCH_OPERATION
  }

  private static final String CACHE_METRICS_NAMESPACE = "camunda.rdbms.exporter.cache";

  private final EnumMap<CacheId, ExporterEntityCache<?, ?>> caches = new EnumMap<>(CacheId.class);
  private final MeterRegistry meterRegistry;

  public RdbmsCacheRegistry(
      final ExporterConfiguration config,
      final RdbmsService rdbmsService,
      final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;

    register(
        CacheId.PROCESS,
        config.getProcessCache().getMaxSize(),
        "process",
        processLoader(rdbmsService));
    register(
        CacheId.DECISION_REQUIREMENTS,
        config.getDecisionRequirementsCache().getMaxSize(),
        "decisionRequirements",
        decisionRequirementsLoader(rdbmsService));
    register(
        CacheId.BATCH_OPERATION,
        config.getBatchOperationCache().getMaxSize(),
        "batchOperation",
        batchOperationLoader(rdbmsService));
  }

  /** Returns the cache for process definitions and derived process diagram metadata. */
  public ExporterEntityCache<Long, CachedProcessEntity> processCache() {
    return get(CacheId.PROCESS);
  }

  /** Returns the cache for decision requirements definitions. */
  public ExporterEntityCache<Long, CachedDecisionRequirementsEntity> decisionRequirementsCache() {
    return get(CacheId.DECISION_REQUIREMENTS);
  }

  /** Returns the cache for batch operation metadata. */
  public ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache() {
    return get(CacheId.BATCH_OPERATION);
  }

  private <K, T> void register(
      final CacheId cacheId,
      final long maxSize,
      final String metricsName,
      final CacheLoader<K, T> cacheLoader) {
    caches.put(
        cacheId,
        new ExporterEntityCacheImpl<>(
            maxSize,
            cacheLoader,
            new CaffeineCacheStatsCounter(CACHE_METRICS_NAMESPACE, metricsName, meterRegistry)));
  }

  private <K, T> void register(
      final CacheId cacheId,
      final long maxSize,
      final String metricsName,
      final BulkExporterEntityCacheLoader<K, T> cacheLoader) {
    caches.put(
        cacheId,
        new ExporterEntityCacheImpl<>(
            maxSize,
            cacheLoader,
            new CaffeineCacheStatsCounter(CACHE_METRICS_NAMESPACE, metricsName, meterRegistry)));
  }

  @SuppressWarnings("unchecked")
  private <K, T> ExporterEntityCache<K, T> get(final CacheId cacheId) {
    return (ExporterEntityCache<K, T>) caches.get(cacheId);
  }

  private BulkExporterEntityCacheLoader<Long, CachedProcessEntity> processLoader(
      final RdbmsService rdbmsService) {
    final ProcessDefinitionDbReader reader = rdbmsService.getProcessDefinitionReader();
    return new RdbmsEntityCacheLoader<>(
        "Process",
        reader::findOne,
        keys ->
            ProcessDefinitionQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(List.copyOf(keys)))
                        .resultConfig(c -> c.includeXml(true))),
        query -> reader.search(query).items(),
        ProcessDefinitionEntity::processDefinitionKey,
        RdbmsCacheRegistry::toCachedProcessEntity);
  }

  private BulkExporterEntityCacheLoader<Long, CachedDecisionRequirementsEntity>
      decisionRequirementsLoader(final RdbmsService rdbmsService) {
    final DecisionRequirementsDbReader reader = rdbmsService.getDecisionRequirementsReader();
    return new RdbmsEntityCacheLoader<>(
        "DecisionRequirements",
        reader::findOne,
        keys ->
            DecisionRequirementsQuery.of(
                b -> b.filter(f -> f.decisionRequirementsKeys(List.copyOf(keys)))),
        query -> reader.search(query).items(),
        DecisionRequirementsEntity::decisionRequirementsKey,
        entity ->
            new CachedDecisionRequirementsEntity(
                entity.decisionRequirementsKey(), entity.name(), entity.version()));
  }

  private CacheLoader<String, CachedBatchOperationEntity> batchOperationLoader(
      final RdbmsService rdbmsService) {
    final BatchOperationDbReader reader = rdbmsService.getBatchOperationReader();
    return new RdbmsEntityCacheLoader<>(
        "BatchOperation",
        reader::findOne,
        entity -> entity.batchOperationKey(),
        entity ->
            new CachedBatchOperationEntity(entity.batchOperationKey(), entity.operationType()));
  }

  private static CachedProcessEntity toCachedProcessEntity(
      final ProcessDefinitionEntity processDefinitionEntity) {
    final var processDiagramData =
        ProcessCacheUtil.extractProcessDiagramData(processDefinitionEntity);
    return new CachedProcessEntity(
        processDefinitionEntity.name(),
        processDefinitionEntity.version(),
        processDefinitionEntity.versionTag(),
        processDiagramData.callActivityIds(),
        processDiagramData.flowNodesMap(),
        processDiagramData.hasUserTasks());
  }
}
