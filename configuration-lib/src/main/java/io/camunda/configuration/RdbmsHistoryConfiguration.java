/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;

public interface RdbmsHistoryConfiguration {

  Duration getDefaultHistoryTTL();

  Duration getDecisionInstanceTTL();

  Duration getDefaultBatchOperationHistoryTTL();

  Duration getBatchOperationCancelProcessInstanceHistoryTTL();

  Duration getBatchOperationMigrateProcessInstanceHistoryTTL();

  Duration getBatchOperationModifyProcessInstanceHistoryTTL();

  Duration getBatchOperationResolveIncidentHistoryTTL();

  Duration getMinHistoryCleanupInterval();

  Duration getMaxHistoryCleanupInterval();

  Integer getHistoryCleanupBatchSize();

  Integer getHistoryCleanupProcessInstanceBatchSize();

  Duration getUsageMetricsCleanup();

  Duration getUsageMetricsTTL();

  double getMaxHistoryCleanupUsage();
}
