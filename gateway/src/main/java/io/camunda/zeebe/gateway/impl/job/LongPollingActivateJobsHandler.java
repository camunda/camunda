/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.job;

import static io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler.toInflightActivateJobsRequest;
import static io.camunda.zeebe.scheduler.clock.ActorClock.currentTimeMillis;

import com.google.rpc.Code;
import com.google.rpc.Status;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.grpc.ServerStreamObserver;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.camunda.zeebe.gateway.metrics.LongPollingMetrics;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.grpc.protobuf.StatusProto;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * Adds long polling to the handling of activate job requests. When there are no jobs available to
 * activate, the response will be kept open.
 */
public final class LongPollingActivateJobsHandler implements ActivateJobsHandler {

  private static final String JOBS_AVAILABLE_TOPIC = "jobsAvailable";
  private static final Logger LOG = Loggers.LONG_POLLING;
  private static final String ERROR_MSG_ACTIVATED_EXHAUSTED =
      "Expected to activate jobs of type '%s', but no jobs available and at least one broker returned 'RESOURCE_EXHAUSTED'. Please try again later.";

  private final RoundRobinActivateJobsHandler activateJobsHandler;
  private final BrokerClient brokerClient;

  // jobType -> state
  private final Map<String, InFlightLongPollingActivateJobsRequestsState> jobTypeState =
      new ConcurrentHashMap<>();
  private final Duration longPollingTimeout;
  private final long probeTimeoutMillis;
  private final int failedAttemptThreshold;

  private final LongPollingMetrics metrics;

  private ActorControl actor;

  private LongPollingActivateJobsHandler(
      final BrokerClient brokerClient,
      final long longPollingTimeout,
      final long probeTimeoutMillis,
      final int failedAttemptThreshold) {
    this.brokerClient = brokerClient;
    activateJobsHandler = new RoundRobinActivateJobsHandler(brokerClient);
    this.longPollingTimeout = Duration.ofMillis(longPollingTimeout);
    this.probeTimeoutMillis = probeTimeoutMillis;
    this.failedAttemptThreshold = failedAttemptThreshold;
    metrics = new LongPollingMetrics();
  }

  @Override
  public void accept(final ActorControl actor) {
    this.actor = actor;
    activateJobsHandler.accept(actor);
    onActorStarted();
  }

  protected void onActorStarted() {
    actor.run(
        () -> {
          brokerClient.subscribeJobAvailableNotification(
              JOBS_AVAILABLE_TOPIC, this::onNotification);
          actor.runAtFixedRate(Duration.ofMillis(probeTimeoutMillis), this::probe);
        });
  }

  @Override
  public void activateJobs(
      final ActivateJobsRequest request,
      final ServerStreamObserver<ActivateJobsResponse> responseObserver) {
    final var longPollingRequest = toInflightActivateJobsRequest(request, responseObserver);
    activateJobs(longPollingRequest);
  }

  private void completeOrResubmitRequest(
      final InflightActivateJobsRequest request, final boolean activateImmediately) {
    if (request.isLongPollingDisabled()) {
      // request is not supposed to use the
      // long polling capabilities -> just
      // complete the request
      request.complete();
      return;
    }

    if (request.isTimedOut()) {
      // already timed out, nothing to do here
      return;
    }

    final var type = request.getType();
    final var state = getJobTypeState(type);

    if (!request.hasScheduledTimer()) {
      addTimeOut(state, request);
    }

    if (activateImmediately) {
      activateJobs(request);
    } else {
      enqueueRequest(state, request);
    }
  }

  public void activateJobs(final InflightActivateJobsRequest request) {
    actor.run(
        () -> {
          final InFlightLongPollingActivateJobsRequestsState state =
              getJobTypeState(request.getType());

          if (state.shouldAttempt(failedAttemptThreshold)) {
            activateJobsUnchecked(state, request);
          } else {
            completeOrResubmitRequest(request, false);
          }
        });
  }

  private InFlightLongPollingActivateJobsRequestsState getJobTypeState(final String jobType) {
    return jobTypeState.computeIfAbsent(
        jobType, type -> new InFlightLongPollingActivateJobsRequestsState(type, metrics));
  }

  private void activateJobsUnchecked(
      final InFlightLongPollingActivateJobsRequestsState state,
      final InflightActivateJobsRequest request) {

    final BrokerClusterState topology = brokerClient.getTopologyManager().getTopology();
    if (topology != null) {
      state.addActiveRequest(request);

      final int partitionsCount = topology.getPartitionsCount();
      activateJobsHandler.activateJobs(
          partitionsCount,
          request,
          error -> onError(state, request, error),
          (remainingAmount, containedResourceExhaustedResponse) ->
              onCompleted(state, request, remainingAmount, containedResourceExhaustedResponse));
    }
  }

  private void onNotification(final String jobType) {
    LOG.trace("Received jobs available notification for type {}.", jobType);

    // instead of calling #getJobTypeState(), do only a
    // get to avoid the creation of a state instance.
    final var state = jobTypeState.get(jobType);

    if (state != null && state.shouldNotifyAndStartNotification()) {
      LOG.trace("Handle jobs available notification for type {}.", jobType);
      actor.run(
          () -> {
            resetFailedAttemptsAndHandlePendingRequests(jobType);
            state.completeNotification();
          });
    } else {
      LOG.trace("Ignore jobs available notification for type {}.", jobType);
    }
  }

