/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl.metrics;

import io.prometheus.client.Histogram;

public class ProcessingMetrics {

  private static final String NAMESPACE = "zeebe";
  private static final String LABEL_NAME_PARTITION = "partition";

  private static final Histogram BATCH_PROCESSING_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("stream_processor_batch_processing_duration")
          .help("Time spent in batch processing (in seconds)")
          .labelNames(LABEL_NAME_PARTITION)
          .register();

  private final String partitionIdLabel;

  public ProcessingMetrics(final String partitionIdLabel) {
    this.partitionIdLabel = partitionIdLabel;
  }

  public Histogram.Timer startBatchProcessingDurationTimer() {
    return BATCH_PROCESSING_DURATION.labels(partitionIdLabel).startTimer();
  }
}
