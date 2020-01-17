/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.job;

import static io.zeebe.util.sched.clock.ActorClock.currentTimeMillis;

import io.grpc.stub.StreamObserver;
import io.zeebe.gateway.Loggers;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.zeebe.gateway.metrics.LongPollingMetrics;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ScheduledTimer;
import io.zeebe.util.sched.clock.ActorClock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import org.slf4j.Logger;

public final class LongPollingActivateJobsHandler extends Actor {

  private static final String JOBS_AVAILABLE_TOPIC = "jobsAvailable";
  private static final Logger LOG = Loggers.GATEWAY_LOGGER;

  private final ActivateJobsHandler activateJobsHandler;
  private final BrokerClient brokerClient;

  // jobType -> state
  private final Map<String, JobTypeAvailabilityState> jobTypeState = new HashMap<>();
  private final Duration longPollingTimeout;
  private final long probeTimeoutMillis;
  private final int emptyResponseThreshold;

  private final LongPollingMetrics metrics;

  private LongPollingActivateJobsHandler(
      final BrokerClient brokerClient,
      final long longPollingTimeout,
      final long probeTimeoutMillis,
      final int emptyResponseThreshold) {
    this.brokerClient = brokerClient;
    this.activateJobsHandler = new ActivateJobsHandler(brokerClient);
    this.longPollingTimeout = Duration.ofMillis(longPollingTimeout);
    this.probeTimeoutMillis = probeTimeoutMillis;
    this.emptyResponseThreshold = emptyResponseThreshold;
    metrics = new LongPollingMetrics();
  }

  @Override
  public String getName() {
    return "GatewayLongPollingJobHandler";
  }

  public void activateJobs(
      final ActivateJobsRequest request,
      final StreamObserver<ActivateJobsResponse> responseObserver) {
    final LongPollingActivateJobsRequest longPollingRequest =
        new LongPollingActivateJobsRequest(request, responseObserver);
    activateJobs(longPollingRequest);
  }

  public void activateJobs(final LongPollingActivateJobsRequest request) {
    actor.run(
        () -> {
          final JobTypeAvailabilityState state = jobTypeState.get(request.getType());
          final boolean isJobAvailable =
              state == null || (state.getEmptyResponses() < emptyResponseThreshold);
          if (isJobAvailable) {
            activateJobsUnchecked(request);
          } else {
            block(state, request);
          }
        });
  }

  private void activateJobsUnchecked(final LongPollingActivateJobsRequest request) {
    final BrokerClusterState topology = brokerClient.getTopologyManager().getTopology();
    if (topology != null) {
      final int partitionsCount = topology.getPartitionsCount();
      activateJobsHandler.activateJobs(
          partitionsCount,
          request.getRequest(),
          request.getMaxJobsToActivate(),
          request.getType(),
          response -> onResponse(request, response),
          remainingAmount -> onCompleted(request, remainingAmount));
    }
  }

  @Override
  protected void onActorStarted() {
    brokerClient.subscribeJobAvailableNotification(JOBS_AVAILABLE_TOPIC, this::onNotification);
    actor.runAtFixedRate(Duration.ofMillis(probeTimeoutMillis), this::probe);
  }

  private void onNotification(final String jobType) {
    LOG.trace("Received jobs available notification for type {}.", jobType);
    actor.call(() -> jobsAvailable(jobType));
  }

  private void onCompleted(
      final LongPollingActivateJobsRequest request, final Integer remainingAmount) {
    if (remainingAmount == request.getMaxJobsToActivate()) {
      actor.submit(() -> jobsNotAvailable(request));
    } else {
      actor.submit(request::complete);
    }
  }

  private void onResponse(
      final LongPollingActivateJobsRequest request,
      final ActivateJobsResponse activateJobsResponse) {
    actor.submit(
        () -> {
          request.onResponse(activateJobsResponse);
          jobsAvailable(request.getType());
        });
  }

  private void jobsNotAvailable(final LongPollingActivateJobsRequest request) {
    final JobTypeAvailabilityState state =
        jobTypeState.computeIfAbsent(
            request.getType(), type -> new JobTypeAvailabilityState(type, metrics));
    state.incrementEmptyResponses(currentTimeMillis());
    block(state, request);
  }

  private void jobsAvailable(final String jobType) {
    final JobTypeAvailabilityState removedState = jobTypeState.remove(jobType);
    if (removedState != null) {
      unblockRequests(removedState);
    }
  }

  private void unblockRequests(final JobTypeAvailabilityState state) {
    final Queue<LongPollingActivateJobsRequest> requests = state.getBlockedRequests();
    if (requests == null) {
      return;
    }
    requests.stream()
        .filter((r) -> !r.isCanceled())
        .forEach(
            request -> {
              LOG.trace("Unblocking ActivateJobsRequest {}", request.getRequest());
              activateJobs(request);
            });
    state.clearBlockedRequests();
  }

  private void block(
      final JobTypeAvailabilityState state, final LongPollingActivateJobsRequest request) {
    if (request.isLongPollingDisabled()) {
      request.complete();
      return;
    }
    if (!request.isTimedOut()) {
      LOG.debug(
          "Jobs of type {} not available. Blocking request {}",
          request.getType(),
          request.getRequest());
      state.blockRequest(request);
      if (!request.hasScheduledTimer()) {
        addTimeOut(state, request);
      }
    }
  }

  private void addTimeOut(
      final JobTypeAvailabilityState state, final LongPollingActivateJobsRequest request) {
    ActorClock.currentTimeMillis();
    final Duration requestTimeout = request.getLongPollingTimeout(longPollingTimeout);
    final ScheduledTimer timeout =
        actor.runDelayed(
            requestTimeout,
            () -> {
              LOG.debug(
                  "Remove blocking request {} for job type {} after timeout of {}",
                  request.getRequest(),
                  request.getType(),
                  requestTimeout);
              state.removeBlockedRequest(request);
              request.timeout();
            });
    request.setScheduledTimer(timeout);
  }

  private void probe() {
    final long now = currentTimeMillis();
    jobTypeState.forEach(
        (type, state) -> {
          if (state.getLastUpdatedTime() < (now - probeTimeoutMillis)) {
            state.removeCanceledRequests();

            final LongPollingActivateJobsRequest probeRequest = state.pollBlockedRequests();
            if (probeRequest != null) {
              activateJobsUnchecked(probeRequest);
            } else {
              // there are no blocked requests, so use next request as probe
              if (state.getEmptyResponses() >= emptyResponseThreshold) {
                state.resetEmptyResponses(emptyResponseThreshold - 1);
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