  private void onCompleted(
      final InFlightLongPollingActivateJobsRequestsState state,
      final InflightActivateJobsRequest request,
      final int remainingAmount,
      final boolean containedResourceExhaustedResponse) {

    if (remainingAmount == request.getMaxJobsToActivate()) {
      if (containedResourceExhaustedResponse) {
        actor.submit(
            () -> {
              state.removeActiveRequest(request);
              final var type = request.getType();
              final var errorMsg = String.format(ERROR_MSG_ACTIVATED_EXHAUSTED, type);
              final var status =
                  Status.newBuilder()
                      .setCode(Code.RESOURCE_EXHAUSTED_VALUE)
                      .setMessage(errorMsg)
                      .build();

              request.onError(StatusProto.toStatusException(status));
            });
      } else {
        actor.submit(
            () -> {
              state.incrementFailedAttempts(currentTimeMillis());
              final boolean shouldBeRepeated = state.shouldBeRepeated(request);
              state.removeActiveRequest(request);
              completeOrResubmitRequest(request, shouldBeRepeated);
            });
      }
    } else {
      actor.submit(
          () -> {
            request.complete();
            state.removeActiveRequest(request);
            resetFailedAttemptsAndHandlePendingRequests(request.getType());
          });
    }
  }

  private void onError(
      final InFlightLongPollingActivateJobsRequestsState state,
      final InflightActivateJobsRequest request,
      final Throwable error) {
    actor.submit(
        () -> {
          request.onError(error);
          state.removeActiveRequest(request);
        });
  }

  private void resetFailedAttemptsAndHandlePendingRequests(final String jobType) {
    final InFlightLongPollingActivateJobsRequestsState state = getJobTypeState(jobType);

    state.resetFailedAttempts();

    final Queue<InflightActivateJobsRequest> pendingRequests = state.getPendingRequests();

    if (!pendingRequests.isEmpty()) {
      pendingRequests.forEach(
          nextPendingRequest -> {
            LOG.trace("Unblocking ActivateJobsRequest {}", nextPendingRequest.getRequest());
            activateJobs(nextPendingRequest);
          });
    } else {
      if (!state.hasActiveRequests()) {
        jobTypeState.remove(jobType);
      }
    }
  }

  private void enqueueRequest(
      final InFlightLongPollingActivateJobsRequestsState state,
      final InflightActivateJobsRequest request) {
    LOG.trace(
        "Worker '{}' asked for '{}' jobs of type '{}', but none are available. This request will"
            + " be kept open until a new job of this type is created or until timeout of '{}'.",
        request.getWorker(),
        request.getMaxJobsToActivate(),
        request.getType(),
        request.getLongPollingTimeout(longPollingTimeout));
    state.enqueueRequest(request);
  }

  private void addTimeOut(
      final InFlightLongPollingActivateJobsRequestsState state,
      final InflightActivateJobsRequest request) {
    final Duration requestTimeout = request.getLongPollingTimeout(longPollingTimeout);
    final ScheduledTimer timeout =
        actor.schedule(
            requestTimeout,
            () -> {
              request.timeout();
              state.removeRequest(request);
            });
    request.setScheduledTimer(timeout);
  }

  private void probe() {
    final long now = currentTimeMillis();
    jobTypeState.forEach(
        (type, state) -> {
          if (state.getLastUpdatedTime() < (now - probeTimeoutMillis)) {
            final InflightActivateJobsRequest probeRequest = state.getNextPendingRequest();
            if (probeRequest != null) {
              activateJobsUnchecked(state, probeRequest);
            } else {
              // there are no blocked requests, so use next request as probe
              if (state.getFailedAttempts() >= failedAttemptThreshold) {
                state.setFailedAttempts(failedAttemptThreshold - 1);
              }
            }
          }
        });
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {

    private static final long DEFAULT_LONG_POLLING_TIMEOUT = 10_000; // 10 seconds
    private static final long DEFAULT_PROBE_TIMEOUT = 10_000; // 10 seconds
    // Minimum number of responses with jobCount 0 to infer that no jobs are available
    private static final int EMPTY_RESPONSE_THRESHOLD = 3;

    private BrokerClient brokerClient;
    private long longPollingTimeout = DEFAULT_LONG_POLLING_TIMEOUT;
    private long probeTimeoutMillis = DEFAULT_PROBE_TIMEOUT;
    private int minEmptyResponses = EMPTY_RESPONSE_THRESHOLD;

    public Builder setBrokerClient(final BrokerClient brokerClient) {
      this.brokerClient = brokerClient;
      return this;
    }

    public Builder setLongPollingTimeout(final long longPollingTimeout) {
      this.longPollingTimeout = longPollingTimeout;
      return this;
    }

    public Builder setProbeTimeoutMillis(final long probeTimeoutMillis) {
      this.probeTimeoutMillis = probeTimeoutMillis;
      return this;
    }

    public Builder setMinEmptyResponses(final int minEmptyResponses) {
      this.minEmptyResponses = minEmptyResponses;
      return this;
    }

    public LongPollingActivateJobsHandler build() {
      Objects.requireNonNull(brokerClient, "brokerClient");
      return new LongPollingActivateJobsHandler(
          brokerClient, longPollingTimeout, probeTimeoutMillis, minEmptyResponses);
    }
  }
}
