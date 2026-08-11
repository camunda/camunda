/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Objects;

/**
 * Shared logic for the {@code AGENT_HISTORY}/{@code AGENT_INSTANCE} processors that deal with an
 * embedded {@code history[]} batch: validating the job a batch is attributed to, validating the
 * batch itself, and (in later commits) applying it to an {@code AgentInstanceRecord}.
 */
public final class AgentHistoryBatchHelper {

  static final String ERROR_MSG_JOB_NOT_ACTIVE =
      "Expected job with key '%d' to be active, but it was not.";
  static final String ERROR_MSG_JOB_LEASE_MISMATCH =
      "Expected job with key '%d' to hold the supplied lease, but it did not match. The job may "
          + "have been re-activated.";
  static final String ERROR_MSG_JOB_ELEMENT_MISMATCH =
      "Expected job '%d' to be associated with element instance '%d', but it is associated with element instance '%d'";
  static final String ERROR_MSG_JOB_REQUIRED_FOR_HISTORY =
      "Expected a job to be provided for the embedded history batch, but no jobKey was set."
          + " A history batch must be attributed to the active job that produced it.";

  private final ProcessingState processingState;

  public AgentHistoryBatchHelper(final ProcessingState processingState) {
    this.processingState = processingState;
  }

  /**
   * Validates the job context a command carries. If a history batch is present, {@code jobKey}
   * becomes required — a batch must always be attributed to the job that produced it — otherwise no
   * job at all remains valid. When a job is supplied (with or without a batch), it must refer to a
   * currently-active job, that job's lease token (if any) must match {@code jobLease}, and the job
   * must belong to {@code elementInstanceKey}.
   *
   * @return the active {@link JobRecord} if a job was supplied and is valid, {@code null} wrapped
   *     in {@link Either#right} if no job was supplied and none was required, otherwise the {@link
   *     Rejection} to surface
   */
  public Either<Rejection, JobRecord> validateJobContext(
      final long jobKey,
      final String jobLease,
      final long elementInstanceKey,
      final List<? extends AgentHistoryRecordValue> history) {

    if (jobKey == -1L) {
      if (history != null && !history.isEmpty()) {
        return Either.left(
            new Rejection(RejectionType.INVALID_ARGUMENT, ERROR_MSG_JOB_REQUIRED_FOR_HISTORY));
      } else {
        return Either.right(null);
      }
    }

    final var jobState = processingState.getJobState();
    if (jobState.getState(jobKey) != JobState.State.ACTIVATED) {
      return Either.left(
          new Rejection(RejectionType.NOT_FOUND, ERROR_MSG_JOB_NOT_ACTIVE.formatted(jobKey)));
    }

    final var job = jobState.getJob(jobKey);
    if (job.hasLeaseToken() && !Objects.equals(jobLease, job.getLeaseToken())) {
      return Either.left(
          new Rejection(RejectionType.NOT_FOUND, ERROR_MSG_JOB_LEASE_MISMATCH.formatted(jobKey)));
    }

    final var jobElementInstanceKey = job.getElementInstanceKey();
    if (jobElementInstanceKey != elementInstanceKey) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              ERROR_MSG_JOB_ELEMENT_MISMATCH.formatted(
                  jobKey, elementInstanceKey, jobElementInstanceKey)));
    }

    return Either.right(job);
  }
}
