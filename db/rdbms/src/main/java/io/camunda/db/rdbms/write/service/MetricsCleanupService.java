/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsCleanupService {

  private static final Logger LOG = LoggerFactory.getLogger(MetricsCleanupService.class);

  private final RdbmsWriterConfig config;
  private final UsageMetricWriter usageMetricWriter;
  private final RdbmsWriterMetrics metrics;
  private final int cleanupBatchSize;

  public MetricsCleanupService(
      final RdbmsWriterConfig config,
      final UsageMetricWriter usageMetricWriter,
      final RdbmsWriterMetrics metrics) {

    this.config = config;
    this.usageMetricWriter = usageMetricWriter;
    this.metrics = metrics;
    // TODO add configs
    cleanupBatchSize = config.historyCleanupBatchSize();
  }

  public Duration cleanupMetrics(final int partitionId, final OffsetDateTime cleanupDate) {
    try (final var timer = metrics.measureHistoryCleanupDuration()) {
      final var numDeletedRecords =
          usageMetricWriter.cleanupMetrics(partitionId, cleanupDate, cleanupBatchSize);
      LOG.debug("Deleted metrics records: {}", numDeletedRecords);
    }

    // TODO Calculate next duration. Reuse history?
    return null;
  }
}
