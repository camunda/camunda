/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface RdbmsConfiguration {

  String databaseName();

  String getUrl();

  List<String> getUrls();

  String getUsername();

  String getPassword();

  Boolean getAutoDdl();

  @Nullable String getPrefix();

  Duration getFlushInterval();

  Integer getQueueSize();

  Integer getQueueMemoryLimit();

  Integer getMaxVarcharFieldLength();

  @Nullable Duration getDdlLockWaitTimeout();

  boolean isExportBatchOperationItemsOnCreation();

  int getBatchOperationItemInsertBlockSize();

  RdbmsCacheConfiguration getProcessCache();

  RdbmsCacheConfiguration getBatchOperationCache();

  RdbmsHistoryConfiguration getHistory();

  RdbmsMetricsConfiguration getMetrics();

  RdbmsInsertBatchingConfiguration getInsertBatching();

  RdbmsAsyncReplicationConfiguration getAsyncReplication();

  RdbmsQueryConfiguration getQuery();

  RdbmsConnectionPoolConfiguration getConnectionPool();
}
