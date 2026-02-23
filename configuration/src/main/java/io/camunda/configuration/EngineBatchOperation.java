/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;
import java.util.Set;

public class EngineBatchOperation {

  private static final String PREFIX = "camunda.processing.engine.batch-operations";
  private static final Duration DEFAULT_BATCH_OPERATION_SCHEDULER_INTERVAL = Duration.ofSeconds(1);
  // reasonable size of a chunk record to avoid too many or too large records
  private static final int DEFAULT_BATCH_OPERATION_CHUNK_SIZE = 100;
  // ES/OS have max 10000 entities per query
  private static final int DEFAULT_BATCH_OPERATION_QUERY_PAGE_SIZE = 10000;
  // Oracle can only have 1000 elements in `IN` clause
  private static final int DEFAULT_BATCH_OPERATION_QUERY_IN_CLAUSE_SIZE = 1000;
  private static final int DEFAULT_BATCH_OPERATION_QUERY_RETRY_MAX = 0;
  private static final Duration DEFAULT_BATCH_OPERATION_QUERY_RETRY_INITIAL_DELAY =
      Duration.ofSeconds(1);
  private static final Duration DEFAULT_BATCH_OPERATION_QUERY_RETRY_MAX_DELAY =
      Duration.ofSeconds(60);
  private static final int DEFAULT_BATCH_OPERATION_QUERY_RETRY_BACKOFF_FACTOR = 2;

  private static final Set<String> LEGACY_SCHEDULER_INTERVAL_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.batchOperations.schedulerInterval");

  private static final Set<String> LEGACY_CHUNK_SIZE_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.batchOperations.chunkSize");

  private static final Set<String> LEGACY_DB_CHUNK_SIZE_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.batchOperations.dbChunkSize");

  private static final Set<String> LEGACY_QUERY_PAGE_SIZE_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.batchOperations.queryPageSize");

  private static final Set<String> LEGACY_QUERY_IN_CLAUSE_SIZE_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.batchOperations.queryInClauseSize");

  private static final Set<String> LEGACY_QUERY_RETRY_MAX_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.batchOperations.queryRetryMax");

  private static final Set<String> LEGACY_QUERY_RETRY_INITIAL_DELAY_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.batchOperations.queryRetryInitialDelay");

  private static final Set<String> LEGACY_QUERY_RETRY_MAX_DELAY_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.batchOperations.queryRetryMaxDelay");

  private static final Set<String> LEGACY_QUERY_RETRY_BACKOFF_FACTOR_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.batchOperations.queryRetryBackoffFactor");

  /**
   * The interval at which the batch operation scheduler runs.
   *
   * <p>Defaults to {@link #DEFAULT_BATCH_OPERATION_SCHEDULER_INTERVAL}.
   */
  private Duration schedulerInterval = DEFAULT_BATCH_OPERATION_SCHEDULER_INTERVAL;

  /**
   * Number of itemKeys in one BatchOperationChunkRecord.
   *
   * <p>To avoid too large records, the set of executed items ina batch operation is split into
   * multiple chunks. Smaller values lead to more records needed to transport the items and the more
   * metadata overhead is created. Larger values tend to overload the exporters as many rows need to
   * be written per exported record.
   *
   * <p>Defaults to {@link #DEFAULT_BATCH_OPERATION_CHUNK_SIZE}.
   */
  private int chunkSize = DEFAULT_BATCH_OPERATION_CHUNK_SIZE;

  /**
   * The page size for batch operation queries.
   *
   * <p>To initialize a batch operation, the engine will query the secondary storage for relevant
   * items using the given filter object. Since the secondary storage uses paginated queries, this
   * setting allows to configure the maximum number of items that can be returned in a single query.
   * The higher the value, the more items can be returned in a single query, which can lead to
   * better performance but also to memory issues when the pages are too large.
   *
   * <p>When using Elasticsearch or OpenSearch as secondary storage, the default value of 10000
   * items is also the maximum pageSize of these databases and a higher value here will be ignored.
   *
   * <p>Defaults to {@link #DEFAULT_BATCH_OPERATION_QUERY_PAGE_SIZE}.
   */
  private int queryPageSize = DEFAULT_BATCH_OPERATION_QUERY_PAGE_SIZE;

  /**
   * The size of the IN clause for batch operation queries.
   *
   * <p>Some batch operation types need to query the secondary storage in multiple steps. E.g.
   * RESOLVE_INCIDENT first queries for processInstances and in a second step for all open incidents
   * of these processInstances. This setting allows to configure the maximum number of itemKeys to
   * be given in the second step query as processInstances "IN" clause. A higher value will result
   * in fewer queries to the secondary storage, but may not be supported by all database systems.
   * E.g. OracleDb only supports 1000 elements in an IN clause.
   *
   * <p>Defaults to {@link #DEFAULT_BATCH_OPERATION_QUERY_IN_CLAUSE_SIZE}.
   */
  private int queryInClauseSize = DEFAULT_BATCH_OPERATION_QUERY_IN_CLAUSE_SIZE;

