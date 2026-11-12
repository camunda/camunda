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
import io.camunda.zeebe.gateway.impl.job.ActivationPlanner.Ask;
import io.camunda.zeebe.gateway.metrics.JobActivationPollStats;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

/**
 * Activates jobs on several partitions at the same time instead of one after the other.
 *
 * <p>The jobs to activate are split over the partitions and asked for in parallel. Every response
 * is sent to the client as it arrives, and may lead to further asks right away; see {@link
 * ActivationPlanner} for how they are planned. Partitions that had jobs recently, or that a broker
 * announced jobs for, are asked first; see {@link PartitionHints}.
 */
final class ParallelActivateJobsHandler<T> implements PartitionedActivateJobsHandler<T> {

  private final Map<String, RoundRobinDispatchStrategy> jobTypeToFirstPartition =
      new ConcurrentHashMap<>();
  private final PartitionHints hints = new PartitionHints();
  private final BrokerClient brokerClient;
  private final BrokerTopologyManager topologyManager;
  private final ActivatedJobsDelivery<T> delivery;
  private final Function<String, Throwable> requestCanceledExceptionProvider;

  private ActorControl actor;

  ParallelActivateJobsHandler(
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
  public void hintJobsAvailable(
      final String partitionGroup, final String jobType, final int partitionId) {
    hints.hint(partitionGroup, jobType, partitionId);
  }

  @Override
  public void activateJobs(
      final int partitionsCount,
      final InflightActivateJobsRequest<T> request,
      final Consumer<Throwable> onError,
      final BiConsumer<Integer, Boolean> onCompleted,
      final Consumer<JobActivationPollStats> onPollFinished) {
    final var partitionGroup = request.getRequest().getPartitionGroup();
    final var firstPartition =
        jobTypeToFirstPartition
            .computeIfAbsent(request.getType(), t -> new RoundRobinDispatchStrategy())
            .determinePartition(topologyManager, partitionGroup);
    final var partitionIds = new ArrayList<Integer>(partitionsCount);
    new PartitionIdIterator(firstPartition, partitionsCount, topologyManager, partitionGroup)
        .forEachRemaining(partitionIds::add);

    final var planner =
        new ActivationPlanner(
            request.getMaxJobsToActivate(),
            partitionIds,
            hints.hinted(partitionGroup, request.getType()));
    sendNextAsks(request, new RequestState(planner, onError, onCompleted, onPollFinished), 1);
  }

  /**
   * Sends what the planner wants to ask, and ends the request once nothing is in flight.
   *
   * @param depth how many asks one after the other led to the asks sent here
   */
  private void sendNextAsks(
      final InflightActivateJobsRequest<T> request, final RequestState state, final int depth) {
    actor.run(
        () -> {
          if (!state.shouldStop(request)) {
            state.planner.nextAsks().forEach(ask -> sendAsk(request, state, ask, depth));
          }
          if (!state.planner.hasInFlightAsks()) {
            state.finish();
          }
        });
  }

  private void sendAsk(
      final InflightActivateJobsRequest<T> request,
      final RequestState state,
      final Ask ask,
      final int depth) {
    state.recordAsk(ask, depth);
    final var brokerRequest = copyForPartition(request.getRequest(), ask);
    brokerClient
        .sendRequest(brokerRequest)
        .whenCompleteAsync(
            (response, error) -> {
              if (error == null && response.isResponse()) {
                handleResponse(request, state, ask, response);
              } else {
                handleError(request, state, ask, error != null ? error : response.toException());
              }
              sendNextAsks(request, state, depth + 1);
            },
            actor);
  }

  private void handleResponse(
      final InflightActivateJobsRequest<T> request,
      final RequestState state,
      final Ask ask,
      final BrokerResponse<JobBatchRecord> brokerResponse) {
    final var response = brokerResponse.getResponse();
    final var returnedJobs = response.getJobKeys().size();
    final var outcome = delivery.deliver(actor, request, brokerResponse, state.planner.remaining());
    state.surplusJobs += outcome.surplusJobs();
    if (outcome.wasUndelivered()) {
      state.fail(requestCanceledExceptionProvider.apply(outcome.undeliveredReason()));
      state.planner.onFailure(ask);
      return;
    }

    state.recordJobs(ask, outcome.jobsCount());
    final var mayHaveMore =
        state.planner.onResponse(ask, outcome.jobsCount(), returnedJobs, response.getTruncated());
    final var partitionGroup = request.getRequest().getPartitionGroup();
    if (mayHaveMore) {
      hints.hint(partitionGroup, request.getType(), ask.partitionId());
    } else {
      hints.clear(partitionGroup, request.getType(), ask.partitionId());
    }
  }

  private void handleError(
      final InflightActivateJobsRequest<T> request,
      final RequestState state,
      final Ask ask,
      final Throwable error) {
    state.planner.onFailure(ask);
    hints.clear(request.getRequest().getPartitionGroup(), request.getType(), ask.partitionId());
    if (error instanceof BrokerRejectionException) {
      state.fail(error);
      return;
    }

    if (wasResourceExhausted(error)) {
      state.resourceExhaustedWasPresent = true;
    } else {
      Loggers.GATEWAY_LOGGER.warn(
          "Failed to activate jobs for type {} from partition {}",
          request.getType(),
          ask.partitionId(),
          error);
    }
  }

  private static boolean wasResourceExhausted(final Throwable error) {
    return error instanceof final BrokerErrorException brokerError
        && brokerError.getError().getCode() == ErrorCode.RESOURCE_EXHAUSTED;
  }

  /**
   * A broker request holds the buffer for its response, so each in-flight ask needs its own
   * instance.
   */
  private static BrokerActivateJobsRequest copyForPartition(
      final BrokerActivateJobsRequest original, final Ask ask) {
    final var record = original.getRequestWriter();
    final var copy =
        new BrokerActivateJobsRequest(record.getType())
            .setWorker(record.getWorker())
            .setTimeout(record.getTimeout())
            .setMaxJobsToActivate(ask.maxJobs())
            .setVariables(variablesOf(record))
            .setTenantIds(record.getTenantIds())
            .setTenantFilter(record.getTenantFilter())
            .setWithLease(record.isWithLease());
    copy.setPartitionId(ask.partitionId());
    copy.setAuthorization(original.getAuthorization().toClaimsMap());
    copy.setOperationReference(original.getOperationReference());
    if (original.hasPartitionGroup()) {
      copy.setPartitionGroup(original.getPartitionGroup());
    }
    return copy;
  }

  private static List<String> variablesOf(final JobBatchRecord record) {
    return StreamSupport.stream(record.variables().spliterator(), false)
        .map(variable -> BufferUtil.bufferAsString(variable.getValue()))
        .toList();
  }

  /** All fields are only accessed on the actor. */
  private static final class RequestState {
    private final ActivationPlanner planner;
    private final Consumer<Throwable> onError;
    private final BiConsumer<Integer, Boolean> onCompleted;
    private final Consumer<JobActivationPollStats> onPollFinished;
    private final Map<Integer, Integer> jobsPerPartition = new TreeMap<>();

    private int requests;
    private int rounds;
    private int surplusJobs;
    private boolean resourceExhaustedWasPresent;
    private boolean finished;
    private @Nullable Throwable failure;

    private RequestState(
        final ActivationPlanner planner,
        final Consumer<Throwable> onError,
        final BiConsumer<Integer, Boolean> onCompleted,
        final Consumer<JobActivationPollStats> onPollFinished) {
      this.planner = planner;
      this.onError = onError;
      this.onCompleted = onCompleted;
      this.onPollFinished = onPollFinished;
    }

    private void recordAsk(final Ask ask, final int depth) {
      requests++;
      rounds = Math.max(rounds, depth);
      jobsPerPartition.putIfAbsent(ask.partitionId(), 0);
    }

    private void recordJobs(final Ask ask, final int jobs) {
      jobsPerPartition.merge(ask.partitionId(), jobs, Integer::sum);
    }

    private boolean shouldStop(final InflightActivateJobsRequest<?> request) {
      return failure != null || !request.isOpen();
    }

    /** Keeps the first failure; it is reported once all in-flight responses have been handled. */
    private void fail(final Throwable error) {
      if (failure == null) {
        failure = error;
      }
    }

    private void finish() {
      if (finished) {
        return;
      }
      finished = true;
      onPollFinished.accept(
          new JobActivationPollStats(
              true, requests, rounds, Map.copyOf(jobsPerPartition), surplusJobs));
      if (failure != null) {
        onError.accept(failure);
      } else {
        onCompleted.accept(planner.remaining(), resourceExhaustedWasPresent);
      }
    }
  }
}
