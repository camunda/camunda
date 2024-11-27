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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ProcessCache implements CacheLoader<Long, ProcessCacheItem> {

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
    cache = cacheBuilder.build(this);
  }

  @Override
  public ProcessCacheItem load(final Long key) throws Exception {
    final var flowNodes = new ConcurrentHashMap<String, String>();
    xmlUtil.extractFlowNodeNames(key, (pdId, node) -> flowNodes.put(node.id(), node.name()));
    return new ProcessCacheItem(key, flowNodes);
  }

  @Override
  public Map<Long, ProcessCacheItem> loadAll(final Set<? extends Long> keys) {
    final var processMap = new HashMap<Long, ProcessCacheItem>();
    xmlUtil.extractFlowNodeNames(
        (Set<Long>) keys,
        (pdId, node) -> {
          final var flowNodeMap =
              processMap.computeIfAbsent(
                  pdId, key -> new ProcessCacheItem(key, new ConcurrentHashMap<>()));
          flowNodeMap.putFlowNode(node.id(), node.name());
        });
    return processMap;
  }

  public ProcessCacheItem getCacheItem(final long processDefinitionKey) {
    return cache.get(processDefinitionKey);
  }

  public Map<Long, ProcessCacheItem> getCacheItems(final List<Long> processDefinitionKeys) {
    return cache.getAll(processDefinitionKeys);
  }

  public String getUserTaskName(final UserTaskEntity userTask) {
    return getCacheItem(userTask.processDefinitionKey())
        .flowNodes()
        .getOrDefault(userTask.elementId(), userTask.elementId());
  }

  public String getFlowNodeName(final FlowNodeInstanceEntity flowNode) {
    return getCacheItem(flowNode.processDefinitionKey())
        .flowNodes()
        .getOrDefault(flowNode.flowNodeId(), flowNode.flowNodeId());
  }

  protected LoadingCache<Long, ProcessCacheItem> getCache() {
    return cache;
  }
}
