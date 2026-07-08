/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.starter;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.impl.CamundaClientImpl;
import io.camunda.zeebe.config.SuspendResumeChurnProperties;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SuspendResumeProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SuspendResumeProcessInstanceResponse;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quick-and-dirty driver for the process instance suspend/resume POC (#56552) during a load test —
 * track (c) of the benchmark plan. Not a reviewed feature; disabled unless explicitly enabled via
 * {@code load-tester.suspend-resume-churn.enabled}.
 *
 * <p>Periodically finds a batch of this load test's own active, not-already-suspended process
 * instances (via the search API, filtered by process definition id) and suspends them, then
 * schedules a resume after a configurable delay — via a raw {@code GatewayStub} opened alongside
 * {@link CamundaClient}, since the new {@code SuspendResumeProcessInstance} RPC isn't exposed
 * through the official client SDK. Reuses {@link CamundaClientImpl}'s public {@code
 * buildChannel}/{@code buildGatewayStub} helpers so the raw stub matches the client's own TLS/auth
 * configuration exactly.
 *
 * <p>A locally-tracked {@code currentlySuspended} set excludes instances this churner has already
 * suspended (and not yet resumed) from the next selection. Without it the churner re-selects its
 * own suspended instances every cycle — they still read as ACTIVE in the search index because the
 * POC doesn't export the suspended flag — and the engine rejects the redundant SUSPEND
 * (INVALID_STATE), flooding the run with rejected commands that pollute the very metrics under
 * study. The set keeps the load a clean stream of genuine suspend/resume pairs.
 *
 * <p>In phased mode ({@code phased=true}) churn alternates OFF/ON every {@code phaseDuration},
 * starting OFF, and exports the current phase as the gauge {@code
 * suspend_resume_churn_phase_active} (0/1). This gives a repeated A/B on one warm cluster with no
 * restarts: correlate backpressure against that gauge in Prometheus to attribute any delta to the
 * churn rather than to warm-up or small-denominator noise.
 */
