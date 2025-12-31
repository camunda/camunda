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
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Process cache uses a Caffeine {@link LoadingCache} to store process definition key and {@link
 * ProcessCacheItem} entries.
 *
 * <p>Use the {@link ProcessCache#getCacheItem(long)} method to load one item or the {@link
 * ProcessCache#getCacheItems(Set)} method to load multiple cache items at once.
 */
public class ProcessCache {

  public static final String NAMESPACE = "camunda.gateway.rest.cache";
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessCache.class);
  private final LoadingCache<Long, ProcessCacheItem> cache;
  private final ProcessDefinitionProvider processDefinitionProvider;

  public ProcessCache(
      final Configuration configuration,
      final ProcessDefinitionServices processDefinitionServices,
      final BrokerTopologyManager brokerTopologyManager,
      final MeterRegistry meterRegistry) {
    processDefinitionProvider = new ProcessDefinitionProvider(processDefinitionServices);

    final var statsCounter = new CaffeineCacheStatsCounter(NAMESPACE, "process", meterRegistry);
    final var cacheBuilder =
        Caffeine.newBuilder().maximumSize(configuration.maxSize()).recordStats(() -> statsCounter);
    final var expirationIdle = configuration.expirationIdleMillis();
    if (expirationIdle != null && expirationIdle > 0) {
      cacheBuilder.expireAfterAccess(expirationIdle, TimeUnit.MILLISECONDS);
    }
    cache = cacheBuilder.build(new ProcessCacheLoader());

    brokerTopologyManager.addTopologyListener(new ProcessCacheInvalidator(this));
  }

  public ProcessCacheItem getCacheItem(final long processDefinitionKey) {
    return cache.get(processDefinitionKey);
  }

  public ProcessCacheResult getCacheItems(final Set<Long> processDefinitionKeys) {
    return new ProcessCacheResult(cache.getAll(processDefinitionKeys));
  }

  public void invalidate() {
    cache.invalidateAll();
  }

  public LoadingCache<Long, ProcessCacheItem> getRawCache() {
    return cache;
  }

  public record Configuration(long maxSize, Long expirationIdleMillis) {
    static Configuration getDefault() {
      return new Configuration(100, null);
    }
  }

  private final class ProcessCacheLoader implements CacheLoader<Long, ProcessCacheItem> {

    @Override
    public ProcessCacheItem load(final Long processDefinitionKey) {
      final var processData = processDefinitionProvider.extractProcessData(processDefinitionKey);
      return ProcessCacheItem.from(processData);
    }

    @Override
    public Map<Long, ProcessCacheItem> loadAll(final Set<? extends Long> processDefinitionKeys) {
      final var processDataMap =
          processDefinitionProvider.extractProcessData((Set<Long>) processDefinitionKeys);
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
