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
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Shared logic for the {@code AGENT_HISTORY}/{@code AGENT_INSTANCE} processors that deal with an
 * embedded {@code history[]} batch: validating the job a batch is attributed to, validating the
 * shape of the batch itself, and (in a later commit) applying it to an {@code AgentInstanceRecord}.
 */
public final class AgentHistoryBatchHelper {

  /** The names of the {@code AgentInstanceRecord} attributes a history item can affect. */
  static final Set<String> ALLOWED_CONFIGURATION_ATTRIBUTES =
      Set.of(
          AgentInstanceRecord.ATTR_MODEL,
          AgentInstanceRecord.ATTR_PROVIDER,
          AgentInstanceRecord.ATTR_SYSTEM_PROMPT,
          AgentInstanceRecord.ATTR_TOOLS,
          AgentInstanceRecord.ATTR_MAX_TOKENS,
          AgentInstanceRecord.ATTR_MAX_MODEL_CALLS,
          AgentInstanceRecord.ATTR_MAX_TOOL_CALLS);

  static final String ERROR_MSG_HISTORY_ITEM_ID_MISSING =
      "Expected to process history item at index %d, but historyItemId is missing (got empty "
          + "string). Every history item must have a non-empty historyItemId.";
  static final String ERROR_MSG_ROLE_UNSPECIFIED =
      "Expected to process history item with historyItemId '%s', but its role is UNSPECIFIED. "
          + "Every history item must declare a role.";
  static final String ERROR_MSG_LOOP_ITERATION_MISSING =
      "Expected to process history item with historyItemId '%s', but loopIteration is missing "
          + "(got %d). Every history item must declare a positive loopIteration.";
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
  private static final String ERROR_MSG_UNKNOWN_ATTRIBUTES =
      "Expected to update agent instance configuration with history item '%s',"
          + " but changedAttributes contained unknown attribute(s) %s. Allowed attributes are: %s.";

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

  /**
   * Validates every item in the batch, in order: {@code historyItemId} must be non-empty, {@code
   * role} must not be {@code UNSPECIFIED}, {@code loopIteration} must be a positive integer (the
   * {@code 0} default means the field was left unset), and — for {@link
   * AgentHistoryRole#CONFIGURATION} items only — every name in {@code changedAttributes} must be
   * one this helper actually knows how to apply.
   *
   * @return the rejection for the first invalid item found, or {@link Either#rightVoid()} if the
   *     whole batch is valid
   */
  public Either<Rejection, Void> validateHistory(
      final List<? extends AgentHistoryRecordValue> history) {
    if (history == null || history.isEmpty()) {
      return Either.rightVoid();
    }

    for (int i = 0; i < history.size(); i++) {
      final var item = history.get(i);
      final var historyItemId = item.getHistoryItemId();

      if (historyItemId.isEmpty()) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_ARGUMENT, ERROR_MSG_HISTORY_ITEM_ID_MISSING.formatted(i)));
      }

      if (item.getRole() == AgentHistoryRole.UNSPECIFIED) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_ARGUMENT,
                ERROR_MSG_ROLE_UNSPECIFIED.formatted(historyItemId)));
      }

      if (item.getLoopIteration() < 1) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_ARGUMENT,
                ERROR_MSG_LOOP_ITERATION_MISSING.formatted(
                    historyItemId, item.getLoopIteration())));
      }

      if (item.getRole() == AgentHistoryRole.CONFIGURATION) {
        final var unknown =
            item.getChangedAttributes().stream()
                .filter(Predicate.not(ALLOWED_CONFIGURATION_ATTRIBUTES::contains))
                .toList();
        if (!unknown.isEmpty()) {
          return Either.left(
              new Rejection(
                  RejectionType.INVALID_ARGUMENT,
                  ERROR_MSG_UNKNOWN_ATTRIBUTES.formatted(
                      historyItemId, unknown, ALLOWED_CONFIGURATION_ATTRIBUTES)));
        }
      }
    }
    return Either.rightVoid();
  }
}
