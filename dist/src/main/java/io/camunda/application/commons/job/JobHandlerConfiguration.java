/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.job;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.configuration.LongPollingCfg;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.RoundRobinActivateJobsHandler;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResult;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.controller.JobActivationRequestResponseObserver;
import io.camunda.zeebe.gateway.rest.controller.ResponseObserverProvider;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
public class JobHandlerConfiguration {

  private final ActivateJobHandlerConfiguration config;
  private final BrokerClient brokerClient;
  private final ActorScheduler scheduler;

  @Autowired
  public JobHandlerConfiguration(
      final ActivateJobHandlerConfiguration config,
      final BrokerClient brokerClient,
      final ActorScheduler scheduler) {
    this.config = config;
    this.brokerClient = brokerClient;
    this.scheduler = scheduler;
  }

  @Bean
  public ResponseObserverProvider responseObserverProvider() {
    return JobActivationRequestResponseObserver::new;
  }

  @Bean
  public ActivateJobsHandler<JobActivationResult> activateJobsHandler() {
    final var handler = buildActivateJobsHandler(brokerClient);
    final var future = new CompletableFuture<ActivateJobsHandler<JobActivationResult>>();
    final var actor =
        Actor.newActor()
            .name(config.actorName())
            .actorStartedHandler(handler.andThen(t -> future.complete(handler)))
            .build();
    scheduler.submitActor(actor);
    return handler;
  }

  private ActivateJobsHandler<JobActivationResult> buildActivateJobsHandler(
      final BrokerClient brokerClient) {
    if (config.longPolling().isEnabled()) {
      return buildLongPollingHandler(brokerClient);
    } else {
      return new RoundRobinActivateJobsHandler<>(
          brokerClient,
          config.maxMessageSize().toBytes(),
          ResponseMapper::toActivateJobsResponse,
          RuntimeException::new);
    }
  }

  private LongPollingActivateJobsHandler<JobActivationResult> buildLongPollingHandler(
      final BrokerClient brokerClient) {
    return LongPollingActivateJobsHandler.<JobActivationResult>newBuilder()
        .setBrokerClient(brokerClient)
        .setMaxMessageSize(config.maxMessageSize().toBytes())
        .setLongPollingTimeout(config.longPolling().getTimeout())
        .setProbeTimeoutMillis(config.longPolling().getProbeTimeout())
        .setMinEmptyResponses(config.longPolling().getMinEmptyResponses())
        .setActivationResultMapper(ResponseMapper::toActivateJobsResponse)
        .setNoJobsReceivedExceptionProvider(RuntimeException::new)
        .setRequestCanceledExceptionProvider(RuntimeException::new)
        .build();
  }

  public static record ActivateJobHandlerConfiguration(
      String actorName, LongPollingCfg longPolling, DataSize maxMessageSize) {}
}
