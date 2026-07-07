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
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quick-and-dirty driver for the process instance suspend/resume POC (#56552) during a load test —
 * track (c) of the benchmark plan. Not a reviewed feature; disabled unless explicitly enabled via
 * {@code load-tester.suspend-resume-churn.enabled}.
 *
 * <p>Periodically finds a batch of this load test's own active process instances (via the search
 * API, filtered by process definition id) and suspends them, then schedules a resume after a
 * configurable delay — via a raw {@code GatewayStub} opened alongside {@link CamundaClient}, since
 * the new {@code SuspendResumeProcessInstance} RPC isn't exposed through the official client SDK.
 * Reuses {@link CamundaClientImpl}'s public {@code buildChannel}/{@code buildGatewayStub} helpers
 * so the raw stub matches the client's own TLS/auth configuration exactly.
 *
 * <p>Note: because the POC has no exporter/secondary-storage integration, the search index has no
 * idea an instance is suspended — an instance already suspended by a previous cycle can be
 * re-selected and re-suspended, which the engine correctly rejects (INVALID_STATE). That shows up
 * here as a logged error and an incremented error counter, not a crash.
 */
public class SuspendResumeChurner implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(SuspendResumeChurner.class);

  private final CamundaClient client;
  private final String processDefinitionId;
  private final SuspendResumeChurnProperties config;
  private final ScheduledExecutorService executor;
  private final ManagedChannel channel;
  private final GatewayStub gatewayStub;
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
  }

  /** Starts the periodic suspend/resume cycle. */
  public void start() {
    LOG.info(
        "Starting suspend/resume churn: interval={}, batchSize={}, resumeDelay={}",
        config.getInterval(),
        config.getBatchSize(),
        config.getResumeDelay());
    executor.scheduleAtFixedRate(
        this::churnCycle,
        config.getInterval().toMillis(),
        config.getInterval().toMillis(),
        TimeUnit.MILLISECONDS);
  }

  private void churnCycle() {
    client
        .newProcessInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(processDefinitionId).state(ProcessInstanceState.ACTIVE))
        .page(p -> p.limit(config.getBatchSize()))
        .send()
        .thenAccept(
            response ->
                response
                    .items()
                    .forEach(pi -> suspendThenScheduleResume(pi.getProcessInstanceKey())))
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
            suspendedCounter.increment();
            executor.schedule(
                () -> resume(processInstanceKey),
                config.getResumeDelay().toMillis(),
                TimeUnit.MILLISECONDS);
          }

          @Override
          public void onError(final Throwable t) {
            LOG.debug(
                "suspend/resume churn: suspend failed for {} (likely already suspended or"
                    + " completed)",
                processInstanceKey,
                t);
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
            resumedCounter.increment();
          }

          @Override
          public void onError(final Throwable t) {
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
