/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import java.util.UUID;

/**
 * Handles the {@code cc-complete-change} job type that appears as a terminal service task in the
 * scale-operation and exporter-operation BPMN processes. Submits a {@link
 * ClusterConfigurationIntent#COMPLETE_CHANGE} command to the system partition so the stream
 * processor closes the change plan after all per-broker operations have been applied.
 */
public final class CcCompleteChangeJobWorker implements AutoCloseable {

  static final String JOB_TYPE = "cc-complete-change";

  private final CamundaClient camundaClient;
  private final ClusterConfigCommandSubmitter systemPartition;
  private JobWorker worker;

  public CcCompleteChangeJobWorker(
      final CamundaClient camundaClient, final ClusterConfigCommandSubmitter systemPartition) {
    this.camundaClient = camundaClient;
    this.systemPartition = systemPartition;
  }

  public void start() {
    worker = camundaClient.newWorker().jobType(JOB_TYPE).handler(this::handleJob).open();
  }

  private void handleJob(final JobClient jobClient, final ActivatedJob job) {
    final long version = systemPartition.query().version();
    final var record =
        new ClusterConfigurationRecord()
            .setRequestId(UUID.randomUUID().toString())
            .setExpectedPreviousVersion(version);
    systemPartition
        .submitCommand(ClusterConfigurationIntent.COMPLETE_CHANGE, record)
        .onComplete(
            (reply, err) -> {
              if (err != null) {
                jobClient
                    .newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(err.getMessage())
                    .send();
              } else {
                jobClient.newCompleteCommand(job.getKey()).send();
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
