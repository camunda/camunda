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
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.util.XmlUtil;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

  private final LoadingCache<Long, ProcessCacheItem> cache;
  private final XmlUtil xmlUtil;

  public ProcessCache(final GatewayRestConfiguration configuration, final XmlUtil xmlUtil) {
    this.xmlUtil = xmlUtil;
    final var cacheBuilder =
        Caffeine.newBuilder().maximumSize(configuration.getProcessCache().getMaxSize());
    final var ttlMillis = configuration.getProcessCache().getExpirationMillis();
    if (ttlMillis != null && ttlMillis > 0) {
      cacheBuilder.expireAfterAccess(ttlMillis, TimeUnit.MILLISECONDS);
    }
    cache = cacheBuilder.build(new ProcessCacheLoader());
  }

  public ProcessCacheItem getCacheItem(final long processDefinitionKey) {
    return cache.get(processDefinitionKey);
  }

  public ProcessCacheItems getCacheItems(final Set<Long> processDefinitionKeys) {
    return new ProcessCacheItems(cache.getAll(processDefinitionKeys));
  }

  public ProcessCacheItems getUserTaskNames(final List<UserTaskEntity> items) {
    return getCacheItems(
        items.stream().map(UserTaskEntity::processDefinitionKey).collect(Collectors.toSet()));
  }

  public String getUserTaskName(final UserTaskEntity userTask) {
    return getCacheItem(userTask.processDefinitionKey()).getFlowNodeName(userTask.elementId());
  }

  public ProcessCacheItems getFlowNodeNames(final List<FlowNodeInstanceEntity> items) {
    return getCacheItems(
        items.stream()
            .map(FlowNodeInstanceEntity::processDefinitionKey)
            .collect(Collectors.toSet()));
  }

  public String getFlowNodeName(final FlowNodeInstanceEntity flowNode) {
    return getCacheItem(flowNode.processDefinitionKey()).getFlowNodeName(flowNode.flowNodeId());
  }

  private final class ProcessCacheLoader implements CacheLoader<Long, ProcessCacheItem> {

    @Override
    public ProcessCacheItem load(final Long processDefinitionKey) {
      final var flowNodes = new HashMap<String, String>();
      xmlUtil.extractFlowNodeNames(
          processDefinitionKey, (pdKey, node) -> flowNodes.put(node.id(), node.name()));
      return new ProcessCacheItem(Collections.unmodifiableMap(flowNodes));
    }

    @Override
    public Map<Long, ProcessCacheItem> loadAll(final Set<? extends Long> processDefinitionKeys) {
      final var processMap = new HashMap<Long, Map<String, String>>();
      xmlUtil.extractFlowNodeNames(
          (Set<Long>) processDefinitionKeys,
          (pdKey, flowNode) -> {
            final var flowNodeMap = processMap.computeIfAbsent(pdKey, key -> new HashMap<>());
            flowNodeMap.put(flowNode.id(), flowNode.name());
          });
      return processMap.entrySet().stream()
          .collect(
              Collectors.toMap(
                  Map.Entry::getKey,
                  entry -> new ProcessCacheItem(Collections.unmodifiableMap(entry.getValue()))));
    }
  }
}
