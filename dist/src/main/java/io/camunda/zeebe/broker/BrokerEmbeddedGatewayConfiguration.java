/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.atomix.cluster.AtomixCluster;
import io.camunda.application.commons.configuration.BrokerBasedConfiguration;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.security.spring.oidc.ScopedJwtDecoderFactory;
import io.camunda.security.spring.oidc.ScopedOidcClaimsProviderFactory;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.GatewayFactory;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClientImpl;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.CloseableSilently;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Starts the gateway next to the broker when {@code zeebe.broker.gateway.enable} is set. The
 * gateway starts after the {@link Broker} bean is fully started, and closes before it.
 *
 * <p>The enable flag is read from the bound {@code BrokerCfg} and not from a property condition,
 * because the unified {@code camunda.api.enabled} only reaches that object, not the environment.
 */
@Configuration(proxyBeanMethods = false)
@Profile("broker")
public class BrokerEmbeddedGatewayConfiguration {

  private static final Logger LOGGER = Loggers.GATEWAY_LOGGER;

  @Bean(destroyMethod = "close")
  public BrokerEmbeddedGateway brokerEmbeddedGateway(
      // only to start the gateway after the broker, and to close it before the broker
      @SuppressWarnings("unused") final Broker broker,
      final BrokerBasedConfiguration configuration,
      final PhysicalTenantResolver physicalTenantResolver,
      final ActorScheduler actorScheduler,
      final AtomixCluster cluster,
      final BrokerClient brokerClient,
      @Autowired(required = false) final ServiceRegistry serviceRegistry,
      final PasswordEncoder passwordEncoder,
      @Autowired(required = false) final ScopedJwtDecoderFactory scopedJwtDecoderFactory,
      @Autowired(required = false)
          final ScopedOidcClaimsProviderFactory scopedOidcClaimsProviderFactory,
      final MeterRegistry meterRegistry) {
    final var brokerCfg = configuration.config();
    if (!brokerCfg.getGateway().isEnable()) {
      return new BrokerEmbeddedGateway(null, null);
    }

    final var jobStreamClient =
        new JobStreamClientImpl(actorScheduler, cluster.getCommunicationService(), meterRegistry);
    final var gateway =
        GatewayFactory.create(
            configuration.shutdownTimeout(),
            brokerCfg.getGateway(),
            physicalTenantResolver,
            brokerClient,
            actorScheduler,
            jobStreamClient.streamer(),
            passwordEncoder,
            meterRegistry,
            brokerCfg.getExperimental().getEngine().getValidators().getMaxNameFieldLength(),
            serviceRegistry,
            scopedJwtDecoderFactory,
            scopedOidcClaimsProviderFactory);
    final var embeddedGateway = new BrokerEmbeddedGateway(gateway, jobStreamClient);

    LOGGER.info("Starting gateway next to the broker");
    GatewayFactory.startOrClose(
        embeddedGateway,
        () -> {
          jobStreamClient.start().join();
          // before we can add the job stream client as a topology listener, we need to wait for
          // the topology to be set up, otherwise the callback may be lost
          brokerClient.getTopologyManager().addTopologyListener(jobStreamClient);
          gateway.start().join(30, TimeUnit.SECONDS);
        });
    return embeddedGateway;
  }

  /** Holds the gateway and its job stream client. Both are absent if the gateway is disabled. */
  @NullMarked
  public record BrokerEmbeddedGateway(
      @Nullable Gateway gateway, @Nullable JobStreamClient jobStreamClient)
      implements CloseableSilently {

    @Override
    public void close() {
      try {
        // closes both, even if the first one throws
        CloseHelper.closeAll(gateway, jobStreamClient);
      } catch (final Exception e) {
        LOGGER.warn("Error occurred while shutting down the gateway next to the broker", e);
      }
    }
  }
}
