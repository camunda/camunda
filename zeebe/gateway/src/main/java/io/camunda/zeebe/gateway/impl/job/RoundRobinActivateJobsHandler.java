/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.job;

import com.google.rpc.Code;
import com.google.rpc.Status;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.broker.client.impl.PartitionIdIterator;
import io.camunda.zeebe.broker.client.impl.RoundRobinDispatchStrategy;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.ResponseMapper;
import io.camunda.zeebe.gateway.ResponseMapper.JobActivationResult;
import io.camunda.zeebe.gateway.grpc.ServerStreamObserver;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.util.Either;
import io.grpc.protobuf.StatusProto;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Iterates in round-robin fashion over partitions to activate jobs. Uses a map from job type to
 * partition-IDs to determine the next partition to use.
 */
public final class RoundRobinActivateJobsHandler implements ActivateJobsHandler {

  private static final String ACTIVATE_JOB_NOT_SENT_MSG = "Failed to send activated jobs to client";
  private static final String ACTIVATE_JOB_NOT_SENT_MSG_WITH_REASON =
      ACTIVATE_JOB_NOT_SENT_MSG + ", failed with: %s";
  private static final String MAX_MESSAGE_SIZE_EXCEEDED_MSG =
      "the response is bigger than the maximum allowed message size %d";

  private final Map<String, RoundRobinDispatchStrategy> jobTypeToNextPartitionId =
      new ConcurrentHashMap<>();
  private final BrokerClient brokerClient;
  private final BrokerTopologyManager topologyManager;
  private final long maxMessageSize;

  private ActorControl actor;

  public RoundRobinActivateJobsHandler(final BrokerClient brokerClient, final long maxMessageSize) {
    this.brokerClient = brokerClient;
    topologyManager = brokerClient.getTopologyManager();
    this.maxMessageSize = maxMessageSize;
  }

  @Override
  public void accept(final ActorControl actor) {
    this.actor = actor;
  }

  @Override
  public void activateJobs(
      final BrokerActivateJobsRequest request,
      final ServerStreamObserver<ActivateJobsResponse> responseObserver,
      final long requestTimeout) {
    final var topology = topologyManager.getTopology();
    if (topology != null) {
      final var inflightRequest =
          new InflightActivateJobsRequest(
              ACTIVATE_JOBS_REQUEST_ID_GENERATOR.getAndIncrement(),
              request,
              responseObserver,
              requestTimeout);
      activateJobs(
          topology.getPartitionsCount(),
          inflightRequest,
          responseObserver::onError,
          (remainingAmount, resourceExhaustedWasPresent) -> responseObserver.onCompleted());
    }
  }

  public void activateJobs(
      final int partitionsCount,
      final InflightActivateJobsRequest request,
      final Consumer<Throwable> onError,
      final BiConsumer<Integer, Boolean> onCompleted) {
    final var jobType = request.getType();
    final var maxJobsToActivate = request.getMaxJobsToActivate();
    final var partitionIterator = partitionIdIteratorForType(jobType, partitionsCount);

    final var requestState =
        new InflightActivateJobsRequestState(partitionIterator, maxJobsToActivate);
    final var delegate = new ResponseObserverDelegate(onError, onCompleted);

    activateJobs(request, requestState, delegate);
  }

  private void activateJobs(
      final InflightActivateJobsRequest request,
      final InflightActivateJobsRequestState requestState,
      final ResponseObserverDelegate delegate) {
    actor.run(
        () -> {
          if (!request.isOpen()) {
            return;
          }

          if (requestState.shouldActivateJobs()) {
            final var brokerRequest = request.getRequest();
            final var partitionId = requestState.getNextPartition();
            final var remainingAmount = requestState.getRemainingAmount();

            // partitions to check and jobs to activate left
            brokerRequest.setPartitionId(partitionId);
            brokerRequest.setMaxJobsToActivate(remainingAmount);

            brokerClient
                .sendRequest(brokerRequest)
                .whenComplete(handleBrokerResponse(request, requestState, delegate));

          } else {
            // enough jobs activated or no more partitions left to check
            final var remainingAmount = requestState.getRemainingAmount();
            final var resourceExhaustedWasPresent = requestState.wasResourceExhaustedPresent();
            delegate.onCompleted(remainingAmount, resourceExhaustedWasPresent);
          }
        });
  }

  private BiConsumer<BrokerResponse<JobBatchRecord>, Throwable> handleBrokerResponse(
      final InflightActivateJobsRequest request,
      final InflightActivateJobsRequestState requestState,
      final ResponseObserverDelegate delegate) {
    return (brokerResponse, error) -> {
      if (error == null) {
        handleResponseSuccess(request, requestState, delegate, brokerResponse);
      } else {
        handleResponseError(request, requestState, delegate, error);
      }
    };
  }

