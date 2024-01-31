/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.job;

import static io.camunda.zeebe.scheduler.clock.ActorClock.currentTimeMillis;

import com.google.rpc.Code;
import com.google.rpc.Status;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.grpc.ServerStreamObserver;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.metrics.LongPollingMetrics;
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

  private final Map<String, InFlightLongPollingActivateJobsRequestsState> jobTypeState =
      new ConcurrentHashMap<>();
  private final Duration longPollingTimeout;
  private final long probeTimeoutMillis;
  private final int failedAttemptThreshold;

  private final LongPollingMetrics metrics;

  private ActorControl actor;

  private LongPollingActivateJobsHandler(
      final BrokerClient brokerClient,
      final long maxMessageSize,
      final long longPollingTimeout,
      final long probeTimeoutMillis,
      final int failedAttemptThreshold) {
    this.brokerClient = brokerClient;
    activateJobsHandler = new RoundRobinActivateJobsHandler(brokerClient, maxMessageSize);
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

  void onActorStarted() {
    actor.run(
        () -> {
          brokerClient.subscribeJobAvailableNotification(
              JOBS_AVAILABLE_TOPIC, this::onJobAvailableNotification);
          actor.runAtFixedRate(Duration.ofMillis(probeTimeoutMillis), this::probe);
        });
  }

  /***
   *
   *                                                 +--------------------------------------------------------------------------------+
   *                                                 |                                   BROKER(s)                                    |
   *                                                 +--------|-------------------------------^---------------------------------------+
   *                                                          |                               |
   * +-----------+      +-------------------------------------|-------------------------------|--------------------------------------------------------------------------------+
   * |           |      |                                     |          GATEWAY              | Activate commands/responses                                                    |
   * |           |      |                                     |                               |                                                                                |
   * |           |      |                                     |                               |                                                                                |
   * |           |      |                                     |                               |                                                                                |
   * |           |      |   +-----------------------+         |          +--------------------v-------------------+ No Jobs   +-------------------------------+                |
   * |         --|------|--->   Incoming Activate   ----------|---------->   Try to Activate on all partitions    ------------|   Handle No Jobs Activated    |                |
   * |           |      |   +-----------------------+         |          +---------|----------^-------------------+           +-------/---------------|-------+                |
   * |           |      |                        +------------------------+        |          |                                      |                |                        |
   * |           |      |               +---------  Jobs available        |        |          |                                      /                |                        |
   * |           |      |               |        |  Notification          |        |          |                    +----------------v-----+           |                        |
   * |           |      |               |        +------------------------+        |          |               +---->Complete Or Resubmit  |           |                        |
   * |           |      |               |                                          |          |               |    +-----|----------|-----+           |                        |
   * | Client  <-|------|--------+--------------------------+----------------------+          |               |          |          |                 |                        |
   * |           |      |        |                          |        Received Jobs            |               |          |          |                 |                        |
   * |           |      |        | Handle Pending Requests  |                                  \              |          | +--------v-------+         |   Any request          |
   * |           |      |        |                          |                                  |     Reached  |          | |  Mark Pending  |         |   contained            |
   * |           |      |        +--------------------------+                                  |     failure  |          | +----------------+         |   resource exhausted   |
   * |           |      |                      |                                               |     threshold|          |                            |                        |
   * |           |      |                      |                                               |              |          |                            |                        |
   * |           |      |                      |                                               |              |          |Retrieved                   |                        |
   * |           |      |                      |                                               |              |          |notify                      |                        |
   * |           |      |                      |  For all pending requests                     |              |          |in between                  |                        |
   * |           |      |                      |                                       +-------|--------------|-------+  |                            |                        |
   * |           |      |                      +---------------------------------------> Internal Activate Jobs Retry <--+                            |                        |
   * |           |      |                                                              +------------------------------+                               |                        |
   * |           |      |                                                                                                                             |                        |
   * |           |------|-----------                                                                                                                  |                        |
   * +-----------+      |           \-------------------------------------                                                         +------------------v-----+                  |
   *                    |                                         Error   \-------------------------------------                   |                        |                  |
   *                    |                                                                                       \-------------------   Resource Exhausted   |                  |
   *                    |                                                                                                          |                        |                  |
   *                    |                                                                                                          +------------------------+                  |
   *                    |                                                                                                                                                      |
   *                    |                                                                                                                                                      |
   *                    |                                                                                                                                                      |
   *                    +------------------------------------------------------------------------------------------------------------------------------------------------------+
   *
   * https://textik.com/#a2725e317ed87a9d
   * @param request The request to handle
   * @param responseObserver The stream to write the responses to
   */
  @Override
  public void activateJobs(
      final BrokerActivateJobsRequest request,
      final ServerStreamObserver<ActivateJobsResponse> responseObserver,
      final long requestTimeout) {
    final var longPollingRequest =
        new InflightActivateJobsRequest(
            ACTIVATE_JOBS_REQUEST_ID_GENERATOR.getAndIncrement(),
            request,
            responseObserver,
            requestTimeout);
    final String jobType = longPollingRequest.getType();
    // Eagerly removing the request on cancellation may free up some resources
    // We are not allowed to change the responseObserver after we completed this call
    // this means we can't do it async in the following actor call.
    responseObserver.setOnCancelHandler(() -> onRequestCancel(jobType, longPollingRequest));
    actor.run(
        () -> {
          final InFlightLongPollingActivateJobsRequestsState state =
              jobTypeState.computeIfAbsent(
                  jobType, type -> new InFlightLongPollingActivateJobsRequestsState(type, metrics));

          tryToActivateJobsOnAllPartitions(state, longPollingRequest);
        });
  }

  private void onRequestCancel(
      final String type, final InflightActivateJobsRequest longPollingRequest) {
    actor.run(
        () -> {
          final var state = jobTypeState.get(type);
          if (state != null) {
            state.removeRequest(longPollingRequest);
          }
        });
  }

  private void tryToActivateJobsOnAllPartitions(
      final InFlightLongPollingActivateJobsRequestsState state,
      final InflightActivateJobsRequest request) {

    final BrokerClusterState topology = brokerClient.getTopologyManager().getTopology();
    if (topology != null) {
      state.addActiveRequest(request);

      final int partitionsCount = topology.getPartitionsCount();
      activateJobsHandler.activateJobs(
          partitionsCount,
          request,
          error ->
              actor.submit(
                  () -> {
                    request.onError(error);
                    state.removeActiveRequest(request);
                  }),
          (remainingAmount, containedResourceExhaustedResponse) -> {
            final boolean noJobsActivated = remainingAmount == request.getMaxJobsToActivate();
            if (noJobsActivated) {
              handleNoReceivedJobsFromAllPartitions(
                  state, request, containedResourceExhaustedResponse);
            } else {
              actor.submit(
                  () -> {
                    request.complete();
                    state.removeActiveRequest(request);
                    state.resetFailedAttempts();
                    handlePendingRequests(state, request.getType());
                  });
            }
          });
    }
  }

  private void handleNoReceivedJobsFromAllPartitions(
      final InFlightLongPollingActivateJobsRequestsState state,
      final InflightActivateJobsRequest request,
      final Boolean containedResourceExhaustedResponse) {
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

    final var state =
        jobTypeState.computeIfAbsent(
            request.getType(),
            type1 -> new InFlightLongPollingActivateJobsRequestsState(type1, metrics));

    if (!request.hasScheduledTimer()) {
      scheduleLongPollingTimeout(state, request);
    }

    if (activateImmediately) {
      // try now if notification arrived
      internalActivateJobsRetry(request);
    } else {
      // we will react on probes and incoming notifications
      markRequestAsPending(state, request);
    }
  }

  void internalActivateJobsRetry(final InflightActivateJobsRequest request) {
    actor.run(
        () -> {
          final String jobType = request.getType();
          final InFlightLongPollingActivateJobsRequestsState state =
              jobTypeState.computeIfAbsent(
                  jobType, type -> new InFlightLongPollingActivateJobsRequestsState(type, metrics));

          if (state.shouldAttempt(failedAttemptThreshold)) {
            tryToActivateJobsOnAllPartitions(state, request);
          } else {
            completeOrResubmitRequest(request, false);
          }
        });
  }

  private void onJobAvailableNotification(final String jobType) {
    LOG.trace("Received jobs available notification for type {}.", jobType);

    // instead of calling #getJobTypeState(), do only a
    // get to avoid the creation of a state instance.
    final var state = jobTypeState.get(jobType);

    if (state != null && state.shouldNotifyAndStartNotification()) {
      LOG.trace("Handle jobs available notification for type {}.", jobType);
      actor.run(
          () -> {
            state.resetFailedAttempts();
            handlePendingRequests(state, jobType);
            state.completeNotification();
          });
    } else {
      LOG.trace("Ignore jobs available notification for type {}.", jobType);
    }
  }

  private void handlePendingRequests(
      final InFlightLongPollingActivateJobsRequestsState state, final String jobType) {
    final Queue<InflightActivateJobsRequest> pendingRequests = state.getPendingRequests();

    if (!pendingRequests.isEmpty()) {
      pendingRequests.forEach(
          nextPendingRequest -> {
            LOG.trace("Unblocking ActivateJobsRequest {}", nextPendingRequest.getRequest());
            internalActivateJobsRetry(nextPendingRequest);
          });
    } else {
      if (!state.hasActiveRequests()) {
        jobTypeState.remove(jobType);
      }
    }
  }

  private void markRequestAsPending(
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

  private void scheduleLongPollingTimeout(
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
              tryToActivateJobsOnAllPartitions(state, probeRequest);
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
    private long maxMessageSize;
    private long longPollingTimeout = DEFAULT_LONG_POLLING_TIMEOUT;
    private long probeTimeoutMillis = DEFAULT_PROBE_TIMEOUT;
    private int minEmptyResponses = EMPTY_RESPONSE_THRESHOLD;

    public Builder setBrokerClient(final BrokerClient brokerClient) {
      this.brokerClient = brokerClient;
      return this;
    }

    public Builder setMaxMessageSize(final long maxMessageSize) {
      this.maxMessageSize = maxMessageSize;
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
          brokerClient, maxMessageSize, longPollingTimeout, probeTimeoutMillis, minEmptyResponses);
    }
  }
}
