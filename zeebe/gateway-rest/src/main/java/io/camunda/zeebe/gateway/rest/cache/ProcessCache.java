/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.zeebe.broker.client.api.BrokerTopologyListener;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.util.ProcessElementProvider;
import io.camunda.zeebe.util.cache.CaffeineCacheStatsCounter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
 *
 * <p>The process cache default configuration can be changed via the {@link
 * GatewayRestConfiguration.ProcessCacheConfiguration} properties.
 */
public class ProcessCache {

  public static final String NAMESPACE = "camunda.gateway.rest.cache";
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessCache.class);
  private final LoadingCache<Long, ProcessCacheItem> cache;
  private final ProcessElementProvider processElementProvider;

  public ProcessCache(
      final GatewayRestConfiguration configuration,
      final ProcessElementProvider processElementProvider,
      final BrokerTopologyManager brokerTopologyManager,
      final MeterRegistry meterRegistry) {
    this.processElementProvider = processElementProvider;
    final var statsCounter = new CaffeineCacheStatsCounter(NAMESPACE, "process", meterRegistry);
    final var cacheBuilder =
        Caffeine.newBuilder()
            .maximumSize(configuration.getProcessCache().getMaxSize())
            .recordStats(() -> statsCounter);
    final var expirationIdle = configuration.getProcessCache().getExpirationIdleMillis();
    if (expirationIdle != null && expirationIdle > 0) {
      cacheBuilder.expireAfterAccess(expirationIdle, TimeUnit.MILLISECONDS);
    }
    cache = cacheBuilder.build(new ProcessCacheLoader());

    brokerTopologyManager.addTopologyListener(new ProcessCacheInvalidator(this));
  }

  public ProcessCacheItem getCacheItem(final long processDefinitionKey) {
    return cache.get(processDefinitionKey);
  }

  public Map<Long, ProcessCacheItem> getCacheItems(final Set<Long> processDefinitionKeys) {
    return cache.getAll(processDefinitionKeys);
  }

  public Map<Long, ProcessCacheItem> getUserTaskNames(final List<UserTaskEntity> items) {
    return getCacheItems(
        items.stream().map(UserTaskEntity::processDefinitionKey).collect(Collectors.toSet()));
  }

  public String getUserTaskName(final UserTaskEntity userTask) {
    return getCacheItem(userTask.processDefinitionKey()).getElementName(userTask.elementId());
  }

  public Map<Long, ProcessCacheItem> getElementNames(final List<FlowNodeInstanceEntity> items) {
    return getCacheItems(
        items.stream()
            .map(FlowNodeInstanceEntity::processDefinitionKey)
            .collect(Collectors.toSet()));
  }

  public String getElementName(final FlowNodeInstanceEntity element) {
    return getCacheItem(element.processDefinitionKey()).getElementName(element.flowNodeId());
  }

  public void invalidate() {
    cache.invalidateAll();
    LOGGER.debug("Cache invalidated");
  }

  private final class ProcessCacheLoader implements CacheLoader<Long, ProcessCacheItem> {

    @Override
    public ProcessCacheItem load(final Long processDefinitionKey) {
      final var elements = new HashMap<String, String>();
      processElementProvider.extractElementNames(
          processDefinitionKey, (pdKey, node) -> elements.put(node.id(), node.name()));
      return new ProcessCacheItem(Collections.unmodifiableMap(elements));
    }

    @Override
    public Map<Long, ProcessCacheItem> loadAll(final Set<? extends Long> processDefinitionKeys) {
      final var processMap = new HashMap<Long, Map<String, String>>();
      processElementProvider.extractElementNames(
          (Set<Long>) processDefinitionKeys,
          (pdKey, element) -> {
            final var elementMap = processMap.computeIfAbsent(pdKey, key -> new HashMap<>());
            elementMap.put(element.id(), element.name());
          });
      return processMap.entrySet().stream()
          .collect(
              Collectors.toMap(
                  Map.Entry::getKey,
                  entry -> new ProcessCacheItem(Collections.unmodifiableMap(entry.getValue()))));
    }
  }

  private final class ProcessCacheInvalidator implements BrokerTopologyListener {
    private final ProcessCache cache;

    public ProcessCacheInvalidator(final ProcessCache cache) {
      this.cache = cache;
    }

    @Override
    public void completedClusterChange() {
      cache.invalidate();
    }
  }
}
