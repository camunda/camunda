/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.identity;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.tenants.dto.Tenant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public class IdentityTenantService {

  private final RejectedExecutionException ree;

  private final LoadingCache<String, List<Tenant>> tenantCache;
  private final Semaphore semaphore;
  private final Identity identity;

  private final int semaphoreCapacity = 300;
  private final long semaphoreTimeout = 1;
  private final long cacheSize = 1000;
  private final long cacheTtl = 5000;

  public IdentityTenantService(final Identity identity) {
    this.identity = identity;
    semaphore = new Semaphore(semaphoreCapacity);
    tenantCache =
        CacheBuilder.newBuilder()
            .expireAfterWrite(cacheTtl, TimeUnit.MILLISECONDS)
            .maximumSize(cacheSize)
            .build(
                new CacheLoader<>() {
                  @Override
                  public @NotNull List<Tenant> load(final @NotNull String token) {
                    return getTenantsForTokenInternal(token);
                  }
                });
    ree =
        new RejectedExecutionException(
            String.format(
                "Not able to fetch tenants from Identity in %d%s",
                semaphoreTimeout, TimeUnit.SECONDS));
  }

  public List<Tenant> getTenantsForToken(final String token) throws ExecutionException {
    return tenantCache.get(token);
  }

  private List<Tenant> getTenantsForTokenInternal(final String token) {
    try {
      if (!semaphore.tryAcquire(semaphoreTimeout, TimeUnit.SECONDS)) {
        throw ree;
      }
      return identity.tenants().forToken(token);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw ree;
    } finally {
      semaphore.release();
    }
  }
}