  /**
   * The maximum number of retries for batch operation queries.
   *
   * <p>Allows to configure the number of retries in case a query to the secondary database fails.
   * This can be used to mitigate transient issues with the secondary database. A retry will be
   * attempted after a delay, which can be configured with {@link #queryRetryInitialDelay} and
   * {@link #queryRetryMaxDelay} and will be increased exponentially with the given backoff factor
   * {@link #queryRetryBackoffFactor}.
   *
   * <p>Defaults to {@link #DEFAULT_BATCH_OPERATION_QUERY_RETRY_MAX}.
   */
  private int queryRetryMax = DEFAULT_BATCH_OPERATION_QUERY_RETRY_MAX;

  /**
   * The initial delay for retrying batch operation queries in case a query to secondary storage
   * fails.
   *
   * <p>This can be used to mitigate transient issues with the secondary database. A retry will be
   * attempted after a delay, which can be configured with this setting as initial delay and {@link
   * #queryRetryMaxDelay} as maximum delay and will be increased exponentially with the given
   * backoff factor {@link #queryRetryBackoffFactor}.
   *
   * <p>Defaults to {@link #DEFAULT_BATCH_OPERATION_QUERY_RETRY_INITIAL_DELAY}.
   */
  private Duration queryRetryInitialDelay = DEFAULT_BATCH_OPERATION_QUERY_RETRY_INITIAL_DELAY;

  /**
   * The maximum delay between retries if batch operation queries fail.
   *
   * <p>This can be used to mitigate transient issues with the secondary database. A retry will be
   * attempted after a delay, which can be configured with {@link #queryRetryInitialDelay} as
   * initial delay and this setting as maximum delay and will be increased exponentially with the
   * given backoff factor {@link #queryRetryBackoffFactor}. The maximum delay can be used to prevent
   * too long delays between retries in case of a high backoff factor or many retries.
   *
   * <p>Defaults to {@link #DEFAULT_BATCH_OPERATION_QUERY_RETRY_MAX_DELAY}.
   */
  private Duration queryRetryMaxDelay = DEFAULT_BATCH_OPERATION_QUERY_RETRY_MAX_DELAY;

  /**
   * The backoff factor by which the delay between retries is increased.
   *
   * <p>This can be used to mitigate transient issues with the secondary database. A retry will be
   * attempted after a delay, which can be configured with {@link #queryRetryInitialDelay} as
   * initial delay and {@link #queryRetryMaxDelay} as maximum delay and will be increased
   * exponentially with this backoff factor.
   *
   * <p>A higher value will lead to a faster increase of the delay between retries, which can be
   * useful to reduce the load on the secondary database in case of longer outages, but can also
   * lead to longer delays between retries in case of a high number of retries or a high backoff
   * factor.
   *
   * <p>Setting the value to 1 will lead to a constant delay between retries.
   *
   * <p>Defaults to {@link #DEFAULT_BATCH_OPERATION_QUERY_RETRY_BACKOFF_FACTOR}.
   */
  private int queryRetryBackoffFactor = DEFAULT_BATCH_OPERATION_QUERY_RETRY_BACKOFF_FACTOR;

  public Duration getSchedulerInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".scheduler-interval",
        schedulerInterval,
        Duration.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_SCHEDULER_INTERVAL_PROPERTIES);
  }

  public void setSchedulerInterval(final Duration schedulerInterval) {
    this.schedulerInterval = schedulerInterval;
  }

  public int getChunkSize() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".chunk-size",
        chunkSize,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_CHUNK_SIZE_PROPERTIES);
  }

  public void setChunkSize(final int chunkSize) {
    this.chunkSize = chunkSize;
  }

  public int getQueryPageSize() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".query-page-size",
        queryPageSize,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_QUERY_PAGE_SIZE_PROPERTIES);
  }

  public void setQueryPageSize(final int queryPageSize) {
    this.queryPageSize = queryPageSize;
  }

  public int getQueryInClauseSize() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".query-in-clause-size",
        queryInClauseSize,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_QUERY_IN_CLAUSE_SIZE_PROPERTIES);
  }

  public void setQueryInClauseSize(final int queryInClauseSize) {
    this.queryInClauseSize = queryInClauseSize;
  }

  public int getQueryRetryMax() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".query-retry-max",
        queryRetryMax,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_QUERY_RETRY_MAX_PROPERTIES);
  }

  public void setQueryRetryMax(final int queryRetryMax) {
    this.queryRetryMax = queryRetryMax;
  }

  public Duration getQueryRetryInitialDelay() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".query-retry-initial-delay",
        queryRetryInitialDelay,
        Duration.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_QUERY_RETRY_INITIAL_DELAY_PROPERTIES);
  }

  public void setQueryRetryInitialDelay(final Duration queryRetryInitialDelay) {
    this.queryRetryInitialDelay = queryRetryInitialDelay;
  }

  public Duration getQueryRetryMaxDelay() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".query-retry-max-delay",
        queryRetryMaxDelay,
        Duration.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_QUERY_RETRY_MAX_DELAY_PROPERTIES);
  }

  public void setQueryRetryMaxDelay(final Duration queryRetryMaxDelay) {
    this.queryRetryMaxDelay = queryRetryMaxDelay;
  }

  public int getQueryRetryBackoffFactor() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".query-retry-backoff-factor",
        queryRetryBackoffFactor,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_QUERY_RETRY_BACKOFF_FACTOR_PROPERTIES);
  }

  public void setQueryRetryBackoffFactor(final int queryRetryBackoffFactor) {
    this.queryRetryBackoffFactor = queryRetryBackoffFactor;
  }
}
