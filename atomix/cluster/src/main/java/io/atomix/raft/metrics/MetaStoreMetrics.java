/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.metrics;

import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;

public final class MetaStoreMetrics extends RaftMetrics {
  private static final Histogram LAST_FLUSHED_INDEX_UPDATE =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("last_flushed_index_update")
          .help("Time it takes to update the last flushed index")
          .labelNames(PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .register();

  private final Histogram.Child lastFlushedIndexUpdate;

  public MetaStoreMetrics(final String partitionName) {
    super(partitionName);

    lastFlushedIndexUpdate = LAST_FLUSHED_INDEX_UPDATE.labels(partitionGroupName, partition);
  }

  public Timer observeLastFlushedIndexUpdate() {
    return lastFlushedIndexUpdate.startTimer();
  }
}
