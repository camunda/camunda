/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.spring.utils.PhysicalTenantPropagatingExecutorService;
import java.util.Objects;
import java.util.concurrent.*;

public final class ApiServicesExecutorProvider {

  private static final String API_SERVICE_THREAD_NAME = "api-service-thread-";

  private final ExecutorService executor;

  public ApiServicesExecutorProvider(
      final int corePoolSizeMultiplier,
      final int maxPoolSizeMultiplier,
      final long keepAliveSeconds,
      final int queueCapacity) {
    this(
        corePoolSizeMultiplier,
        maxPoolSizeMultiplier,
        keepAliveSeconds,
        queueCapacity,
        0,
        0,
        0,
        0,
        Integer.MAX_VALUE,
        Integer.MAX_VALUE,
        Integer.MAX_VALUE);
  }

  /**
   * @param physicalTenantCount total number of physical tenants configured on this node; each
   *     tenant beyond the first adds an independent slice of headroom on top of the vCPU-derived
   *     terms (see {@link #create}).
   */
  public ApiServicesExecutorProvider(
      final int corePoolSizeMultiplier,
      final int maxPoolSizeMultiplier,
      final long keepAliveSeconds,
      final int queueCapacity,
      final int physicalTenantCount,
      final int corePoolSizePerTenant,
      final int maxPoolSizePerTenant,
      final int queueCapacityPerTenant,
      final int corePoolSizeCeiling,
      final int maxPoolSizeCeiling,
      final int queueCapacityCeiling) {
    executor =
        new PhysicalTenantPropagatingExecutorService(
            Objects.requireNonNull(
                create(
                    corePoolSizeMultiplier,
                    maxPoolSizeMultiplier,
                    keepAliveSeconds,
                    queueCapacity,
                    Math.max(0, physicalTenantCount - 1),
                    corePoolSizePerTenant,
                    maxPoolSizePerTenant,
                    queueCapacityPerTenant,
                    corePoolSizeCeiling,
                    maxPoolSizeCeiling,
                    queueCapacityCeiling),
                "REST API Executor Service must not be null"));
  }

  /**
   * Pass a raw (unwrapped) executor: the provider adds the physical-tenant propagating decorator
   * itself, so a pre-wrapped executor would only get a redundant second layer.
   */
  public ApiServicesExecutorProvider(final ExecutorService executor) {
    this.executor =
        new PhysicalTenantPropagatingExecutorService(
            Objects.requireNonNull(executor, "REST API Executor Service must not be null"));
  }

  public ExecutorService getExecutor() {
    return executor;
  }

  /**
   * Create a customizable dynamic ThreadPoolExecutor.
   *
   * <p>Pool sizes are the sum of two independent terms: a vCPU-derived term (general node
   * compute/dispatch capacity) and a physical-tenant-derived term (each extra physical tenant is an
   * independent traffic source needing its own slice of headroom). The two are additive, not
   * multiplicative, because they size unrelated dimensions — scaling vCPUs shouldn't scale the
   * tenant term and vice versa. A flat ceiling bounds the total regardless of which dimension is
   * large.
   *
   * @param corePoolSizeMultiplier multiplier for the number of core threads based on available
   *     processors
   * @param maxPoolSizeMultiplier multiplier for the maximum number of threads based on available
   *     processors
   * @param keepAliveSeconds how long to keep idle threads above core alive
   * @param queueCapacity tiny bounded queue capacity for short bursts (e.g., 32–128)
   * @param extraTenants number of physical tenants beyond the first
   * @param corePoolSizePerTenant additional core threads per extra tenant
   * @param maxPoolSizePerTenant additional max threads per extra tenant
   * @param queueCapacityPerTenant additional queue capacity per extra tenant
   * @param corePoolSizeCeiling flat ceiling on the computed core pool size
   * @param maxPoolSizeCeiling flat ceiling on the computed max pool size
   * @param queueCapacityCeiling flat ceiling on the computed queue capacity
   */
  private static ExecutorService create(
      final int corePoolSizeMultiplier,
      final int maxPoolSizeMultiplier,
      final long keepAliveSeconds,
      final int queueCapacity,
      final int extraTenants,
      final int corePoolSizePerTenant,
      final int maxPoolSizePerTenant,
      final int queueCapacityPerTenant,
      final int corePoolSizeCeiling,
      final int maxPoolSizeCeiling,
      final int queueCapacityCeiling) {

    final int availableProcessors = Runtime.getRuntime().availableProcessors();
    final int corePoolSize =
        Math.min(
            corePoolSizeCeiling,
            availableProcessors * corePoolSizeMultiplier + extraTenants * corePoolSizePerTenant);
    final int maxPoolSize =
        Math.min(
            maxPoolSizeCeiling,
            availableProcessors * maxPoolSizeMultiplier + extraTenants * maxPoolSizePerTenant);
    final ThreadFactory threadFactory =
        Thread.ofPlatform().name(API_SERVICE_THREAD_NAME, 0).daemon(true).factory();

    // Tiny bounded buffer to absorb micro-bursts
    final int cap =
        Math.max(
            1,
            Math.min(queueCapacityCeiling, queueCapacity + extraTenants * queueCapacityPerTenant));
    final BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(cap);

    final ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            keepAliveSeconds,
            TimeUnit.SECONDS,
            workQueue,
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy());

    executor.allowCoreThreadTimeOut(true); // needed if corePoolSize is greater than 0
    return executor;
  }
}