public class SuspendResumeChurner implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(SuspendResumeChurner.class);

  private final CamundaClient client;
  private final String processDefinitionId;
  private final SuspendResumeChurnProperties config;
  private final ScheduledExecutorService executor;
  private final ManagedChannel channel;
  private final GatewayStub gatewayStub;
  private final Set<Long> currentlySuspended = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean phaseActive = new AtomicBoolean(true);
  private final Counter suspendedCounter;
  private final Counter resumedCounter;
  private final Counter errorCounter;

  public SuspendResumeChurner(
      final CamundaClient client,
      final String processDefinitionId,
      final SuspendResumeChurnProperties config,
      final MeterRegistry registry,
      final ScheduledExecutorService executor) {
    this.client = client;
    this.processDefinitionId = processDefinitionId;
    this.config = config;
    this.executor = executor;
    channel = CamundaClientImpl.buildChannel(client.getConfiguration());
    gatewayStub = CamundaClientImpl.buildGatewayStub(channel, client.getConfiguration());
    suspendedCounter =
        Counter.builder("suspend_resume_churn_suspended_total")
            .description("Number of process instances suspended by the suspend/resume churner")
            .register(registry);
    resumedCounter =
        Counter.builder("suspend_resume_churn_resumed_total")
            .description("Number of process instances resumed by the suspend/resume churner")
            .register(registry);
    errorCounter =
        Counter.builder("suspend_resume_churn_errors_total")
            .description("Number of failed suspend/resume calls issued by the churner")
            .register(registry);
    Gauge.builder("suspend_resume_churn_phase_active", phaseActive, b -> b.get() ? 1 : 0)
        .description("1 while the churner is in an active (ON) phase, 0 while OFF")
        .register(registry);
  }

  /** Starts the periodic suspend/resume cycle. */
  public void start() {
    LOG.info(
        "Starting suspend/resume churn: interval={}, batchSize={}, resumeDelay={}, phased={},"
            + " phaseDuration={}",
        config.getInterval(),
        config.getBatchSize(),
        config.getResumeDelay(),
        config.isPhased(),
        config.getPhaseDuration());
    if (config.isPhased()) {
      // start OFF so the first measured phase is a clean churn-free baseline on the warm cluster
      phaseActive.set(false);
      executor.scheduleAtFixedRate(
          this::togglePhase,
          config.getPhaseDuration().toMillis(),
          config.getPhaseDuration().toMillis(),
          TimeUnit.MILLISECONDS);
    }
    executor.scheduleAtFixedRate(
        this::churnCycle,
        config.getInterval().toMillis(),
        config.getInterval().toMillis(),
        TimeUnit.MILLISECONDS);
  }

  private void togglePhase() {
    // only this single scheduled task mutates the flag, so a plain get/set is race-free here
    final boolean active = !phaseActive.get();
    phaseActive.set(active);
    LOG.info("suspend/resume churn phase -> {}", active ? "ON" : "OFF");
  }

  private void churnCycle() {
    if (!phaseActive.get()) {
      return;
    }
    // Over-fetch so that after excluding already-suspended instances there are still enough fresh
    // ones to fill the batch.
    final int candidateLimit =
        Math.min(100, Math.max(config.getBatchSize() * 5, config.getBatchSize()));
    client
        .newProcessInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(processDefinitionId).state(ProcessInstanceState.ACTIVE))
        .page(p -> p.limit(candidateLimit))
        .send()
        .thenAccept(
            response ->
                response.items().stream()
                    .map(pi -> pi.getProcessInstanceKey())
                    .filter(key -> !currentlySuspended.contains(key))
                    .limit(config.getBatchSize())
                    .forEach(this::suspendThenScheduleResume))
        .exceptionally(
            error -> {
              LOG.warn("suspend/resume churn: failed to query active instances", error);
              errorCounter.increment();
              return null;
            });
  }

  private void suspendThenScheduleResume(final long processInstanceKey) {
    final var request =
        SuspendResumeProcessInstanceRequest.newBuilder()
            .setProcessInstanceKey(processInstanceKey)
            .setResume(false)
            .build();
    gatewayStub.suspendResumeProcessInstance(
        request,
        new StreamObserver<SuspendResumeProcessInstanceResponse>() {
          @Override
          public void onNext(final SuspendResumeProcessInstanceResponse value) {
            currentlySuspended.add(processInstanceKey);
            suspendedCounter.increment();
            executor.schedule(
                () -> resume(processInstanceKey),
                config.getResumeDelay().toMillis(),
                TimeUnit.MILLISECONDS);
          }

          @Override
          public void onError(final Throwable t) {
            LOG.debug("suspend/resume churn: suspend failed for {}", processInstanceKey, t);
            errorCounter.increment();
          }

          @Override
          public void onCompleted() {}
        });
  }

  private void resume(final long processInstanceKey) {
    final var request =
        SuspendResumeProcessInstanceRequest.newBuilder()
            .setProcessInstanceKey(processInstanceKey)
            .setResume(true)
            .build();
    gatewayStub.suspendResumeProcessInstance(
        request,
        new StreamObserver<SuspendResumeProcessInstanceResponse>() {
          @Override
          public void onNext(final SuspendResumeProcessInstanceResponse value) {
            currentlySuspended.remove(processInstanceKey);
            resumedCounter.increment();
          }

          @Override
          public void onError(final Throwable t) {
            // Drop it from the tracked set regardless so a failed resume doesn't strand the key
            // and slowly starve the fresh-instance pool.
            currentlySuspended.remove(processInstanceKey);
            LOG.debug("suspend/resume churn: resume failed for {}", processInstanceKey, t);
            errorCounter.increment();
          }

          @Override
          public void onCompleted() {}
        });
  }

  @Override
  public void close() {
    channel.shutdownNow();
  }
}
