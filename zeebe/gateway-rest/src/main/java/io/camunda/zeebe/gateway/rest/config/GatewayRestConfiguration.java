/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import java.time.Duration;

public class GatewayRestConfiguration {

  private static final int DEFAULT_MAX_NAME_FIELD_LENGTH = 32 * 1024;

  private final ProcessCacheConfiguration processCache = new ProcessCacheConfiguration();
  private final ApiExecutorConfiguration apiExecutor = new ApiExecutorConfiguration();
  private final JobMetricsConfiguration jobMetrics = new JobMetricsConfiguration();
  private final ProcessInstanceExportConfiguration processInstanceExport =
      new ProcessInstanceExportConfiguration();
  private int maxNameFieldLength = DEFAULT_MAX_NAME_FIELD_LENGTH;

  public ProcessCacheConfiguration getProcessCache() {
    return processCache;
  }

  public ApiExecutorConfiguration getApiExecutor() {
    return apiExecutor;
  }

  public JobMetricsConfiguration getJobMetrics() {
    return jobMetrics;
  }

  public ProcessInstanceExportConfiguration getProcessInstanceExport() {
    return processInstanceExport;
  }

  /**
   * Maximum allowed length for name-type fields (e.g. message names, variable names) validated
   * across REST and gRPC gateway requests.
   *
   * <p>Defaults to {@link #DEFAULT_MAX_NAME_FIELD_LENGTH}.
   */
  public int getMaxNameFieldLength() {
    return maxNameFieldLength;
  }

