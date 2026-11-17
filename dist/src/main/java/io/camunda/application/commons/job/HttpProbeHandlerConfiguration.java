/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.job;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.application.commons.job.HttpJobHandlerConfiguration.ActivateJobHandlerConfiguration;
import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerTopologyListener;
import io.camunda.zeebe.gateway.impl.probe.LongPollingActivateProbesHandler;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds and starts the long polling of probe activation for the REST gateway, as {@link
 * HttpJobHandlerConfiguration} does for jobs. Probes share the jobs' long polling configuration and
 * maximum message size.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAnyHttpGatewayEnabled
@NullMarked
public class HttpProbeHandlerConfiguration {

  private static final String ACTOR_NAME = "ActivateProbesHandlerRest";

  @Bean
  public LongPollingActivateProbesHandler activateProbesHandler(
      final ActivateJobHandlerConfiguration config,
      final BrokerClient brokerClient,
      final ActorScheduler scheduler) {
    final var handler =
        new LongPollingActivateProbesHandler(
            brokerClient,
            config.maxMessageSize().toBytes(),
            config.longPolling(),
            GatewayErrorMapper.RESOURCE_EXHAUSTED_EXCEPTION_PROVIDER,
            GatewayErrorMapper.REQUEST_CANCELED_EXCEPTION_PROVIDER);
    // held polls are failed on a cluster purge, so workers poll again, as for jobs
    brokerClient
        .getTopologyManager()
        .addTopologyListener(
            new BrokerTopologyListener() {
              @Override
              public void clusterIncarnationChanged() {
                handler.onClusterIncarnationChanged();
              }
            });
    scheduler.submitActor(Actor.newActor().name(ACTOR_NAME).actorStartedHandler(handler).build());
    return handler;
  }
}