  private void handleResponseSuccess(
      final InflightActivateJobsRequest request,
      final InflightActivateJobsRequestState requestState,
      final ResponseObserverDelegate delegate,
      final BrokerResponse<JobBatchRecord> brokerResponse) {
    actor.run(
        () -> {
          final var response = brokerResponse.getResponse();
          final JobActivationResult jobActivationResult =
              ResponseMapper.toActivateJobsResponse(
                  brokerResponse.getKey(), response, maxMessageSize);

          final List<ActivatedJob> jobsToDefer = jobActivationResult.jobsToDefer();
          if (!jobsToDefer.isEmpty()) {
            final var jobKeys = jobsToDefer.stream().map(ActivatedJob::getKey).toList();
            final var jobType = request.getType();
            final var reason = String.format(MAX_MESSAGE_SIZE_EXCEEDED_MSG, maxMessageSize);

            logResponseNotSent(jobType, jobKeys, reason);
            reactivateJobs(jobsToDefer, reason);
          }

          final ActivateJobsResponse grpcResponse = jobActivationResult.activateJobsResponse();
          final var jobsCount = grpcResponse.getJobsCount();
          final var jobsActivated = jobsCount > 0;
          if (jobsActivated) {
            final var result = request.tryToSendActivatedJobs(grpcResponse);
            final var responseWasSent = result.getOrElse(false);

            if (!responseWasSent) {
              final var activatedJobsToReactivate = grpcResponse.getJobsList();
              final var jobKeys = response.getJobKeys();
              final var jobType = request.getType();
              final var reason = createReasonMessage(result);

              logResponseNotSent(jobType, jobKeys, reason);
              reactivateJobs(activatedJobsToReactivate, reason);
              cancelActivateJobsRequest(reason, delegate);
              return;
            }
          }

          final var remainingJobsToActivate = requestState.getRemainingAmount() - jobsCount;
          final var shouldPollCurrentPartitionAgain = response.getTruncated();

          requestState.setRemainingAmount(remainingJobsToActivate);
          requestState.setPollPrevPartition(shouldPollCurrentPartitionAgain);
          activateJobs(request, requestState, delegate);
        });
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

  private void reactivateJobs(final List<ActivatedJob> activateJobs, final String message) {
    if (activateJobs != null) {
      activateJobs.forEach(j -> tryToReactivateJob(j, message));
    }
  }

  private void tryToReactivateJob(final ActivatedJob job, final String message) {
    final var request = toFailJobRequest(job, message);
    brokerClient
        .sendRequestWithRetry(request)
        .whenComplete(
            (response, error) -> {
              if (error != null) {
                Loggers.GATEWAY_LOGGER.info(
                    "Failed to reactivate job {} due to {}", job.getKey(), error.getMessage());
              }
            });
  }

  private BrokerFailJobRequest toFailJobRequest(final ActivatedJob job, final String errorMessage) {
    return new BrokerFailJobRequest(job.getKey(), job.getRetries(), 0)
        .setErrorMessage(errorMessage);
  }

  private void cancelActivateJobsRequest(
      final String reason, final ResponseObserverDelegate delegate) {
    final var status = Status.newBuilder().setCode(Code.CANCELLED_VALUE).setMessage(reason).build();
    delegate.onError(StatusProto.toStatusException(status));
  }

  private void handleResponseError(
      final InflightActivateJobsRequest request,
      final InflightActivateJobsRequestState state,
      final ResponseObserverDelegate delegate,
      final Throwable error) {
    actor.run(
        () -> {
          final var wasResourceExhausted = wasResourceExhausted(error);
          if (isRejection(error)) {
            delegate.onError(error);
            return;
          } else if (!wasResourceExhausted) {
            logErrorResponse(state.getCurrentPartition(), request.getType(), error);
          }

          state.setResourceExhaustedWasPresent(wasResourceExhausted);
          state.setPollPrevPartition(false);
          activateJobs(request, state, delegate);
        });
  }

  private boolean isRejection(final Throwable error) {
    return error != null && BrokerRejectionException.class.isAssignableFrom(error.getClass());
  }

  private boolean wasResourceExhausted(final Throwable error) {
    if (error instanceof final BrokerErrorException brokerError) {
      return brokerError.getError().getCode() == ErrorCode.RESOURCE_EXHAUSTED;
    }

    return false;
  }

  private void logErrorResponse(final int partition, final String jobType, final Throwable error) {
    Loggers.GATEWAY_LOGGER.warn(
        "Failed to activate jobs for type {} from partition {}", jobType, partition, error);
  }

  private void logResponseNotSent(
      final String jobType, final List<Long> jobKeys, final String reason) {
    Loggers.GATEWAY_LOGGER.debug(
        "Failed to send {} activated jobs for type {} (with job keys: {}) to client, because: {}",
        jobKeys.size(),
        jobType,
        jobKeys,
        reason);
  }

  private PartitionIdIterator partitionIdIteratorForType(
      final String jobType, final int partitionsCount) {
    final var nextPartitionSupplier =
        jobTypeToNextPartitionId.computeIfAbsent(jobType, t -> new RoundRobinDispatchStrategy());
    return new PartitionIdIterator(
        nextPartitionSupplier.determinePartition(topologyManager),
        partitionsCount,
        topologyManager);
  }

  private record ResponseObserverDelegate(
      Consumer<Throwable> onErrorDelegate, BiConsumer<Integer, Boolean> onCompletedDelegate) {

    public void onError(final Throwable t) {
      onErrorDelegate.accept(t);
    }

    public void onCompleted(final int remainingAmount, final boolean resourceExhaustedWasPresent) {
      onCompletedDelegate.accept(remainingAmount, resourceExhaustedWasPresent);
    }
  }
}
