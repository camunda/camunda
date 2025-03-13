/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.agrona.CloseHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public final class EmbeddedGatewayService implements AutoCloseable {
  private final Gateway gateway;
  private final BrokerClient brokerClient;
  private final JobStreamClient jobStreamClient;
  private final ConcurrencyControl concurrencyControl;

  public EmbeddedGatewayService(
      final Duration shutdownTimeout,
      final BrokerCfg configuration,
      final SecurityConfiguration securityConfiguration,
      final ActorSchedulingService actorScheduler,
      final ConcurrencyControl concurrencyControl,
      final JobStreamClient jobStreamClient,
      final BrokerClient brokerClient,
      final UserServices userServices,
      final PasswordEncoder passwordEncoder,
      final JwtDecoder jwtDecoder,
      final MeterRegistry meterRegistry) {
    this.concurrencyControl = concurrencyControl;
    this.brokerClient = brokerClient;
    this.jobStreamClient = jobStreamClient;
    gateway =
        new Gateway(
            shutdownTimeout,
            configuration.getGateway(),
            securityConfiguration,
            brokerClient,
            actorScheduler,
            jobStreamClient.streamer(),
            userServices,
            passwordEncoder,
            jwtDecoder,
            meterRegistry);
  }

  @Override
  public void close() {
    CloseHelper.closeAll(
        error ->
            Loggers.GATEWAY_LOGGER.warn(
                "Error occurred while shutting down embedded gateway", error),
        gateway,
        brokerClient,
        jobStreamClient);
  }

  public Gateway get() {
    return gateway;
  }

  public ActorFuture<Gateway> start() {
    // before we can add the job stream client as a topology listener, we need to wait for the
    // topology to be set up, otherwise the callback may be lost
    concurrencyControl.runOnCompletion(
        jobStreamClient.start(),
        (ok, error) -> brokerClient.getTopologyManager().addTopologyListener(jobStreamClient));

    return gateway.start();
  }
}
