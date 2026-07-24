/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_LONG_POLLING_EMPTY_RESPONSE_THRESHOLD;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_LONG_POLLING_TIMEOUT;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_PROBE_TIMEOUT;
import static io.camunda.zeebe.scheduler.clock.ActorClock.currentTimeMillis;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.metrics.LongPollingMetrics;
import io.camunda.zeebe.gateway.metrics.LongPollingMetricsFactory;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * Adds long polling to the handling of activate job requests. When there are no jobs available to
 * activate, the response will be kept open.
 */
public final class LongPollingActivateJobsHandler<T> implements ActivateJobsHandler<T> {

  private static final String JOBS_AVAILABLE_TOPIC = "jobsAvailable";
  private static final Logger LOG = Loggers.LONG_POLLING;
  private static final String ERROR_MSG_ACTIVATED_EXHAUSTED =
      "Expected to activate jobs of type '%s', but no jobs available and at least one broker returned 'RESOURCE_EXHAUSTED'. Please try again later.";
  private static final String PURGE_ERROR_MSG =
      "Cluster was purged; pending job activation requests have been cancelled. Please retry.";

  private final RoundRobinActivateJobsHandler<T> activateJobsHandler;
  private final BrokerClient brokerClient;

  private final Map<JobTypeKey, InFlightLongPollingActivateJobsRequestsState<T>> jobTypeState =
      new ConcurrentHashMap<>();
  private final Duration longPollingTimeout;
  private final long probeTimeoutMillis;
  private final int failedAttemptThreshold;

  private final LongPollingMetricsFactory metricsFactory;
  private final Map<String, LongPollingMetrics> metricsByPhysicalTenant = new ConcurrentHashMap<>();

  private ActorControl actor;

  private final Function<String, Exception> resourceExhaustedExceptionProvider;

