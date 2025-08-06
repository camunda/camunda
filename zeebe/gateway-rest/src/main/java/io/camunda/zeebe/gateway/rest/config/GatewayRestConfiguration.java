/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

public class GatewayRestConfiguration {

  private final ProcessCacheConfiguration processCache = new ProcessCacheConfiguration();
  private final ApiExecutorConfiguration apiExecutor = new ApiExecutorConfiguration();

  public ProcessCacheConfiguration getProcessCache() {
    return processCache;
  }

  public ApiExecutorConfiguration getApiExecutor() {
    return apiExecutor;
  }

  public static class ProcessCacheConfiguration {
    private static final int DEFAULT_CACHE_SIZE = 100;

    /**
     * Process cache max size. Default value: {@link ProcessCacheConfiguration#DEFAULT_CACHE_SIZE}.
     */
    private int maxSize = DEFAULT_CACHE_SIZE;

    /** Process cache expiration milliseconds. Default value: {@code null}. */
    private Long expirationIdleMillis = null;

    public int getMaxSize() {
      return maxSize;
    }

    public void setMaxSize(final int maxSize) {
      this.maxSize = maxSize;
    }

    public Long getExpirationIdleMillis() {
      return expirationIdleMillis;
    }

    public void setExpirationIdleMillis(final Long expirationIdleMillis) {
      this.expirationIdleMillis = expirationIdleMillis;
    }
  }

  public static class ApiExecutorConfiguration {

    private static final int DEFAULT_CORE_POOL_SIZE = 0;
    private static final int DEFAULT_THREAD_COUNT_MULTIPLIER = 8;
    private static final long DEFAULT_KEEP_ALIVE_SECONDS = 60L;

    private int corePoolSize = DEFAULT_CORE_POOL_SIZE;
    private int threadCountMultiplier = DEFAULT_THREAD_COUNT_MULTIPLIER;
    private long keepAliveSeconds = DEFAULT_KEEP_ALIVE_SECONDS;

    public int getCorePoolSize() {
      return corePoolSize;
    }

    public void setCorePoolSize(final int corePoolSize) {
      this.corePoolSize = corePoolSize;
    }

    public int getThreadCountMultiplier() {
      return threadCountMultiplier;
    }

    public void setThreadCountMultiplier(final int threadCountMultiplier) {
      this.threadCountMultiplier = threadCountMultiplier;
    }

    public long getKeepAliveSeconds() {
      return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(final long keepAliveSeconds) {
      this.keepAliveSeconds = keepAliveSeconds;
    }
  }
}
