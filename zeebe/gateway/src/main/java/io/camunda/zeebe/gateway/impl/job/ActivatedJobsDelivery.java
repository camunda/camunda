/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.camunda.zeebe.gateway.impl.job.JobActivationResult.ActivatedJob;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Hands the jobs of one broker response to the client. Jobs that cannot be delivered, because they
 * exceed the maximum message size or the client is gone, are failed so that they can be activated
 * again.
 */
final class ActivatedJobsDelivery<T> {

  private static final String ACTIVATE_JOB_NOT_SENT_MSG = "Failed to send activated jobs to client";
  private static final String ACTIVATE_JOB_NOT_SENT_MSG_WITH_REASON =
      ACTIVATE_JOB_NOT_SENT_MSG + ", failed with: %s";
  private static final String MAX_MESSAGE_SIZE_EXCEEDED_MSG =
      "the response is bigger than the maximum allowed message size %d";

  private final BrokerClient brokerClient;
  private final long maxMessageSize;
  private final Function<JobActivationResponse, JobActivationResult<T>> activationResultMapper;

  ActivatedJobsDelivery(
      final BrokerClient brokerClient,
      final long maxMessageSize,
      final Function<JobActivationResponse, JobActivationResult<T>> activationResultMapper) {
    this.brokerClient = brokerClient;
    this.maxMessageSize = maxMessageSize;
    this.activationResultMapper = activationResultMapper;
  }

  /**
   * Sends the jobs of the given response to the client.
   *
   * @param actor the actor on which failures to reactivate jobs are reported
   * @return the number of jobs that were sent, or why the jobs could not be sent
   */
  Outcome deliver(
      final ActorControl actor,
      final InflightActivateJobsRequest<T> request,
      final BrokerResponse<JobBatchRecord> brokerResponse) {
    final var response = brokerResponse.getResponse();
    final var partitionGroup = request.getRequest().getPartitionGroup();
    final JobActivationResult<T> jobActivationResult =
        activationResultMapper.apply(
            new JobActivationResponse(
                brokerResponse.getKey(), response, maxMessageSize, partitionGroup));

    final List<ActivatedJob> jobsToDefer = jobActivationResult.getJobsToDefer();
    if (!jobsToDefer.isEmpty()) {
      final var jobKeys = jobsToDefer.stream().map(ActivatedJob::key).toList();
      final var reason = String.format(MAX_MESSAGE_SIZE_EXCEEDED_MSG, maxMessageSize);

      logResponseNotSent(request.getType(), jobKeys, reason);
      reactivateJobs(actor, jobsToDefer, reason, partitionGroup);
    }

    final var jobsCount = jobActivationResult.getJobsCount();
    if (jobsCount > 0) {
      final var result =
          request.tryToSendActivatedJobs(jobActivationResult.getActivateJobsResponse());
      final var responseWasSent = result.getOrElse(false);

      if (!responseWasSent) {
        final var reason = createReasonMessage(result);

        logResponseNotSent(request.getType(), response.getJobKeys(), reason);
        reactivateJobs(actor, jobActivationResult.getJobs(), reason, partitionGroup);
        return new Outcome(0, reason);
      }
    }

    return new Outcome(jobsCount, null);
  }

  private String createReasonMessage(final Either<Exception, Boolean> resultValue) {
    final String errorMessage;
    if (resultValue.isLeft()) {
      final var exception = resultValue.getLeft();
      errorMessage = String.format(ACTIVATE_JOB_NOT_SENT_MSG_WITH_REASON, exception.getMessage());
    } else {
      errorMessage = ACTIVATE_JOB_NOT_SENT_MSG;
    }
    return errorMessage;
  }

  private void reactivateJobs(
      final ActorControl actor,
      final List<ActivatedJob> activateJobs,
      final String message,
      final String partitionGroup) {
    if (activateJobs != null) {
      activateJobs.forEach(j -> tryToReactivateJob(actor, j, message, partitionGroup));
    }
  }

  private void tryToReactivateJob(
      final ActorControl actor,
      final ActivatedJob job,
      final String message,
      final String partitionGroup) {
    final var request = toFailJobRequest(job, message, partitionGroup);
    brokerClient
        .sendRequestWithRetry(request)
        .whenCompleteAsync(
            (response, error) -> {
              if (error != null) {
                Loggers.GATEWAY_LOGGER.info(
                    "Failed to reactivate job {} due to {}", job.key(), error.getMessage());
              } else if (!response.isResponse()) {
                Loggers.GATEWAY_LOGGER.info(
                    "Failed to reactivate job {} due to broker response {}", job.key(), response);
              }
            },
            actor);
  }

  private BrokerFailJobRequest toFailJobRequest(
      final ActivatedJob job, final String errorMessage, final String partitionGroup) {
    final BrokerFailJobRequest brokerFailJobRequest =
        new BrokerFailJobRequest(job.key(), job.retries(), 0).setErrorMessage(errorMessage);
    brokerFailJobRequest.setAuthorization(Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, true));
    brokerFailJobRequest.setPartitionGroup(partitionGroup);
    return brokerFailJobRequest;
  }

  private void logResponseNotSent(
      final String jobType, final List<Long> jobKeys, final String reason) {
    Loggers.GATEWAY_LOGGER.info(
        "Failed to send {} activated jobs for type {} (with job keys: {}) to client, because: {}",
        jobKeys.size(),
        jobType,
        jobKeys,
        reason);
  }

  /**
   * @param jobsCount number of jobs sent to the client; zero if they could not be sent
   * @param undeliveredReason why the response could not be sent, or null if it was sent (or empty)
   */
  record Outcome(int jobsCount, @Nullable String undeliveredReason) {

    boolean wasUndelivered() {
      return undeliveredReason != null;
    }
  }
}
