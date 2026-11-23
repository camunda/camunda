/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.camunda.application.commons.configuration.GatewayBasedConfiguration;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.security.spring.oidc.ScopedJwtDecoderFactory;
import io.camunda.security.spring.oidc.ScopedOidcClaimsProviderFactory;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.gateway.impl.SpringGatewayBridge;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.agrona.LangUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Entry point for the gateway modules by using the {@link io.camunda.application.Profile#GATEWAY}
 * profile, so that the appropriate gateway application properties are applied.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = {"io.camunda.zeebe.gateway", "io.camunda.zeebe.shared"},
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.zeebe\\.gateway\\.rest\\..*")
    })
@Profile("gateway")
public class GatewayModuleConfiguration {

  private static final Logger LOGGER = Loggers.GATEWAY_LOGGER;

  @Bean(destroyMethod = "close")
  public Gateway gateway(
      final GatewayBasedConfiguration configuration,
      final PhysicalTenantResolver physicalTenantResolver,
      final SpringGatewayBridge springGatewayBridge,
      final ActorScheduler actorScheduler,
      final AtomixCluster atomixCluster,
      final BrokerClient brokerClient,
      final JobStreamClient jobStreamClient,
      @Autowired(required = false) final ServiceRegistry serviceRegistry,
      final PasswordEncoder passwordEncoder,
      @Autowired(required = false) final ScopedJwtDecoderFactory scopedJwtDecoderFactory,
      @Autowired(required = false)
          final ScopedOidcClaimsProviderFactory scopedOidcClaimsProviderFactory,
      final MeterRegistry meterRegistry,
      final GatewayRestConfiguration gatewayRestConfiguration) {
    LOGGER.info(
        "Starting standalone gateway {} with version {}",
        configuration.config().getCluster().getMemberId(),
        VersionUtil.getVersion());

    // doing it async as potentially slow (messagingService → unicastService →
    // membershipService → communicationService → eventService).
    final CompletableFuture<Void> atomixClusterStartFuture = atomixCluster.start();
    awaitStartup(() -> jobStreamClient.start().get());

    // before we can add the job stream client as a topology listener, we need to wait for the
    // topology to be set up, otherwise the callback may be lost
    brokerClient.getTopologyManager().addTopologyListener(jobStreamClient);

    final var gateway =
        GatewayFactory.create(
            configuration.shutdownTimeout(),
            configuration.config(),
            physicalTenantResolver,
            brokerClient,
            actorScheduler,
            jobStreamClient.streamer(),
            passwordEncoder,
            meterRegistry,
            gatewayRestConfiguration.getMaxNameFieldLength(),
            serviceRegistry,
            scopedJwtDecoderFactory,
            scopedOidcClaimsProviderFactory);
    springGatewayBridge.registerGatewayStatusSupplier(gateway::getStatus);
    springGatewayBridge.registerClusterStatesSupplier(
        () -> clusterStatesByPhysicalTenant(brokerClient, physicalTenantResolver));
    springGatewayBridge.registerJobStreamClient(() -> jobStreamClient);

    GatewayFactory.startOrClose(
        gateway,
        () -> {
          atomixClusterStartFuture.join();
          awaitStartup(() -> gateway.start().get(30, TimeUnit.SECONDS));
        });
    LOGGER.info("Standalone gateway is started!");

    return gateway;
  }

  /**
   * Spring interrupts the startup thread when the application is closed (e.g. on SIGTERM) while
   * still starting up. Surface this as an explicit, descriptive failure instead of a bare {@link
   * InterruptedException}, and preserve the thread's interrupt flag. Any other failure is rethrown
   * as is, like {@code ActorFuture#join} would.
   */
  private static void awaitStartup(final Callable<?> startupStep) {
    try {
      startupStep.call();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.info("Standalone gateway startup was interrupted, likely due to shutdown");
      throw new IllegalStateException(
          "Standalone gateway startup was interrupted, likely because the application is"
              + " shutting down",
          e);
    } catch (final Exception e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  /**
   * Builds the cluster topology for every configured physical tenant (partition group), keyed by
   * physical tenant id. Used by health indicators that must not privilege the default physical
   * tenant and instead aggregate readiness across all of them (OR-semantics).
   *
   * <p>Groups for which {@link BrokerTopologyManager#getTopology(String)} returns {@code null}
   * (permitted by its contract) are skipped rather than collected, since {@link
   * java.util.stream.Collectors#toMap} does not tolerate null values.
   */
  private static Map<String, BrokerClusterState> clusterStatesByPhysicalTenant(
      final BrokerClient brokerClient, final PhysicalTenantIds physicalTenantIds) {
    final var topologyManager = brokerClient.getTopologyManager();

    final Map<String, BrokerClusterState> clusterStates = new HashMap<>();
    for (final var physicalTenantId : physicalTenantIds.known()) {
      final var topology = topologyManager.getTopology(physicalTenantId);
      if (topology != null) {
        clusterStates.put(physicalTenantId, topology);
      }
    }
    return clusterStates;
  }
}
