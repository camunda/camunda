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
import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.gateway.impl.SpringGatewayBridge;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

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
public class GatewayModuleConfiguration implements CloseableSilently {

  private static final Logger LOGGER = Loggers.GATEWAY_LOGGER;

  private final GatewayBasedConfiguration configuration;
  private final EngineSecurityConfig engineSecurityConfig;
  private final SpringGatewayBridge springGatewayBridge;
  private final ActorScheduler actorScheduler;
  private final AtomixCluster atomixCluster;
  private final BrokerClient brokerClient;
  private final JobStreamClient jobStreamClient;
  private final UserServices userServices;
  private final PasswordEncoder passwordEncoder;
  private final JwtDecoder jwtDecoder;
  private final OidcClaimsProvider oidcClaimsProvider;
  private final MeterRegistry meterRegistry;
  private final int maxVariableNameLength;
  private final PhysicalTenantIds physicalTenantIds;

  private Gateway gateway;

  @Autowired
  public GatewayModuleConfiguration(
      final GatewayBasedConfiguration configuration,
      final CamundaSecurityLibraryProperties securityProperties,
      final SpringGatewayBridge springGatewayBridge,
      final ActorScheduler actorScheduler,
      final AtomixCluster atomixCluster,
      final BrokerClient brokerClient,
      final JobStreamClient jobStreamClient,
      @Autowired(required = false) final UserServices userServices,
      final PasswordEncoder passwordEncoder,
      @Autowired(required = false) final JwtDecoder jwtDecoder,
      @Autowired(required = false) final OidcClaimsProvider oidcClaimsProvider,
      final MeterRegistry meterRegistry,
      final GatewayRestConfiguration gatewayRestConfiguration,
      final PhysicalTenantIds physicalTenantIds) {
    this.configuration = configuration;
    this.engineSecurityConfig =
        new EngineSecurityConfig(
            securityProperties.getAuthentication(),
            securityProperties.getAuthorizations().isEnabled(),
            securityProperties.getMultiTenancy().isChecksEnabled(),
            securityProperties.getInitialization(),
            securityProperties.getCompiledIdValidationPattern(),
            securityProperties.getCompiledGroupIdValidationPattern());
    this.springGatewayBridge = springGatewayBridge;
    this.actorScheduler = actorScheduler;
    this.atomixCluster = atomixCluster;
    this.brokerClient = brokerClient;
    this.jobStreamClient = jobStreamClient;
    this.userServices = userServices;
    this.passwordEncoder = passwordEncoder;
    this.jwtDecoder = jwtDecoder;
    this.oidcClaimsProvider =
        oidcClaimsProvider != null ? oidcClaimsProvider : (jwtClaims, tokenValue) -> jwtClaims;
    this.meterRegistry = meterRegistry;
    maxVariableNameLength = gatewayRestConfiguration.getMaxNameFieldLength();
    this.physicalTenantIds = physicalTenantIds;
  }

  @Bean(destroyMethod = "close")
  public Gateway gateway() {
    LOGGER.info(
        "Starting standalone gateway {} with version {}",
        configuration.config().getCluster().getMemberId(),
        VersionUtil.getVersion());

    // doing it async as potentially slow (messagingService → unicastService →
    // membershipService → communicationService → eventService).
    final CompletableFuture<Void> atomixClusterStartFuture = atomixCluster.start();
    jobStreamClient.start().join();

    // before we can add the job stream client as a topology listener, we need to wait for the
    // topology to be set up, otherwise the callback may be lost
    brokerClient.getTopologyManager().addTopologyListener(jobStreamClient);

    gateway =
        new Gateway(
            configuration.shutdownTimeout(),
            configuration.config(),
            engineSecurityConfig,
            brokerClient,
            actorScheduler,
            jobStreamClient.streamer(),
            userServices,
            passwordEncoder,
            jwtDecoder,
            oidcClaimsProvider,
            meterRegistry,
            maxVariableNameLength,
            physicalTenantIds);
    springGatewayBridge.registerGatewayStatusSupplier(gateway::getStatus);
    springGatewayBridge.registerClusterStateSupplier(
        () ->
            Optional.ofNullable(gateway.getBrokerClient())
                .map(BrokerClient::getTopologyManager)
                .map(BrokerTopologyManager::getTopology));
    springGatewayBridge.registerJobStreamClient(() -> jobStreamClient);

    atomixClusterStartFuture.join();
    gateway.start().join(30, TimeUnit.SECONDS);
    LOGGER.info("Standalone gateway is started!");

    return gateway;
  }

  @Override
  public void close() {
    if (gateway != null) {
      try {
        gateway.close();
      } catch (final Exception e) {
        LOGGER.warn("Failed to gracefully shutdown gRPC gateway", e);
      }
    }
  }
}