  private LongPollingActivateJobsHandler(
      final BrokerClient brokerClient,
      final long maxMessageSize,
      final long longPollingTimeout,
      final long probeTimeoutMillis,
      final int failedAttemptThreshold,
      final Function<JobActivationResponse, JobActivationResult<T>> activationResultMapper,
      final Function<String, Exception> resourceExhaustedExceptionProvider,
      final Function<String, Throwable> requestCanceledExceptionProvider,
      final LongPollingMetricsFactory metricsFactory) {
    this.brokerClient = brokerClient;
    activateJobsHandler =
        new RoundRobinActivateJobsHandler<>(
            brokerClient, maxMessageSize, activationResultMapper, requestCanceledExceptionProvider);
    this.resourceExhaustedExceptionProvider = resourceExhaustedExceptionProvider;
    this.longPollingTimeout = Duration.ofMillis(longPollingTimeout);
    this.probeTimeoutMillis = probeTimeoutMillis;
    this.failedAttemptThreshold = failedAttemptThreshold;
    this.metricsFactory = metricsFactory;
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
          subscribeIfNeeded(DEFAULT_PHYSICAL_TENANT_ID);
          actor.runAtFixedRate(Duration.ofMillis(probeTimeoutMillis), this::probe);
        });
  }

  /**
   * Subscribes to the job-available notifications of a physical tenant, so a notification only
   * wakes long-poll requests of its own tenant. Safe to call on every request for a tenant: {@link
   * BrokerClient#subscribeJobAvailableNotification} dedups by topic, so a repeat call for an
   * already-subscribed tenant is a no-op. The default tenant also listens on the legacy,
   * prefix-less topic for rolling-upgrade compat with 8.9 brokers; remove alongside the legacy
   * topic in 8.11.
   */
  private void subscribeIfNeeded(final String physicalTenantId) {
    brokerClient.subscribeJobAvailableNotification(
        topic(physicalTenantId), jobType -> onJobAvailableNotification(physicalTenantId, jobType));
    if (DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)) {
      brokerClient.subscribeJobAvailableNotification(
          JOBS_AVAILABLE_TOPIC, jobType -> onJobAvailableNotification(physicalTenantId, jobType));
    }
  }

  /** The physicalTenantId-scoped topic name, used for every physical tenant, including default. */
  private static String topic(final String physicalTenantId) {
    return physicalTenantId + "-" + JOBS_AVAILABLE_TOPIC;
  }

  /** Returns the {@link LongPollingMetrics} tagged for the given physical tenant. */
  private LongPollingMetrics metricsFor(final String physicalTenantId) {
    return metricsByPhysicalTenant.computeIfAbsent(
        physicalTenantId, metricsFactory::forPhysicalTenant);
  }

  /**
   * Fails all pending and active long-poll requests so clients can reconnect. Called from the
   * topology listener when the cluster incarnation changes (e.g., after a purge). Safe to call
   * before the actor is started (no-op since there are no pending requests).
   */
  public void onClusterIncarnationChanged() {
    if (actor == null) {
      return;
    }
    actor.run(this::failAllOpenRequests);
  }

  private void failAllOpenRequests() {
    final var error = new RuntimeException(PURGE_ERROR_MSG);
    LOG.info(
        "Cluster purge detected, cancelling all pending job activation requests across {} job types",
        jobTypeState.size());
    jobTypeState.forEach((type, state) -> state.failAllRequests(error));
    jobTypeState.clear();
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
   * |           |      |                      |  For next pending request                     |              |          |in between                  |                        |
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
   */
  @Override
  public void activateJobs(
      final BrokerActivateJobsRequest request,
      final ResponseObserver<T> responseObserver,
      final Consumer<Runnable> setCancelHandler,
      final long requestTimeout) {
    final var longPollingRequest =
        new InflightActivateJobsRequest<>(
            ACTIVATE_JOBS_REQUEST_ID_GENERATOR.getAndIncrement(),
            request,
            responseObserver,
            requestTimeout);
    final String jobType = longPollingRequest.getType();
    final String physicalTenantId = request.getPartitionGroup();
    final var key = new JobTypeKey(physicalTenantId, jobType);
    // Eagerly removing the request on cancellation may free up some resources
    // We are not allowed to change the responseObserver after we completed this call
    // this means we can't do it async in the following actor call.
    setCancelHandler.accept(() -> onRequestCancel(key, longPollingRequest));
    actor.run(
        () -> {
          if (!longPollingRequest.isOpen()) {
            return;
          }

          subscribeIfNeeded(physicalTenantId);
          final InFlightLongPollingActivateJobsRequestsState<T> state =
              jobTypeState.computeIfAbsent(
                  key,
                  k ->
                      new InFlightLongPollingActivateJobsRequestsState<>(
                          jobType, metricsFor(physicalTenantId)));

          tryToActivateJobsOnAllPartitions(state, longPollingRequest);
        });
  }

  private void onRequestCancel(
      final JobTypeKey key, final InflightActivateJobsRequest<T> longPollingRequest) {
    actor.run(
        () -> {
          final var state = jobTypeState.get(key);
          if (state != null) {
            state.removeRequest(longPollingRequest);
            state.removeActiveRequest(longPollingRequest);
            tryCleanupJobTypeState(key);
          }
        });
  }

  private void tryToActivateJobsOnAllPartitions(
      final InFlightLongPollingActivateJobsRequestsState<T> state,
      final InflightActivateJobsRequest<T> request) {

    final BrokerClusterState topology =
        brokerClient.getTopologyManager().getTopology(request.getRequest().getPartitionGroup());
    if (topology != null && request.isOpen()) {
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
                    handlePendingRequests(state, keyOf(request));
                  });
            }
          });
    }
  }

  private void handleNoReceivedJobsFromAllPartitions(
      final InFlightLongPollingActivateJobsRequestsState<T> state,
      final InflightActivateJobsRequest<T> request,
      final Boolean containedResourceExhaustedResponse) {
    if (containedResourceExhaustedResponse) {
      actor.submit(
          () -> {
            state.removeActiveRequest(request);
            final var type = request.getType();
            final var errorMsg = String.format(ERROR_MSG_ACTIVATED_EXHAUSTED, type);
            request.onError(resourceExhaustedExceptionProvider.apply(errorMsg));
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
      final InflightActivateJobsRequest<T> request, final boolean activateImmediately) {
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

    if (request.isCanceled()) {
      // already cancelled, nothing to do here
      return;
    }

    final var key = keyOf(request);
    final var state =
        jobTypeState.computeIfAbsent(
            key,
            k ->
                new InFlightLongPollingActivateJobsRequestsState<>(
                    request.getType(), metricsFor(key.physicalTenantId())));

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

  void internalActivateJobsRetry(final InflightActivateJobsRequest<T> request) {
    actor.run(
        () -> {
          if (!request.isOpen()) {
            return;
          }

          final var key = keyOf(request);
          final InFlightLongPollingActivateJobsRequestsState<T> state =
              jobTypeState.computeIfAbsent(
                  key,
                  k ->
                      new InFlightLongPollingActivateJobsRequestsState<>(
                          request.getType(), metricsFor(key.physicalTenantId())));

          if (state.shouldAttempt(failedAttemptThreshold)) {
            tryToActivateJobsOnAllPartitions(state, request);
          } else {
            completeOrResubmitRequest(request, false);
          }
        });
  }

  private void onJobAvailableNotification(final String physicalTenantId, final String jobType) {
    LOG.trace(
        "Received jobs available notification for type {} of physical tenant {}.",
        jobType,
        physicalTenantId);

    final var key = new JobTypeKey(physicalTenantId, jobType);
    // instead of calling #getJobTypeState(), do only a
    // get to avoid the creation of a state instance.
    final var state = jobTypeState.get(key);

    if (state != null) {
      LOG.trace("Handle jobs available notification for type {}.", jobType);
      actor.run(
          () -> {
            state.resetFailedAttempts();
            handlePendingRequests(state, key);
          });
    }
  }

  private void handlePendingRequests(
      final InFlightLongPollingActivateJobsRequestsState<T> state, final JobTypeKey key) {
    final var nextPending = getNextOpenPendingRequest(state);
    if (nextPending != null) {
      LOG.trace("Unblocking ActivateJobsRequest {}", nextPending.getRequest());
      internalActivateJobsRetry(nextPending);
    } else if (!state.hasActiveRequests()) {
      jobTypeState.remove(key);
    }
  }

  private void markRequestAsPending(
      final InFlightLongPollingActivateJobsRequestsState<T> state,
      final InflightActivateJobsRequest<T> request) {
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
      final InFlightLongPollingActivateJobsRequestsState<T> state,
      final InflightActivateJobsRequest<T> request) {
    final Duration requestTimeout = request.getLongPollingTimeout(longPollingTimeout);
    final ScheduledTimer timeout =
        actor.schedule(
            requestTimeout,
            () -> {
              request.timeout();
              state.removeRequest(request);
              tryCleanupJobTypeState(keyOf(request));
            });
    request.setScheduledTimer(timeout);
  }

  private void tryCleanupJobTypeState(final JobTypeKey key) {
    jobTypeState.computeIfPresent(
        key, (k, s) -> s.hasActiveRequests() || s.hasPendingRequests() ? s : null);
  }

  private static JobTypeKey keyOf(final InflightActivateJobsRequest<?> request) {
    return new JobTypeKey(request.getRequest().getPartitionGroup(), request.getType());
  }

  private void probe() {
    final long now = currentTimeMillis();
    jobTypeState.forEach(
        (type, state) -> {
          if (state.getLastUpdatedTime() < (now - probeTimeoutMillis)) {
            final InflightActivateJobsRequest<T> probeRequest = getNextOpenPendingRequest(state);
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

  private InflightActivateJobsRequest<T> getNextOpenPendingRequest(
      final InFlightLongPollingActivateJobsRequestsState<T> state) {
    InflightActivateJobsRequest<T> request;
    while ((request = state.getNextPendingRequest()) != null) {
      if (request.isOpen()) {
        return request;
      }
    }
    return null;
  }

  int activeRequestsCountForJobType(final String physicalTenantId, final String jobType) {
    final var state = jobTypeState.get(new JobTypeKey(physicalTenantId, jobType));
    return state == null ? 0 : state.activeRequestsCount();
  }

  int pendingRequestsCountForJobType(final String physicalTenantId, final String jobType) {
    final var state = jobTypeState.get(new JobTypeKey(physicalTenantId, jobType));
    return state == null ? 0 : state.pendingRequestsCount();
  }

  public static <T> Builder<T> newBuilder() {
    return new Builder<>();
  }

  private record JobTypeKey(String physicalTenantId, String jobType) {}

  public static class Builder<T> {

    private BrokerClient brokerClient;
    private long maxMessageSize;
    private long longPollingTimeout = DEFAULT_LONG_POLLING_TIMEOUT;
    private long probeTimeoutMillis = DEFAULT_PROBE_TIMEOUT;
    // Minimum number of responses with jobCount 0 to infer that no jobs are available
    private int minEmptyResponses = DEFAULT_LONG_POLLING_EMPTY_RESPONSE_THRESHOLD;
    private Function<JobActivationResponse, JobActivationResult<T>> activationResultMapper;
    private Function<String, Exception> resourceExhaustedExceptionProvider;
    private Function<String, Throwable> requestCanceledExceptionProvider;
    private LongPollingMetricsFactory metricsFactory;

    public Builder<T> setBrokerClient(final BrokerClient brokerClient) {
      this.brokerClient = brokerClient;
      return this;
    }

    public Builder<T> setMaxMessageSize(final long maxMessageSize) {
      this.maxMessageSize = maxMessageSize;
      return this;
    }

    public Builder<T> setLongPollingTimeout(final long longPollingTimeout) {
      this.longPollingTimeout = longPollingTimeout;
      return this;
    }

    public Builder<T> setProbeTimeoutMillis(final long probeTimeoutMillis) {
      this.probeTimeoutMillis = probeTimeoutMillis;
      return this;
    }

    public Builder<T> setMinEmptyResponses(final int minEmptyResponses) {
      this.minEmptyResponses = minEmptyResponses;
      return this;
    }

    public Builder<T> setActivationResultMapper(
        final Function<JobActivationResponse, JobActivationResult<T>> activationResultMapper) {
      this.activationResultMapper = activationResultMapper;
      return this;
    }

    public Builder<T> setResourceExhaustedExceptionProvider(
        final Function<String, Exception> resourceExhaustedExceptionProvider) {
      this.resourceExhaustedExceptionProvider = resourceExhaustedExceptionProvider;
      return this;
    }

    public Builder<T> setRequestCanceledExceptionProvider(
        final Function<String, Throwable> requestCanceledExceptionProvider) {
      this.requestCanceledExceptionProvider = requestCanceledExceptionProvider;
      return this;
    }

    public Builder<T> setMetricsFactory(final LongPollingMetricsFactory metricsFactory) {
      this.metricsFactory = metricsFactory;
      return this;
    }

    public LongPollingActivateJobsHandler<T> build() {
      Objects.requireNonNull(brokerClient, "brokerClient");
      return new LongPollingActivateJobsHandler<>(
          brokerClient,
          maxMessageSize,
          longPollingTimeout,
          probeTimeoutMillis,
          minEmptyResponses,
          activationResultMapper,
          resourceExhaustedExceptionProvider,
          requestCanceledExceptionProvider,
          metricsFactory);
    }
  }
}
