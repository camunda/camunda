/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.clusterversion.ClusterVersionFeatures;
import io.camunda.zeebe.engine.processing.job.behaviour.JobUpdateBehaviour;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JobUpdateProcessor implements TypedRecordProcessor<JobRecord> {

  private final JobUpdateBehaviour jobUpdateBehaviour;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final StateWriter stateWriter;
  private final ClusterVersionFeatures clusterVersionFeatures;

  public JobUpdateProcessor(
      final JobUpdateBehaviour jobUpdateBehaviour,
      final Writers writers,
      final ClusterVersionFeatures clusterVersionFeatures) {
    this.jobUpdateBehaviour = jobUpdateBehaviour;
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    stateWriter = writers.state();
    this.clusterVersionFeatures = clusterVersionFeatures;
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> command) {
    final long jobKey = command.getKey();
    jobUpdateBehaviour
        .checkJobCommand(command)
        .flatMap(job -> jobUpdateBehaviour.isAuthorized(command, job))
        .ifRightOrLeft(
            job -> {
              final Set<String> changeset = command.getValue().getChangedAttributes();

              // Pass 1: validate all requested fields — no events written yet
              final List<String> errors = new ArrayList<>();
              if (changeset.contains(JobRecord.RETRIES)) {
                jobUpdateBehaviour
                    .validateJobRetries(jobKey, command.getValue().getRetries())
                    .ifPresent(errors::add);
              }
              if (changeset.contains(JobRecord.TIMEOUT)) {
                jobUpdateBehaviour.validateJobTimeout(jobKey, job).ifPresent(errors::add);
              }
              if (changeset.contains(JobRecord.PRIORITY)
                  && !clusterVersionFeatures.isActive(Capability.JOB_PRIORITIZATION)) {
                // ECV gate: PRIORITY_UPDATED is a new event intent (PR #51333). Emitting it on a
                // log that older replicas might replay would surface as an unknown intent. Reject
                // loudly until the operator raises ECV to JOB_PRIORITIZATION; other fields in the
                // same changeset are also rejected — the command is treated as atomic.
                errors.add(
                    "Cannot update priority: the cluster has not activated the '"
                        + Capability.JOB_PRIORITIZATION.name()
                        + "' capability. Raise the cluster's ECV before sending priority updates"
                        + " — for example: POST /v2/cluster-version/raise {\"capability\":\""
                        + Capability.JOB_PRIORITIZATION.name()
                        + "\"}.");
              }
              if (!errors.isEmpty()) {
                handleRejection(errors, command);
                return;
              }

              // Pass 2: all validations passed — apply and emit events
              if (changeset.contains(JobRecord.RETRIES)) {
                jobUpdateBehaviour.applyJobRetries(jobKey, command.getValue().getRetries(), job);
              }
              if (changeset.contains(JobRecord.TIMEOUT)) {
                jobUpdateBehaviour.applyJobTimeout(jobKey, command.getValue().getTimeout(), job);
              }
              if (changeset.contains(JobRecord.PRIORITY)) {
                jobUpdateBehaviour.applyJobPriority(jobKey, command.getValue().getPriority(), job);
              }

              stateWriter.appendFollowUpEvent(jobKey, JobIntent.UPDATED, job);
              responseWriter.writeEventOnCommand(jobKey, JobIntent.UPDATED, job, command);
            },
            rejection -> {
              rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
              responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
            });
  }

  private void handleRejection(final List<String> errors, final TypedRecord<JobRecord> command) {
    final String errorMessage = String.join(", ", errors);
    rejectionWriter.appendRejection(command, RejectionType.INVALID_ARGUMENT, errorMessage);
    responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_ARGUMENT, errorMessage);
  }
}
