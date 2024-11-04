/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.identity;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.zeebe.gateway.cmd.ConcurrentRequestException;
import io.camunda.zeebe.gateway.impl.configuration.IdentityServiceCfg;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class IdentityTenantService {

  private static final TimeUnit TIME_UNIT = TimeUnit.MILLISECONDS;

  private final LoadingCache<String, List<Tenant>> tenantCache;
  private final Semaphore semaphore;
  private final Identity identity;

  private final boolean isCachingEnabled;
  private final long tenantRequestTimeout;

  public IdentityTenantService(final Identity identity, final IdentityServiceCfg config) {
    this.identity = identity;
    isCachingEnabled = config.isEnabled();
    tenantRequestTimeout = config.getTenantRequestTimeout();
    semaphore = new Semaphore(config.getTenantRequestCapacity());
    tenantCache =
        CacheBuilder.newBuilder()
            .expireAfterWrite(config.getTenantCacheTtl(), TIME_UNIT)
            .maximumSize(config.getTenantCacheSize())
            .build(
                new CacheLoader<>() {
                  @Override
                  public List<Tenant> load(final String token) {
                    return getTenantsForTokenThrottled(token);
                  }
                });
  }

  public List<Tenant> getTenantsForToken(final String token) throws ExecutionException {
    if (!isCachingEnabled) {
      return getTenantsForTokenInternal(token);
    }
    return tenantCache.get(token);
  }

  private List<Tenant> getTenantsForTokenThrottled(final String token) {
    try {
      if (!semaphore.tryAcquire(tenantRequestTimeout, TIME_UNIT)) {
        throw new ConcurrentRequestException(tenantRequestTimeout, TIME_UNIT);
      }
      return getTenantsForTokenInternal(token);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(
          "Expected to fetch tenants from Identity, but the request was interrupted", e);
    } finally {
      semaphore.release();
    }
  }

  private List<Tenant> getTenantsForTokenInternal(final String token) {
    return identity.tenants().forToken(token);
  }
}
