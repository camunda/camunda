/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.job;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.probe.LongPollingActivateProbesHandler;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sets up the long polling of probe activation for the REST gateway, as {@link
 * HttpJobHandlerConfiguration} does for jobs.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAnyHttpGatewayEnabled
public class HttpProbeHandlerConfiguration {

  /** As the default long polling timeout of job activation; a POC shortcut, not configurable. */
  private static final Duration DEFAULT_LONG_POLLING_TIMEOUT = Duration.ofSeconds(10);

  @Bean
  public LongPollingActivateProbesHandler activateProbesHandler(
      final BrokerClient brokerClient, final ActorScheduler scheduler) {
    final var handler =
        new LongPollingActivateProbesHandler(brokerClient, DEFAULT_LONG_POLLING_TIMEOUT);
    scheduler.submitActor(
        Actor.newActor().name("ActivateProbesHandler").actorStartedHandler(handler).build());
    return handler;
  }
}
