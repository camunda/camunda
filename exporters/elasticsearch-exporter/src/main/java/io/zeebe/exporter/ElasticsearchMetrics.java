/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.exporter;

import io.prometheus.client.Histogram;

public class ElasticsearchMetrics {

  private static final Histogram FLUSH_DURATION =
      Histogram.build()
          .namespace("zeebe_elasticsearch_exporter")
          .name("flush_duration_seconds")
          .help("Flush duration of bulk exporters in seconds")
          .labelNames("partition")
          .register();

  private static final Histogram BULK_SIZE =
      Histogram.build()
          .namespace("zeebe_elasticsearch_exporter")
          .name("bulk_size")
          .help("Exporter bulk size")
          .buckets(10, 100, 1_000, 10_000, 100_000)
          .labelNames("partition")
          .register();

  private final String partitionIdLabel;

  public ElasticsearchMetrics(int partitionId) {
    this.partitionIdLabel = String.valueOf(partitionId);
  }

  public Histogram.Timer measureFlushDuration() {
    return FLUSH_DURATION.labels(partitionIdLabel).startTimer();
  }

  public void recordBulkSize(int bulkSize) {
    BULK_SIZE.labels(partitionIdLabel).observe(bulkSize);
  }
}