  public void setMaxNameFieldLength(final int maxNameFieldLength) {
    this.maxNameFieldLength = maxNameFieldLength;
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

  /**
   * Configuration for the REST API executor thread pool.
   *
   * <p>The executor is sized proportionally to the number of available CPU cores using multipliers
   * instead of absolute thread counts, so it scales sensibly across different deployment sizes
   * without manual retuning.
   *
   * <p>Effective sizes (computed at startup using {@code Runtime.availableProcessors()}):
   *
   * <pre>
   *   corePoolSize = availableProcessors * corePoolSizeMultiplier
   *   maxPoolSize  = availableProcessors * maxPoolSizeMultiplier
   * </pre>
   *
   * <p>Threads above the core size may be reclaimed after being idle for {@code keepAliveSeconds}.
   *
   * <p><b>Constraints:</b>
   *
   * <ul>
   *   <li>{@code corePoolSizeMultiplier} ≥ 0 (0 means no core threads)
   *   <li>{@code maxPoolSizeMultiplier} &gt; 0 (and typically ≥ core multiplier)
   * </ul>
   */
  public static class ApiExecutorConfiguration {

    private static final int DEFAULT_CORE_POOL_SIZE_MULTIPLIER = 1;
    private static final int DEFAULT_MAX_POOL_SIZE_MULTIPLIER = 8;
    private static final long DEFAULT_KEEP_ALIVE_SECONDS = 60L;
    private static final int DEFAULT_QUEUE_CAPACITY = 64;

    /**
     * Multiplier applied to the number of available processors to compute the executor's core pool
     * size (minimum number of threads kept alive).
     *
     * <p>Effective value: {@code corePoolSize = availableProcessors * corePoolSizeMultiplier}.
     *
     * <p>Use a higher value if you have steady, continuous traffic and want to minimize cold-start
     * latency; keep it low to allow the pool to scale down when idle.
     *
     * <p>Default value: {@link #DEFAULT_CORE_POOL_SIZE_MULTIPLIER}.
     */
    private int corePoolSizeMultiplier = DEFAULT_CORE_POOL_SIZE_MULTIPLIER;

    /**
     * Multiplier applied to the number of available processors to compute the executor's maximum
     * pool size (hard cap on threads).
     *
     * <p>Effective value: {@code maxPoolSize = availableProcessors * maxPoolSizeMultiplier}.
     *
     * <p>Must be >= {@code corePoolSizeMultiplier}. Increase cautiously; high values can cause
     * oversubscription for CPU-bound workloads.
     *
     * <p>Default value: {@link #DEFAULT_MAX_POOL_SIZE_MULTIPLIER}.
     */
    private int maxPoolSizeMultiplier = DEFAULT_MAX_POOL_SIZE_MULTIPLIER;

    /**
     * Time in seconds that threads above the core size may remain idle before being terminated.
     * Lower values reclaim resources faster after bursts; higher values reduce thread
     * creation/destruction churn if bursts are frequent.
     *
     * <p>Default value: {@link #DEFAULT_KEEP_ALIVE_SECONDS}.
     */
    private long keepAliveSeconds = DEFAULT_KEEP_ALIVE_SECONDS;

    /**
     * Capacity of the executor's task queue. A small bounded queue (e.g. 64) is recommended to
     * handle short bursts while still allowing the pool to grow. Default value: {@link
     * #DEFAULT_QUEUE_CAPACITY}.
     */
    private int queueCapacity = DEFAULT_QUEUE_CAPACITY;

    public int getCorePoolSizeMultiplier() {
      return corePoolSizeMultiplier;
    }

    public void setCorePoolSizeMultiplier(final int corePoolSizeMultiplier) {
      if (corePoolSizeMultiplier < 0) {
        throw new IllegalArgumentException(
            "corePoolSizeMultiplier must be >= 0 (was " + corePoolSizeMultiplier + ")");
      }
      this.corePoolSizeMultiplier = corePoolSizeMultiplier;
    }

    public int getMaxPoolSizeMultiplier() {
      return maxPoolSizeMultiplier;
    }

    public void setMaxPoolSizeMultiplier(final int maxPoolSizeMultiplier) {
      if (maxPoolSizeMultiplier <= 0) {
        throw new IllegalArgumentException(
            "maxPoolSizeMultiplier must be > 0 (was " + maxPoolSizeMultiplier + ")");
      }
      this.maxPoolSizeMultiplier = maxPoolSizeMultiplier;
    }

    public long getKeepAliveSeconds() {
      return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(final long keepAliveSeconds) {
      if (keepAliveSeconds <= 0) {
        throw new IllegalArgumentException(
            "keepAliveSeconds must be > 0 (was " + keepAliveSeconds + ")");
      }
      this.keepAliveSeconds = keepAliveSeconds;
    }

    public int getQueueCapacity() {
      return queueCapacity;
    }

    public void setQueueCapacity(final int queueCapacity) {
      if (queueCapacity <= 0) {
        throw new IllegalArgumentException("queueCapacity must be > 0 (was " + queueCapacity + ")");
      }
      this.queueCapacity = queueCapacity;
    }
  }

  /** Configuration for job metrics export settings. */
  public static class JobMetricsConfiguration {

    private static final Duration DEFAULT_EXPORT_INTERVAL = Duration.ofMinutes(5);
    private static final int DEFAULT_MAX_WORKER_NAME_LENGTH = 100;
    private static final int DEFAULT_MAX_JOB_TYPE_LENGTH = 100;
    private static final int DEFAULT_MAX_TENANT_ID_LENGTH = 30;
    private static final int DEFAULT_MAX_UNIQUE_KEYS = 9500;
    private static final boolean DEFAULT_ENABLED = true;

    /** The interval at which job metrics are exported. */
    private Duration exportInterval = DEFAULT_EXPORT_INTERVAL;

    /** The maximum length of the worker name used in job metrics labels. */
    private int maxWorkerNameLength = DEFAULT_MAX_WORKER_NAME_LENGTH;

    /** The maximum length of the job type used in job metrics labels. */
    private int maxJobTypeLength = DEFAULT_MAX_JOB_TYPE_LENGTH;

    /** The maximum length of the tenant ID used in job metrics labels. */
    private int maxTenantIdLength = DEFAULT_MAX_TENANT_ID_LENGTH;

    /** The maximum number of unique metric keys tracked for job metrics. */
    private int maxUniqueKeys = DEFAULT_MAX_UNIQUE_KEYS;

    /** Whether job metrics export is enabled. */
    private boolean enabled = DEFAULT_ENABLED;

    public Duration getExportInterval() {
      return exportInterval;
    }

    public void setExportInterval(final Duration exportInterval) {
      this.exportInterval = exportInterval;
    }

    public int getMaxWorkerNameLength() {
      return maxWorkerNameLength;
    }

    public void setMaxWorkerNameLength(final int maxWorkerNameLength) {
      this.maxWorkerNameLength = maxWorkerNameLength;
    }

    public int getMaxJobTypeLength() {
      return maxJobTypeLength;
    }

    public void setMaxJobTypeLength(final int maxJobTypeLength) {
      this.maxJobTypeLength = maxJobTypeLength;
    }

    public int getMaxTenantIdLength() {
      return maxTenantIdLength;
    }

    public void setMaxTenantIdLength(final int maxTenantIdLength) {
      this.maxTenantIdLength = maxTenantIdLength;
    }

    public int getMaxUniqueKeys() {
      return maxUniqueKeys;
    }

    public void setMaxUniqueKeys(final int maxUniqueKeys) {
      this.maxUniqueKeys = maxUniqueKeys;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }
  }

  /**
   * Configuration for the {@code POST /v2/process-instances/search.csv} endpoint that streams the
   * filtered process-instance grid as CSV for offline analysis.
   *
   * <p>Bound under {@code camunda.rest.process-instance-export.*}.
   */
  public static class ProcessInstanceExportConfiguration {

    private static final int DEFAULT_MAX_ROWS = 50_000;
    private static final int DEFAULT_PAGE_SIZE = 1_000;

    /**
     * Cap on the number of rows a single export request will stream. Beyond this the response is
     * truncated and the {@code X-Camunda-Export-Truncated} header is set so clients can warn the
     * user. Default: {@link #DEFAULT_MAX_ROWS}. Operators who need larger exports can override at
     * their own discretion — at some point the bottleneck moves from this cap to streaming timeouts
     * / BI-tool memory and a real reporting pipeline becomes the right answer.
     */
    private int maxRows = DEFAULT_MAX_ROWS;

    /**
     * Internal page size used to walk the matching set via cursor pagination. Larger values reduce
     * round-trips to secondary storage; smaller values keep memory pressure lower. Default: {@link
     * #DEFAULT_PAGE_SIZE}.
     */
    private int pageSize = DEFAULT_PAGE_SIZE;

    public int getMaxRows() {
      return maxRows;
    }

    public void setMaxRows(final int maxRows) {
      if (maxRows <= 0) {
        throw new IllegalArgumentException("maxRows must be > 0 (was " + maxRows + ")");
      }
      this.maxRows = maxRows;
    }

    public int getPageSize() {
      return pageSize;
    }

    public void setPageSize(final int pageSize) {
      if (pageSize <= 0) {
        throw new IllegalArgumentException("pageSize must be > 0 (was " + pageSize + ")");
      }
      this.pageSize = pageSize;
    }
  }
}
