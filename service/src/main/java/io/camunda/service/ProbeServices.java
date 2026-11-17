/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.RequestRetryHandler;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateProbesRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerProbeAnswerRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerProbeCreateRequest;
import io.camunda.zeebe.gateway.impl.job.ResponseObserver;
import io.camunda.zeebe.gateway.impl.probe.LongPollingActivateProbesHandler;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Creates probes, collects them for workers and records their answers (spec section 4).
 *
 * <p>Creating a probe waits for its end. The deadline is set here, when the request arrives, and
 * the engine uses it as is (D49): time spent before the engine processes {@code CREATE} comes out
 * of the probe's time, never out of the margin this service waits beyond it, so the client gets the
 * engine's answer and not a gateway time-out.
 */
@NullMarked
public final class ProbeServices extends PhysicalTenantScopedApiServices<ProbeServices> {

  /** The cap on a probe's request timeout (D18). */
  public static final Duration MAX_REQUEST_TIMEOUT = Duration.ofSeconds(30);

  /** How much longer than the probe's deadline the gateway waits for the broker (section 10.1). */
  static final Duration RESPONSE_MARGIN = Duration.ofMillis(500);

  private final LongPollingActivateProbesHandler activateProbesHandler;
  private final Duration defaultRequestTimeout;
  private final InstantSource clock;
  private final RequestRetryHandler requestRetryHandler;
  private final ExecutorService executor;

  /**
   * @param clusterRequestTimeout the gateway's cluster request timeout, the default of a probe's
   *     request timeout
   * @param clock the gateway's wall clock, from which the probe's deadline is computed
   */
  public ProbeServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final LongPollingActivateProbesHandler activateProbesHandler,
      final Duration clusterRequestTimeout,
      final InstantSource clock) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.activateProbesHandler = activateProbesHandler;
    defaultRequestTimeout =
        clusterRequestTimeout.compareTo(MAX_REQUEST_TIMEOUT) > 0
            ? MAX_REQUEST_TIMEOUT
            : clusterRequestTimeout;
    this.clock = clock;
    requestRetryHandler = new RequestRetryHandler(brokerClient, brokerClient.getTopologyManager());
    executor = executorProvider.getExecutor();
  }

  /**
   * Creates a probe and waits for its end: the worker's answer, or why it ended without one.
   *
   * <p>A probe has no key yet, so {@code CREATE} goes round-robin to a partition of the physical
   * tenant, and to another one only if the first never wrote the command (D22): no leader, or
   * backpressure. A time-out is never retried, since the probe may exist already.
   */
  public CompletableFuture<ProbeResult> createProbe(
      final ProbeCreateRequest request, final CamundaAuthentication authentication) {
    final Long requested = request.requestTimeout();
    final long requestTimeout =
        requested == null || requested == 0 ? defaultRequestTimeout.toMillis() : requested;
    final long deadline = clock.millis() + requestTimeout;
    final var brokerRequest =
        new BrokerProbeCreateRequest()
            .setProbeId(request.probeId())
            .setInputExpression(request.input())
            .setTenantId(request.tenantId())
            .setRequestTimeout(requestTimeout)
            .setDeadline(deadline);
    applyBrokerRequestMutators(brokerRequest, authentication);

    final CompletableFuture<ProbeResult> result = new CompletableFuture<>();
    requestRetryHandler.sendRequest(
        brokerRequest,
        (key, response) -> result.complete(new ProbeResult(key, response)),
        result::completeExceptionally,
        () -> waitFor(deadline));
    return result.handleAsync(
        (response, error) -> {
          if (error != null) {
            throw ErrorMapper.mapError(error);
          }
          return response;
        },
        executor);
  }

  /** How long to wait for the broker: until {@link #RESPONSE_MARGIN} after the deadline. */
  private Duration waitFor(final long deadline) {
    return Duration.ofMillis(Math.max(1, deadline + RESPONSE_MARGIN.toMillis() - clock.millis()));
  }

  /** Sends a worker's answer to the partition that owns the probe. */
  public CompletableFuture<ProbeRecord> answerProbe(
      final ProbeAnswerRequest request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerProbeAnswerRequest(request.probeKey())
            .setOutcome(request.outcome())
            .setData(request.data())
            .setMessage(request.message()),
        authentication);
  }

  /**
   * Collects probes for a worker's poll, with the long polling of job activation.
   *
   * @param cancelationHandlerConsumer receives the action to run when the worker's request is
   *     cancelled, as for job activation
   */
  public void activateProbes(
      final ProbeActivationRequest request,
      final ResponseObserver<ProbeBatchRecord> responseObserver,
      final Consumer<Runnable> cancelationHandlerConsumer,
      final CamundaAuthentication authentication) {
    final var brokerRequest =
        new BrokerActivateProbesRequest(request.probeIds())
            .setWorker(request.worker())
            .setMaxProbesToActivate(request.maxProbesToActivate())
            .setTenantIds(request.tenantIds())
            .setTenantFilter(request.tenantFilter());
    // the handler bypasses sendBrokerRequest, so it needs the full mutator set: the authorization
    // and the partition group of this physical tenant
    applyBrokerRequestMutators(brokerRequest, authentication);
    final Long requestTimeout = request.requestTimeout();
    activateProbesHandler.activateProbes(
        brokerRequest,
        responseObserver,
        cancelationHandlerConsumer,
        requestTimeout == null ? 0 : requestTimeout);
  }

  public record ProbeCreateRequest(
      String probeId, String input, @Nullable String tenantId, @Nullable Long requestTimeout) {}

  public record ProbeAnswerRequest(
      long probeKey,
      ProbeOutcome outcome,
      @Nullable Map<String, Object> data,
      @Nullable String message) {}

  public record ProbeActivationRequest(
      List<String> probeIds,
      @Nullable String worker,
      int maxProbesToActivate,
      @Nullable Long requestTimeout,
      @Nullable List<String> tenantIds,
      @Nullable TenantFilter tenantFilter) {}

  /** How the probe ended: the record carries the answer, or the reason it ended without one. */
  public record ProbeResult(long probeKey, ProbeRecord probe) {}
}
