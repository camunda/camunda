/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.broker.client.impl.PartitionIdIterator;
import io.camunda.zeebe.broker.client.impl.RoundRobinDispatchStrategy;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.camunda.zeebe.gateway.impl.job.JobActivationResult.ActivatedJob;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Iterates in round-robin fashion over partitions to activate jobs. Uses a map from job type to
 * partition-IDs to determine the next partition to use.
 *
 * <p>The map is bounded to the {@link #MAX_TRACKED_JOB_TYPES} most frequently used job types
 * (W-TinyLFU), as job types are user input and thus unbounded. Entries idle for {@link
 * #IDLE_CURSOR_EXPIRATION} are dropped, as their position is stale by then and dropping it frees
 * the slot for active types. Job types that are dropped lose their round-robin cursor and restart
 * at a random partition when seen again, so their first request does not concentrate on one
 * partition even when the tracked set churns.
 */
public final class RoundRobinActivateJobsHandler<T> implements ActivateJobsHandler<T> {

  static final int MAX_TRACKED_JOB_TYPES = 1024;
  static final Duration IDLE_CURSOR_EXPIRATION = Duration.ofSeconds(30);

  private static final String ACTIVATE_JOB_NOT_SENT_MSG = "Failed to send activated jobs to client";
  private static final String ACTIVATE_JOB_NOT_SENT_MSG_WITH_REASON =
      ACTIVATE_JOB_NOT_SENT_MSG + ", failed with: %s";
  private static final String MAX_MESSAGE_SIZE_EXCEEDED_MSG =
      "the response is bigger than the maximum allowed message size %d";

  // Bounded because job types are user input, so their domain is unbounded and tracking every
  // type seen over the gateway's lifetime would leak memory.
  private final Cache<String, RoundRobinDispatchStrategy> jobTypeToNextPartitionId;
  private final BrokerClient brokerClient;
  private final BrokerTopologyManager topologyManager;
  private final long maxMessageSize;
  private final Function<JobActivationResponse, JobActivationResult<T>> activationResultMapper;

  private ActorControl actor;
  private final Function<String, Throwable> requestCanceledExceptionProvider;
  private final Supplier<RoundRobinDispatchStrategy> strategyFactory;

  public RoundRobinActivateJobsHandler(
      final BrokerClient brokerClient,
      final long maxMessageSize,
      final Function<JobActivationResponse, JobActivationResult<T>> activationResultMapper,
      final Function<String, Throwable> requestCanceledExceptionProvider) {
    this(
        brokerClient,
        maxMessageSize,
        activationResultMapper,
        requestCanceledExceptionProvider,
        RoundRobinDispatchStrategy::withRandomInitialOffset);
  }

  /**
   * The strategy factory is injectable so tests can predict the start partition; production must
   * keep the random start, which keeps untracked types from concentrating on one partition.
   */
  RoundRobinActivateJobsHandler(
      final BrokerClient brokerClient,
      final long maxMessageSize,
      final Function<JobActivationResponse, JobActivationResult<T>> activationResultMapper,
      final Function<String, Throwable> requestCanceledExceptionProvider,
      final Supplier<RoundRobinDispatchStrategy> strategyFactory) {
    this(
        brokerClient,
        maxMessageSize,
        activationResultMapper,
        requestCanceledExceptionProvider,
        strategyFactory,
        newJobTypeCache());
  }

  /**
   * The cache is injectable so tests can force pending maintenance with {@link Cache#cleanUp()}
   * instead of relying on how the production cache schedules its evictions.
   */
  RoundRobinActivateJobsHandler(
      final BrokerClient brokerClient,
      final long maxMessageSize,
      final Function<JobActivationResponse, JobActivationResult<T>> activationResultMapper,
      final Function<String, Throwable> requestCanceledExceptionProvider,
      final Supplier<RoundRobinDispatchStrategy> strategyFactory,
      final Cache<String, RoundRobinDispatchStrategy> jobTypeToNextPartitionId) {
    this.jobTypeToNextPartitionId = jobTypeToNextPartitionId;
    this.brokerClient = brokerClient;
    topologyManager = brokerClient.getTopologyManager();
    this.maxMessageSize = maxMessageSize;
    this.activationResultMapper = activationResultMapper;
    this.requestCanceledExceptionProvider = requestCanceledExceptionProvider;
    this.strategyFactory = strategyFactory;
  }

  static Cache<String, RoundRobinDispatchStrategy> newJobTypeCache() {
    return Caffeine.newBuilder()
        .maximumSize(MAX_TRACKED_JOB_TYPES)
        // A type idle this long has a stale cursor; dropping it frees the slot for active
        // types, and the lost cursor only randomizes the type's next first request.
        .expireAfterAccess(IDLE_CURSOR_EXPIRATION)
        // Keeps cache maintenance off the JVM-wide common pool, which other components share
        // and could starve; with at most 1024 tiny entries, evicting inline adds negligible
        // latency.
        .executor(Runnable::run)
        .build();
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
          final var response = brokerResponse.getResponse();
          final JobActivationResult<T> jobActivationResult =
              activationResultMapper.apply(
                  new JobActivationResponse(
                      brokerResponse.getKey(),
                      response,
                      maxMessageSize,
                      request.getRequest().getPartitionGroup()));

          final var partitionGroup = request.getRequest().getPartitionGroup();
          final List<ActivatedJob> jobsToDefer = jobActivationResult.getJobsToDefer();
          if (!jobsToDefer.isEmpty()) {
            final var jobKeys = jobsToDefer.stream().map(ActivatedJob::key).toList();
            final var jobType = request.getType();
            final var reason = String.format(MAX_MESSAGE_SIZE_EXCEEDED_MSG, maxMessageSize);

            logResponseNotSent(jobType, jobKeys, reason);
            reactivateJobs(jobsToDefer, reason, partitionGroup);
          }

          final T activateJobsResponse = jobActivationResult.getActivateJobsResponse();
          final var jobsCount = jobActivationResult.getJobsCount();
          final var jobsActivated = jobsCount > 0;
          if (jobsActivated) {
            final var result = request.tryToSendActivatedJobs(activateJobsResponse);
            final var responseWasSent = result.getOrElse(false);

            if (!responseWasSent) {
              final var activatedJobsToReactivate = jobActivationResult.getJobs();
              final var jobKeys = response.getJobKeys();
              final var jobType = request.getType();
              final var reason = createReasonMessage(result);

              logResponseNotSent(jobType, jobKeys, reason);
              reactivateJobs(activatedJobsToReactivate, reason, partitionGroup);
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

  private void reactivateJobs(
      final List<ActivatedJob> activateJobs, final String message, final String partitionGroup) {
    if (activateJobs != null) {
      activateJobs.forEach(j -> tryToReactivateJob(j, message, partitionGroup));
    }
  }

  private void tryToReactivateJob(
      final ActivatedJob job, final String message, final String partitionGroup) {
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
    final var leaseToken = job.jobLeaseToken();
    if (leaseToken != null && !leaseToken.isEmpty()) {
      brokerFailJobRequest.setJobLeaseToken(leaseToken);
    }
    brokerFailJobRequest.setAuthorization(Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, true));
    brokerFailJobRequest.setPartitionGroup(partitionGroup);
    return brokerFailJobRequest;
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

  private void logResponseNotSent(
      final String jobType, final List<Long> jobKeys, final String reason) {
    Loggers.GATEWAY_LOGGER.info(
        "Failed to send {} activated jobs for type {} (with job keys: {}) to client, because: {}",
        jobKeys.size(),
        jobType,
        jobKeys,
        reason);
  }

  private PartitionIdIterator partitionIdIteratorForType(
      final String jobType, final int partitionsCount, final String partitionGroup) {
    final var nextPartitionSupplier =
        jobTypeToNextPartitionId.get(jobType, t -> strategyFactory.get());
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
