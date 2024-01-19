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
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class OperateElasticsearchMetrics {

  private static final String NAMESPACE = "zeebe_operate_elasticsearch_exporter";
  private static final String PARTITION_LABEL = "partition";
  private static final String THREAD_NAME = "threadname";

  private static final Counter FLUSHED_CACHES =
      Counter.build()
          .namespace(NAMESPACE)
          .name("flushed_caches")
          .help("Number of times the entity cache is flushed and submitted for JSON serialization")
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Histogram BULK_MEMORY_SIZE =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("bulk_memory_size")
          .help("Size of JSON-serialized bulk requests to Elasticsearch")
          .buckets(
              1_000_000,
              2_000_000,
              3_000_000,
              4_000_000,
              5_000_000,
              6_000_000,
              7_000_000,
              8_000_000,
              9_000_000,
              10_000_000,
              11_000_000,
              12_000_000)
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Histogram BULK_RECORD_SIZE =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("bulk_record_size")
          .help("Number of records that were converted into a batch of entities")
          .buckets(
              100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200, 1300, 1400, 1500, 1600,
              1700, 1800, 1900, 2000)
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Histogram REQUEST_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("request_duration_seconds")
          .help("Duration of making bulk requests to Elasticsearch in seconds")
          .buckets(
              .025, .05, .075, .1, .125, .15, .175, .2, .225, .25, .275, .3, .325, .35, .375, .4,
              .425, .45, .475, .5, .6, .7, .8, .9, 1)
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Histogram RECORD_CONVERSION_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("record_conversion_duration_seconds")
          .help("Duration of conversion of Zeebe record into Operate entity in seconds")
          .buckets(
              .000005, .00001, .000015, .00002, .000025, .00003, .000035, .00004, .000045, .00005,
              .075, .1)
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Gauge RECORD_CONVERSION_DURATION_CUMULATIVE =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("record_conversion_duration_seconds_cumulative")
          .help("Total duration of conversion of Zeebe record into Operate entity in seconds")
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Histogram SERIALIZATION_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("serialization_duration_seconds")
          .help("Duration of conversion a flushed entity cache to a JSON request body in seconds")
          .buckets(.005, .01, .015, .02, .025, .03, .035, .04, .045, .05, .075, .1, .25, .5, .75, 1)
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Gauge JSON_PROCESSING_QUEUE_SIZE =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("json_processing_queue_size")
          .help(
              "Size of the queue that keeps the current batches for JSON processing (a batch can remain "
                  + "in the queue if it was processed but not picked picked up for sending yet)")
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Gauge THREAD_CPU_TIME =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("thread_cpu_time")
          .help("CPU time as determined by ThreadMXBean#getThreadCpuTime")
          .labelNames(PARTITION_LABEL, THREAD_NAME)
          .register();

  private final String partitionIdLabel;

  private final ThreadMXBean threadMxBean;

  public OperateElasticsearchMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
    this.threadMxBean = ManagementFactory.getThreadMXBean();
  }

  public void countCacheFlush() {
    FLUSHED_CACHES.labels(partitionIdLabel).inc();
  }

  public void recordBulkMemorySize(final int bulkSizeInBytes) {
    BULK_MEMORY_SIZE.labels(partitionIdLabel).observe(bulkSizeInBytes);
  }

  public void recordBulkRecordsSize(final int numberOfRecordsInBulk) {
    BULK_RECORD_SIZE.labels(partitionIdLabel).observe(numberOfRecordsInBulk);
  }

  public Histogram.Timer measureRequestDuration() {
    return REQUEST_DURATION.labels(partitionIdLabel).startTimer();
  }

  public Histogram.Timer measureSerializationDuration() {
    return SERIALIZATION_DURATION.labels(partitionIdLabel).startTimer();
  }

  public Histogram.Timer measureRecordConversionDuration() {
    return RECORD_CONVERSION_DURATION.labels(partitionIdLabel).startTimer();
  }

  public void recordJsonProcessingQueueSize(final int queueSize) {
    JSON_PROCESSING_QUEUE_SIZE.labels(partitionIdLabel).set(queueSize);
  }

  public void recordCurrentThreadCpuTime() {
    final String threadName = Thread.currentThread().getName();
    final long cpuTime = threadMxBean.getCurrentThreadCpuTime();

    THREAD_CPU_TIME.labels(partitionIdLabel, threadName).set(cpuTime);
  }

  public void incrementRecordConversionDuration(final double duration) {
    RECORD_CONVERSION_DURATION_CUMULATIVE.labels(partitionIdLabel).inc(duration);
  }
}
