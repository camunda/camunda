/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.broker.client.api.BrokerTopologyListener;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.util.cache.CaffeineCacheStatsCounter;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Process cache uses a Caffeine {@link LoadingCache} to store process definition key and {@link
 * ProcessCacheItem} entries.
 *
 * <p>Use the {@link ProcessCache#getCacheItem(long, String)} method to load one item or the {@link
 * ProcessCache#getCacheItems(Set, String)} method to load multiple cache items at once.
 */
public class ProcessCache {

  public static final String NAMESPACE = "camunda.gateway.rest.cache";
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessCache.class);
  private static final String PHYSICAL_TENANT_ID_TAG = "physicalTenantId";
  private final ConcurrentHashMap<String, LoadingCache<Long, ProcessCacheItem>>
      cachesByPhysicalTenant;
  private final ProcessDefinitionProvider processDefinitionProvider;
  private final Configuration configuration;
  private final MeterRegistry meterRegistry;

  public ProcessCache(
      final Configuration configuration,
      final ProcessDefinitionServices processDefinitionServices,
      final BrokerTopologyManager brokerTopologyManager,
      final MeterRegistry meterRegistry) {
    this.configuration = configuration;
    processDefinitionProvider = new ProcessDefinitionProvider(processDefinitionServices);
    cachesByPhysicalTenant = new ConcurrentHashMap<>();
    this.meterRegistry = meterRegistry;

    brokerTopologyManager.addTopologyListener(new ProcessCacheInvalidator(this));
  }

  public ProcessCacheItem getCacheItem(
      final long processDefinitionKey, final String physicalTenantId) {
    return getOrCreateTenantCache(physicalTenantId).get(processDefinitionKey);
  }

  public ProcessCacheResult getCacheItems(
      final Set<Long> processDefinitionKeys, final String physicalTenantId) {
    return new ProcessCacheResult(
        getOrCreateTenantCache(physicalTenantId).getAll(processDefinitionKeys));
  }

  public void invalidate() {
    cachesByPhysicalTenant.values().forEach(LoadingCache::invalidateAll);
  }

  public LoadingCache<Long, ProcessCacheItem> getRawCache(final String physicalTenantId) {
    return getOrCreateTenantCache(physicalTenantId);
  }

  private LoadingCache<Long, ProcessCacheItem> getOrCreateTenantCache(
      final String physicalTenantId) {
    return cachesByPhysicalTenant.computeIfAbsent(physicalTenantId, this::buildTenantCache);
  }

  private LoadingCache<Long, ProcessCacheItem> buildTenantCache(final String physicalTenantId) {
    final var tenantRegistry =
        MicrometerUtil.wrap(meterRegistry, Tags.of(PHYSICAL_TENANT_ID_TAG, physicalTenantId));
    final var tenantStatsCounter =
        new CaffeineCacheStatsCounter(NAMESPACE, "process", tenantRegistry);
    final var cacheBuilder =
        Caffeine.newBuilder()
            .maximumSize(configuration.maxSize())
            .recordStats(() -> tenantStatsCounter);
    final var expirationIdle = configuration.expirationIdleMillis();
    if (expirationIdle != null && expirationIdle > 0) {
      cacheBuilder.expireAfterAccess(expirationIdle, TimeUnit.MILLISECONDS);
    }
    return cacheBuilder.build(new ProcessCacheLoader(physicalTenantId));
  }

  public record Configuration(long maxSize, Long expirationIdleMillis) {
    static Configuration getDefault() {
      return new Configuration(100, null);
    }
  }

  private final class ProcessCacheLoader implements CacheLoader<Long, ProcessCacheItem> {

    private final String physicalTenantId;

    ProcessCacheLoader(final String physicalTenantId) {
      this.physicalTenantId = physicalTenantId;
    }

    @Override
    public ProcessCacheItem load(final Long processDefinitionKey) {
      final var processData =
          processDefinitionProvider.extractProcessData(processDefinitionKey, physicalTenantId);
      return ProcessCacheItem.from(processData);
    }

    @Override
    public Map<Long, ProcessCacheItem> loadAll(final Set<? extends Long> processDefinitionKeys) {
      final var processDataMap =
          processDefinitionProvider.extractProcessData(
              (Set<Long>) processDefinitionKeys, physicalTenantId);
      return processDataMap.entrySet().stream()
          .collect(
              Collectors.toMap(
                  Map.Entry::getKey, entry -> ProcessCacheItem.from(entry.getValue())));
    }
  }

  private final class ProcessCacheInvalidator implements BrokerTopologyListener {
    private final ProcessCache cache;

    public ProcessCacheInvalidator(final ProcessCache cache) {
      this.cache = cache;
    }

    @Override
    public void clusterIncarnationChanged() {
      cache.invalidate();
      LOGGER.debug("Process cache invalidated");
    }
  }
}
