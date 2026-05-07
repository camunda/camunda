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
import io.camunda.zeebe.backup.retention.BackupRetention;

/**
 * Handles the {@code retention-trigger} job emitted by the retention cleanup BPMN. Each tick
 * delegates to {@link BackupRetention#triggerRetention()}, which performs a single retention pass
 * on the actor thread.
 */
public final class RetentionTriggerJobWorker implements AutoCloseable {

  public static final String JOB_TYPE = "retention-trigger";

  private final CamundaClient camundaClient;
  private final BackupRetention backupRetention;
  private JobWorker worker;

  public RetentionTriggerJobWorker(
      final CamundaClient camundaClient, final BackupRetention backupRetention) {
    this.camundaClient = camundaClient;
    this.backupRetention = backupRetention;
  }

  public void start() {
    worker = camundaClient.newWorker().jobType(JOB_TYPE).handler(this::handleJob).open();
  }

  private void handleJob(final JobClient jobClient, final ActivatedJob job) {
    backupRetention
        .triggerRetention()
        .whenComplete(
            (deletedBackupIds, error) -> {
              if (error != null) {
                jobClient
                    .newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(error.getMessage())
                    .send();
              } else {
                jobClient
                    .newCompleteCommand(job.getKey())
                    .variable(
                        "deletedBackupIds",
                        deletedBackupIds == null ? java.util.List.of() : deletedBackupIds)
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
