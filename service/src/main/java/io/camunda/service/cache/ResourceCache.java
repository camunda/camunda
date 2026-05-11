/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.search.entities.DeployedResourceEntity;
import io.camunda.zeebe.broker.client.api.BrokerTopologyListener;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.util.cache.CaffeineCacheStatsCounter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resource cache uses a Caffeine {@link Cache} to store deployed resource key and {@link
 * DeployedResourceEntity} entries.
 *
 * <p>Use the {@link ResourceCache#get(long)} method to retrieve a cached resource by its key, or
 * {@link ResourceCache#put(long, DeployedResourceEntity)} to store a resource in the cache.
 */
public class ResourceCache {

  public static final String NAMESPACE = "camunda.gateway.rest.cache";
  private static final Logger LOGGER = LoggerFactory.getLogger(ResourceCache.class);
  private final Cache<Long, DeployedResourceEntity> cache;

  public ResourceCache(
      final Configuration configuration,
      final BrokerTopologyManager brokerTopologyManager,
      final MeterRegistry meterRegistry) {

    final var statsCounter = new CaffeineCacheStatsCounter(NAMESPACE, "resource", meterRegistry);
    final var cacheBuilder =
        Caffeine.newBuilder().maximumSize(configuration.maxSize()).recordStats(() -> statsCounter);
    final var expirationIdle = configuration.expirationIdleMillis();
    if (expirationIdle != null && expirationIdle > 0) {
      cacheBuilder.expireAfterAccess(expirationIdle, TimeUnit.MILLISECONDS);
    }
    cache = cacheBuilder.build();

    brokerTopologyManager.addTopologyListener(new ResourceCacheInvalidator(this));
  }

  public DeployedResourceEntity get(final long resourceKey) {
    return cache.getIfPresent(resourceKey);
  }

  public void put(final long resourceKey, final DeployedResourceEntity entity) {
    cache.put(resourceKey, entity);
  }

  public void invalidate() {
    cache.invalidateAll();
  }

  public Cache<Long, DeployedResourceEntity> getRawCache() {
    return cache;
  }

  public record Configuration(long maxSize, Long expirationIdleMillis) {
    static Configuration getDefault() {
      return new Configuration(100, null);
    }
  }

  private final class ResourceCacheInvalidator implements BrokerTopologyListener {
    private final ResourceCache cache;

    public ResourceCacheInvalidator(final ResourceCache cache) {
      this.cache = cache;
    }

    @Override
    public void clusterIncarnationChanged() {
      cache.invalidate();
      LOGGER.debug("Resource cache invalidated");
    }
  }
}
