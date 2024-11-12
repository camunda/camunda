/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public class CheckpointMetrics {

  private static final String NAMESPACE = "zeebe";
  private static final String LABEL_NAME_PARTITION = "partition";
  private static final String LABEL_NAME_RESULT = "result";

  private static final Counter CHECKPOINT_RECORDS =
      Counter.build()
          .namespace(NAMESPACE)
          .name("checkpoint_records_total")
          .help(
              "Number of checkpoint records processed by stream processor. Processing can result in either creating a new checkpoint or ignoring the record. This can be observed by filtering for label 'result'.")
          .labelNames(LABEL_NAME_RESULT, LABEL_NAME_PARTITION)
          .register();

  private static final Gauge CHECKPOINT_POSITION =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("checkpoint_position")
          .help("Position of the last checkpoint")
          .labelNames(LABEL_NAME_PARTITION)
          .register();

  private static final Gauge CHECKPOINT_ID =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("checkpoint_id")
          .help("Id of the last checkpoint")
          .labelNames(LABEL_NAME_PARTITION)
          .register();

  final String partitionId;

  public CheckpointMetrics(final int partitionId) {
    this.partitionId = String.valueOf(partitionId);
  }

  public void created(final long checkpointId, final long checkpointPosition) {
    setCheckpointId(checkpointId, checkpointPosition);
    CHECKPOINT_RECORDS.labels("created", partitionId).inc();
  }

  public void setCheckpointId(final long checkpointId, final long checkpointPosition) {
    CHECKPOINT_ID.labels(partitionId).set(checkpointId);
    CHECKPOINT_POSITION.labels(partitionId).set(checkpointPosition);
  }

  public void ignored() {
    CHECKPOINT_RECORDS.labels("ignored", partitionId).inc();
  }
}
