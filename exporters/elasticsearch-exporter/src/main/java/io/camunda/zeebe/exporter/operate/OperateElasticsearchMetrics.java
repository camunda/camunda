/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

public class OperateElasticsearchMetrics {

  private static final String NAMESPACE = "zeebe_operate_elasticsearch_exporter";
  private static final String PARTITION_LABEL = "partition";


  private static final Counter FLUSHED_CACHES =
      Counter.build()
          .namespace(NAMESPACE)
          .name("flushed_caches")
          .help("Number of times the entity cache is flushed and submitted for JSON serialization")
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Histogram BULK_SIZE =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("bulk_size")
          .help("Size of JSON-serialized bulk requests to Elasticsearch")
          .buckets(10, 100, 1_000, 10_000, 100_000)
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Histogram REQUEST_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("request_duration_seconds")
          .help("Duration of making bulk requests to Elasticsearch in seconds")
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Histogram SERIALIZATION_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("serialization_duration_seconds")
          .help("Duration of convertion a flushed entity cache to a JSON request body in seconds")
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Gauge JSON_PROCESSING_QUEUE_SIZE =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("json_processing_queue_size")
          .help("Size of the queue that keeps the current batches for JSON processing (a batch can remain "
              + "in the queue if it was processed but not picked picked up for sending yet)")
          .labelNames(PARTITION_LABEL)
          .register();

  private final String partitionIdLabel;

  public OperateElasticsearchMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  public void countCacheFlush() {
    FLUSHED_CACHES.labels(partitionIdLabel).inc();
  }

  public void recordBulkSize(final int bulkSizeInBytes) {
    BULK_SIZE.labels(partitionIdLabel).observe(bulkSizeInBytes);
  }

  public Histogram.Timer measureRequestDuration() {
    return REQUEST_DURATION.labels(partitionIdLabel).startTimer();
  }

  public Histogram.Timer measureSerializationDuration() {
    return SERIALIZATION_DURATION.labels(partitionIdLabel).startTimer();
  }

  public void recordJsonProcessingQueueSize(final int queueSize) {
    JSON_PROCESSING_QUEUE_SIZE.labels(partitionIdLabel).set(queueSize);
  }
}
