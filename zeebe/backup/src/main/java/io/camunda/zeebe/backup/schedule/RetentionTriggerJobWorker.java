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
import io.camunda.zeebe.dynamic.config.SystemPartitionBackupCommandSubmitter;
import io.camunda.zeebe.protocol.impl.record.value.backupmetadata.BackupMetadataRecord;
import io.camunda.zeebe.protocol.record.intent.BackupMetadataIntent;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the {@code retention-trigger} job emitted by the retention scheduler BPMN.
 *
 * <p>Each tick reads the persisted backup-metadata snapshot from the system partition, computes
 * which checkpoint ids fall outside the retention window, and submits one {@link
 * BackupMetadataIntent#DELETE DELETE} command per partition row that must be removed.
 *
 * <p>Hackday note: the retention window is interpreted against the row's last-modified timestamp,
 * which is not currently captured on {@link BackupMetadataRecord}. Until that field is added we
 * approximate by deleting only rows whose checkpoint id is strictly older than the configured
 * window relative to the highest known checkpoint id (windowing by id, not time). If the source-
 * branch behaviour requires time-based windowing, the orchestrator must be extended.
 */
public final class RetentionTriggerJobWorker implements AutoCloseable {

  public static final String JOB_TYPE = "retention-trigger";
  private static final Logger LOG = LoggerFactory.getLogger(RetentionTriggerJobWorker.class);

  private final CamundaClient camundaClient;
  private final SystemPartitionBackupCommandSubmitter systemPartition;
  private final Duration window;
  private JobWorker worker;

  public RetentionTriggerJobWorker(
      final CamundaClient camundaClient,
      final SystemPartitionBackupCommandSubmitter systemPartition,
      final Duration window) {
    this.camundaClient = camundaClient;
    this.systemPartition = systemPartition;
    this.window = window;
  }

  public void start() {
    worker = camundaClient.newWorker().jobType(JOB_TYPE).handler(this::handleJob).open();
  }

  private void handleJob(final JobClient jobClient, final ActivatedJob job) {
    if (window == null || window.isZero() || window.isNegative()) {
      LOG.warn("retention-trigger fired with no positive window configured; nothing to do");
      jobClient.newCompleteCommand(job.getKey()).variable("deletedCount", 0).send();
      return;
    }

    final ConcurrentLinkedQueue<BackupMetadataRecord> snapshot = new ConcurrentLinkedQueue<>();
    systemPartition
        .queryBackupMetadata(snapshot::add)
        .onComplete(
            (ignore, error) -> {
              if (error != null) {
                LOG.warn("retention-trigger: failed to query backup metadata", error);
                jobClient
                    .newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(error.getMessage())
                    .send();
                return;
              }
              final Set<Long> deletable = computeDeletable(snapshot);
              int deleted = 0;
              for (final var r : snapshot) {
                if (deletable.contains(r.getCheckpointId())) {
                  final BackupMetadataRecord cmd =
                      new BackupMetadataRecord()
                          .setCheckpointId(r.getCheckpointId())
                          .setPartitionId(r.getPartitionId());
                  systemPartition.submitBackupCommand(BackupMetadataIntent.DELETE, cmd);
                  deleted++;
                }
              }
              jobClient
                  .newCompleteCommand(job.getKey())
                  .variable("deletedCount", deleted)
                  .variable("deletedBackupIds", List.copyOf(deletable))
                  .send();
            });
  }

  /**
   * Approximation: delete every checkpoint id more than {@code window.toMillis()/1000} positions
   * older than the maximum observed id. Captures the spirit of "old backups beyond the retention
   * window" without the per-row timestamp the source branch had access to.
   */
  private Set<Long> computeDeletable(final Iterable<BackupMetadataRecord> rows) {
    long maxId = Long.MIN_VALUE;
    for (final var r : rows) {
      if (r.getCheckpointId() > maxId) {
        maxId = r.getCheckpointId();
      }
    }
    final long windowSlots = Math.max(1, window.toSeconds());
    final long threshold = maxId - windowSlots;
    final Set<Long> deletable = new HashSet<>();
    for (final var r : rows) {
      if (r.getCheckpointId() < threshold) {
        deletable.add(r.getCheckpointId());
      }
    }
    LOG.debug(
        "retention-trigger at {}: maxId={} threshold={} window={} → {} deletable id(s)",
        Instant.now(),
        maxId,
        threshold,
        window,
        deletable.size());
    return deletable;
  }

  @Override
  public void close() {
    if (worker != null) {
      worker.close();
    }
  }
}
