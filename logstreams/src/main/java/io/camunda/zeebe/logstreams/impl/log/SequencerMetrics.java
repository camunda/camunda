/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

final class SequencerMetrics {
  private static final Gauge QUEUE_SIZE =
      Gauge.build()
          .namespace("zeebe")
          .name("sequencer_queue_size")
          .help(
              "Current length of queue, i.e. how many entry batches are available to the appender")
          .labelNames("partition")
          .register();

  private static final Histogram BATCH_SIZE =
      Histogram.build()
          .namespace("zeebe")
          .name("sequencer_batch_size")
          .help("Histogram over the number of entries in each batch that is appended")
          .buckets(1, 2, 3, 5, 10, 25, 50, 100, 500, 1000)
          .labelNames("partition")
          .register();
  private final Gauge.Child queueSize;
  private final Histogram.Child batchSize;

  SequencerMetrics(final int partitionId) {
    final var partitionLabel = String.valueOf(partitionId);
    this.queueSize = QUEUE_SIZE.labels(partitionLabel);
    this.batchSize = BATCH_SIZE.labels(partitionLabel);
  }

  void setQueueSize(final int length) {
    queueSize.set(length);
  }

  void observeBatchSize(final int size) {
    batchSize.observe(size);
  }
}
