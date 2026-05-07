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
import io.camunda.zeebe.backup.client.api.BackupRequestHandler;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;

/**
 * Handles the {@code checkpoint-trigger} job emitted by the checkpoint scheduler BPMN processes.
 * The BPMN start timers drive the cadence; this worker only performs the broker request that
 * actually creates the checkpoint, mirroring what {@link CheckpointScheduler} previously did inline
 * on each tick.
 */
public final class CheckpointTriggerJobWorker implements AutoCloseable {

  public static final String JOB_TYPE = "checkpoint-trigger";

  private final CamundaClient camundaClient;
  private final BackupRequestHandler backupRequestHandler;
  private JobWorker worker;

  public CheckpointTriggerJobWorker(
      final CamundaClient camundaClient, final BackupRequestHandler backupRequestHandler) {
    this.camundaClient = camundaClient;
    this.backupRequestHandler = backupRequestHandler;
  }

  public void start() {
    worker = camundaClient.newWorker().jobType(JOB_TYPE).handler(this::handleJob).open();
  }

  private void handleJob(final JobClient jobClient, final ActivatedJob job) {
    final var vars = job.getVariablesAsMap();
    final var typeStr = (String) vars.get("checkpointType");
    if (typeStr == null) {
      jobClient
          .newThrowErrorCommand(job.getKey())
          .errorCode("CHECKPOINT_TYPE_MISSING")
          .errorMessage("checkpointType variable is required")
          .send();
      return;
    }

    final CheckpointType type;
    try {
      type = CheckpointType.valueOf(typeStr);
    } catch (final IllegalArgumentException e) {
      jobClient
          .newThrowErrorCommand(job.getKey())
          .errorCode("INVALID_CHECKPOINT_TYPE")
          .errorMessage("Unknown checkpoint type: " + typeStr)
          .send();
      return;
    }

    backupRequestHandler
        .checkpoint(type)
        .whenComplete(
            (checkpointId, error) -> {
              if (error != null) {
                jobClient
                    .newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(error.getMessage())
                    .send();
              } else {
                jobClient
                    .newCompleteCommand(job.getKey())
                    .variable("checkpointId", checkpointId)
                    .send();
              }
            });
  }

  @Override
  public void close() {
    if (worker != null) {
      worker.close();
    }
  }
}
