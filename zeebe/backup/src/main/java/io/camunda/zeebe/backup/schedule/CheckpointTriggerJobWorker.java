/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.schedule;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.dynamic.config.ClusterConfigCommandSubmitter;
import io.camunda.zeebe.dynamic.config.SystemPartitionBackupCommandSubmitter;
import io.camunda.zeebe.protocol.impl.record.value.backupmetadata.BackupMetadataRecord;
import io.camunda.zeebe.protocol.record.intent.BackupMetadataIntent;
import java.util.PrimitiveIterator;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the {@code checkpoint-trigger} job emitted by the checkpoint scheduler BPMN.
 *
 * <p>Each tick computes a new checkpoint id and submits one {@link BackupMetadataIntent#RECORD
 * RECORD} command per data partition on the system partition. The {@code BackupOrchestrator} picks
 * up the resulting {@code RECORDED} events and fans out the {@code CheckpointIntent.CREATE}
 * commands to data partitions.
 */
public final class CheckpointTriggerJobWorker implements AutoCloseable {

  public static final String JOB_TYPE = "checkpoint-trigger";
  private static final Logger LOG = LoggerFactory.getLogger(CheckpointTriggerJobWorker.class);

  private final CamundaClient camundaClient;
  private final ClusterConfigCommandSubmitter systemPartition;
  private final SystemPartitionBackupCommandSubmitter backupSubmitter;
  private final AtomicLong nextCheckpointId;
  private JobWorker worker;

  public CheckpointTriggerJobWorker(
      final CamundaClient camundaClient,
      final ClusterConfigCommandSubmitter systemPartition,
      final SystemPartitionBackupCommandSubmitter backupSubmitter,
      final long initialCheckpointId) {
    this.camundaClient = camundaClient;
    this.systemPartition = systemPartition;
    this.backupSubmitter = backupSubmitter;
    nextCheckpointId = new AtomicLong(Math.max(1L, initialCheckpointId));
  }

  public void start() {
    worker = camundaClient.newWorker().jobType(JOB_TYPE).handler(this::handleJob).open();
  }

  private void handleJob(final JobClient jobClient, final ActivatedJob job) {
    final long checkpointId = nextCheckpointId.getAndIncrement();
    final var partitionIds = systemPartition.query().partitionIds();
    int submitted = 0;
    final PrimitiveIterator.OfInt it = partitionIds.iterator();
    while (it.hasNext()) {
      final int p = it.nextInt();
      final BackupMetadataRecord record =
          new BackupMetadataRecord()
              .setCheckpointId(checkpointId)
              .setPartitionId(p)
              .setStatus("PENDING");
      backupSubmitter.submitBackupCommand(BackupMetadataIntent.RECORD, record);
      submitted++;
    }
    if (submitted == 0) {
      LOG.warn(
          "checkpoint-trigger fired but cluster configuration has no data partitions; skipping");
      jobClient
          .newCompleteCommand(job.getKey())
          .variable("checkpointId", checkpointId)
          .variable("submittedPartitions", 0)
          .send();
      return;
    }
    jobClient
        .newCompleteCommand(job.getKey())
        .variable("checkpointId", checkpointId)
        .variable("submittedPartitions", submitted)
        .send();
  }

  @Override
  public void close() {
    if (worker != null) {
      worker.close();
    }
  }
}
