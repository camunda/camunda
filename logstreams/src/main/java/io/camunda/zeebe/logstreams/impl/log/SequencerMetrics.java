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

  private static final Histogram BATCH_LENGTH_BYTES =
      Histogram.build()
          .namespace("zeebe")
          .name("sequencer_batch_length_bytes")
          .help("Histogram over the size, in Kilobytes, of the sequenced batches")
          .buckets(0.256, 0.512, 1, 4, 8, 32, 128, 512, 1024, 4096)
          .labelNames("partition")
          .register();

  private final Gauge.Child queueSize;
  private final Histogram.Child batchSize;
  private final Histogram.Child batchLengthBytes;

  SequencerMetrics(final int partitionId) {
    final var partitionLabel = String.valueOf(partitionId);
    queueSize = QUEUE_SIZE.labels(partitionLabel);
    batchSize = BATCH_SIZE.labels(partitionLabel);
    batchLengthBytes = BATCH_LENGTH_BYTES.labels(partitionLabel);
  }

  void setQueueSize(final int length) {
    queueSize.set(length);
  }

  void observeBatchSize(final int size) {
    batchSize.observe(size);
  }

  void observeBatchLengthBytes(final int lengthBytes) {
    final int batchLengthKiloBytes = Math.floorDiv(lengthBytes, 1024);
    batchLengthBytes.observe(batchLengthKiloBytes);
  }
}
