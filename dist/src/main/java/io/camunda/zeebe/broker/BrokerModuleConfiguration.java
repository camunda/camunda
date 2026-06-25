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
import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.spring.oidc.ScopedJwtDecoderFactory;
import io.camunda.security.spring.oidc.ScopedOidcClaimsProviderFactory;
import io.camunda.service.UserServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Entry point for the broker modules by using the the {@link io.camunda.application.Profile#BROKER}
 * profile, so that the appropriate broker application properties are applied.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = {
      "io.camunda.zeebe.broker",
      "io.camunda.zeebe.shared",
    })
@Profile("broker")
@DependsOn(
    "dataDirectoryProvider") // ensure that the data directory is set up before starting the broker
public class BrokerModuleConfiguration implements CloseableSilently {
  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;

  private final BrokerBasedConfiguration configuration;
  private final IdentityConfiguration identityConfiguration;
  private final SpringBrokerBridge springBrokerBridge;
  private final ActorScheduler actorScheduler;
  private final AtomixCluster cluster;
  private final BrokerClient brokerClient;
  private final BrokerShutdownHelper shutdownHelper;
  private final MeterRegistry meterRegistry;
  private final Map<String, EngineSecurityConfig> engineSecurityConfigsByPhysicalTenant;
  private final ServiceRegistry serviceRegistry;
  private final PasswordEncoder passwordEncoder;
  private final ScopedJwtDecoderFactory scopedJwtDecoderFactory;
  private final ScopedOidcClaimsProviderFactory scopedOidcClaimsProviderFactory;
  private final SearchClientsProxy searchClientsProxy;
  private final NodeIdProvider nodeIdProvider;
  private final PhysicalTenantIds physicalTenantIds;

  private Broker broker;

  @Autowired
  public BrokerModuleConfiguration(
      final BrokerBasedConfiguration configuration,
      final IdentityConfiguration identityConfiguration,
      final SpringBrokerBridge springBrokerBridge,
      final ActorScheduler actorScheduler,
      final AtomixCluster cluster,
      final BrokerClient brokerClient,
      final BrokerShutdownHelper shutdownHelper,
      final MeterRegistry meterRegistry,
      final PhysicalTenantResolver physicalTenantResolver,
      // The ServiceRegistry is not available if you want to start-up the Standalone Broker
      @Autowired(required = false) final ServiceRegistry serviceRegistry,
      final PasswordEncoder passwordEncoder,
      @Autowired(required = false) final ScopedJwtDecoderFactory scopedJwtDecoderFactory,
      @Autowired(required = false)
          final ScopedOidcClaimsProviderFactory scopedOidcClaimsProviderFactory,
      @Autowired(required = false) final SearchClientsProxy searchClientsProxy,
      final NodeIdProvider nodeIdProvider,
      final PhysicalTenantIds physicalTenantIds) {
    this.configuration = configuration;
    this.identityConfiguration = identityConfiguration;
    this.springBrokerBridge = springBrokerBridge;
    this.actorScheduler = actorScheduler;
    this.cluster = cluster;
    this.brokerClient = brokerClient;
    this.shutdownHelper = shutdownHelper;
    this.meterRegistry = meterRegistry;
    this.engineSecurityConfigsByPhysicalTenant =
        physicalTenantResolver.mapValues(
            camunda -> {
              final var s = camunda.getSecurity();
              return new EngineSecurityConfig(
                  s.getAuthentication(),
                  s.getAuthorizations().isEnabled(),
                  s.getMultiTenancy().isChecksEnabled(),
                  s.getInitialization(),
                  s.getCompiledIdValidationPattern(),
                  s.getCompiledGroupIdValidationPattern());
            });
    this.serviceRegistry = serviceRegistry;
    this.passwordEncoder = passwordEncoder;
    this.scopedJwtDecoderFactory = scopedJwtDecoderFactory;
    this.scopedOidcClaimsProviderFactory = scopedOidcClaimsProviderFactory;
    this.searchClientsProxy = searchClientsProxy;
    this.nodeIdProvider = nodeIdProvider;
    this.physicalTenantIds = physicalTenantIds;
  }

  @Bean
  public ExporterRepository exporterRepository(
      @Autowired(required = false) final List<ExporterDescriptor> exporterDescriptors) {
    if (exporterDescriptors != null && !exporterDescriptors.isEmpty()) {
      LOGGER.info("Create ExporterRepository with predefined exporter descriptors.");
      return new ExporterRepository(exporterDescriptors);
    } else {
      return new ExporterRepository();
    }
  }

  @Bean(destroyMethod = "close")
  public Broker broker(final ExporterRepository exporterRepository) {
    final Map<String, BrokerRequestAuthorizationConverter>
        brokerRequestAuthorizationConvertersByPhysicalTenant =
            engineSecurityConfigsByPhysicalTenant.entrySet().stream()
                .collect(
                    Collectors.toUnmodifiableMap(
                        Entry::getKey,
                        entry -> new BrokerRequestAuthorizationConverter(entry.getValue())));
    final Function<String, UserServices> userServicesForTenant =
        tenantId -> serviceRegistry.userServices(tenantId);
    final Function<AuthenticationConfiguration, JwtDecoder> jwtDecoderFactory =
        authConfig -> scopedJwtDecoderFactory.buildIssuerAwareDecoder(authConfig);
    final Function<AuthenticationConfiguration, OidcClaimsProvider> oidcClaimsProviderFactory =
        authConfig -> scopedOidcClaimsProviderFactory.buildClaimsProvider(authConfig);

    final SystemContext systemContext =
        new SystemContext(
            configuration.shutdownTimeout(),
            configuration.config(),
            identityConfiguration,
            actorScheduler,
            cluster,
            brokerClient,
            meterRegistry,
            engineSecurityConfigsByPhysicalTenant,
            userServicesForTenant,
            passwordEncoder,
            jwtDecoderFactory,
            oidcClaimsProviderFactory,
            searchClientsProxy,
            brokerRequestAuthorizationConvertersByPhysicalTenant,
            nodeIdProvider,
            physicalTenantIds);
    springBrokerBridge.registerShutdownHelper(
        (errorCode, reason) -> shutdownHelper.initiateShutdown(errorCode, reason));
    broker =
        new Broker(systemContext, springBrokerBridge, Collections.emptyList(), exporterRepository);

    // already initiate starting the broker
    // to ensure that the necessary ports
    // get opened and other apps like
    // Operate can connect
    startBroker();

    return broker;
  }

  protected void startBroker() {
    broker.start();
  }

  protected void stopBroker() {
    try {
      if (broker != null) {
        broker.close();
      }
    } finally {
      cleanupWorkingDirectory();
    }
  }

  private void cleanupWorkingDirectory() {
    final var workingDirectory = configuration.workingDirectory();
    if (!workingDirectory.isTemporary()) {
      return;
    }

    LOGGER.debug("Deleting broker temporary working directory {}", workingDirectory.path());
    try {
      FileUtil.deleteFolderIfExists(workingDirectory.path());
    } catch (final IOException e) {
      LOGGER.warn("Failed to delete temporary directory {}", workingDirectory.path());
    }
  }

  @Override
  public void close() {
    stopBroker();
  }
}
