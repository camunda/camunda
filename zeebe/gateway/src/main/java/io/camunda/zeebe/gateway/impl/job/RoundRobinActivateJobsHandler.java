/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.broker.client.impl.PartitionIdIterator;
import io.camunda.zeebe.broker.client.impl.RoundRobinDispatchStrategy;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.scheduler.ActorControl;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Iterates in round-robin fashion over partitions to activate jobs. Uses a map from job type to
 * partition-IDs to determine the next partition to use.
 */
public final class RoundRobinActivateJobsHandler<T> implements ActivateJobsHandler<T> {

  private final Map<String, RoundRobinDispatchStrategy> jobTypeToNextPartitionId =
      new ConcurrentHashMap<>();
  private final BrokerClient brokerClient;
  private final BrokerTopologyManager topologyManager;
  private final ActivatedJobsDelivery<T> delivery;

  private ActorControl actor;
  private final Function<String, Throwable> requestCanceledExceptionProvider;

  public RoundRobinActivateJobsHandler(
      final BrokerClient brokerClient,
      final long maxMessageSize,
      final Function<JobActivationResponse, JobActivationResult<T>> activationResultMapper,
      final Function<String, Throwable> requestCanceledExceptionProvider) {
    this.brokerClient = brokerClient;
    topologyManager = brokerClient.getTopologyManager();
    delivery = new ActivatedJobsDelivery<>(brokerClient, maxMessageSize, activationResultMapper);
    this.requestCanceledExceptionProvider = requestCanceledExceptionProvider;
  }

  @Override
  public void accept(final ActorControl actor) {
    this.actor = actor;
  }

  @Override
  public void activateJobs(
      final BrokerActivateJobsRequest request,
      final ResponseObserver<T> responseObserver,
      final Consumer<Runnable> setCancelHandler,
      final long requestTimeout) {
    final var topology = topologyManager.getTopology(request.getPartitionGroup());
    if (topology != null) {
      final var inflightRequest =
          new InflightActivateJobsRequest<>(
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
      final InflightActivateJobsRequest<T> request,
      final Consumer<Throwable> onError,
      final BiConsumer<Integer, Boolean> onCompleted) {
    final var jobType = request.getType();
    final var maxJobsToActivate = request.getMaxJobsToActivate();
    final var partitionIterator =
        partitionIdIteratorForType(
            jobType, partitionsCount, request.getRequest().getPartitionGroup());

    final var requestState =
        new InflightActivateJobsRequestState(partitionIterator, maxJobsToActivate);
    final var delegate = new ResponseObserverDelegate(onError, onCompleted);

    activateJobs(request, requestState, delegate);
  }

  private void activateJobs(
      final InflightActivateJobsRequest<T> request,
      final InflightActivateJobsRequestState requestState,
      final ResponseObserverDelegate delegate) {
    actor.run(
        () -> {
          if (!request.isOpen()) {
            delegate.onCompleted(
                requestState.getRemainingAmount(), requestState.wasResourceExhaustedPresent());
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
                .whenCompleteAsync(handleBrokerResponse(request, requestState, delegate), actor);

          } else {
            // enough jobs activated or no more partitions left to check
            final var remainingAmount = requestState.getRemainingAmount();
            final var resourceExhaustedWasPresent = requestState.wasResourceExhaustedPresent();
            delegate.onCompleted(remainingAmount, resourceExhaustedWasPresent);
          }
        });
  }

  private BiConsumer<BrokerResponse<JobBatchRecord>, Throwable> handleBrokerResponse(
      final InflightActivateJobsRequest<T> request,
      final InflightActivateJobsRequestState requestState,
      final ResponseObserverDelegate delegate) {
    return (brokerResponse, error) -> {
      if (error == null && brokerResponse.isResponse()) {
        handleResponseSuccess(request, requestState, delegate, brokerResponse);
      } else if (error == null) {
        handleBrokerErrorResponse(request, requestState, delegate, brokerResponse);
      } else {
        handleResponseError(request, requestState, delegate, error);
      }
    };
  }

  private void handleResponseSuccess(
      final InflightActivateJobsRequest<T> request,
      final InflightActivateJobsRequestState requestState,
      final ResponseObserverDelegate delegate,
      final BrokerResponse<JobBatchRecord> brokerResponse) {
    actor.run(
        () -> {
          final var outcome = delivery.deliver(actor, request, brokerResponse);
          if (outcome.wasUndelivered()) {
            cancelActivateJobsRequest(outcome.undeliveredReason(), delegate);
            return;
          }

          final var remainingJobsToActivate =
              requestState.getRemainingAmount() - outcome.jobsCount();
          final var shouldPollCurrentPartitionAgain = brokerResponse.getResponse().getTruncated();

          requestState.setRemainingAmount(remainingJobsToActivate);
          requestState.setPollPrevPartition(shouldPollCurrentPartitionAgain);
          activateJobs(request, requestState, delegate);
        });
  }

  private void cancelActivateJobsRequest(
      final String reason, final ResponseObserverDelegate delegate) {
    delegate.onError(requestCanceledExceptionProvider.apply(reason));
  }

  private void handleResponseError(
      final InflightActivateJobsRequest<T> request,
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

  private void handleBrokerErrorResponse(
      final InflightActivateJobsRequest<T> request,
      final InflightActivateJobsRequestState state,
      final ResponseObserverDelegate delegate,
      final BrokerResponse<JobBatchRecord> response) {
    handleResponseError(request, state, delegate, response.toException());
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

  private PartitionIdIterator partitionIdIteratorForType(
      final String jobType, final int partitionsCount, final String partitionGroup) {
    final var nextPartitionSupplier =
        jobTypeToNextPartitionId.computeIfAbsent(jobType, t -> new RoundRobinDispatchStrategy());
    return new PartitionIdIterator(
        nextPartitionSupplier.determinePartition(topologyManager, partitionGroup),
        partitionsCount,
        topologyManager,
        partitionGroup);
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
