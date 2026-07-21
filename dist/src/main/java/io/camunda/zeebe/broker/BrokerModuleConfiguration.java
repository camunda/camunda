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
import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.spring.oidc.ScopedJwtDecoderFactory;
import io.camunda.security.spring.oidc.ScopedOidcClaimsProviderFactory;
import io.camunda.service.UserServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.jspecify.annotations.Nullable;
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
  private final SpringBrokerBridge springBrokerBridge;
  private final ActorScheduler actorScheduler;
  private final AtomixCluster cluster;
  private final BrokerClient brokerClient;
  private final BrokerShutdownHelper shutdownHelper;
  private final MeterRegistry meterRegistry;
  private final UnifiedConfiguration unifiedConfiguration;
  private final PhysicalTenantResolver physicalTenantResolver;
  private final ServiceRegistry serviceRegistry;
  private final PasswordEncoder passwordEncoder;
  private final ScopedJwtDecoderFactory scopedJwtDecoderFactory;
  private final ScopedOidcClaimsProviderFactory scopedOidcClaimsProviderFactory;
  private final SearchClientsProxy searchClientsProxy;
  private final @Nullable IntFunction<Long> rdbmsExportedPositionSupplier;
  private final NodeIdProvider nodeIdProvider;
  private final WorkingDirectory workingDirectory;

  private Broker broker;

  @Autowired
  public BrokerModuleConfiguration(
      final BrokerBasedConfiguration configuration,
      final SpringBrokerBridge springBrokerBridge,
      final ActorScheduler actorScheduler,
      final AtomixCluster cluster,
      final BrokerClient brokerClient,
      final BrokerShutdownHelper shutdownHelper,
      final MeterRegistry meterRegistry,
      final UnifiedConfiguration unifiedConfiguration,
      final PhysicalTenantResolver physicalTenantResolver,
      // The ServiceRegistry is not available if you want to start-up the Standalone Broker
      @Autowired(required = false) final ServiceRegistry serviceRegistry,
      final PasswordEncoder passwordEncoder,
      @Autowired(required = false) final ScopedJwtDecoderFactory scopedJwtDecoderFactory,
      @Autowired(required = false)
          final ScopedOidcClaimsProviderFactory scopedOidcClaimsProviderFactory,
      @Autowired(required = false) final SearchClientsProxy searchClientsProxy,
      @Autowired(required = false) final RdbmsMapperBundle rdbmsMapperBundle,
      final NodeIdProvider nodeIdProvider,
      final WorkingDirectory workingDirectory) {
    this.configuration = configuration;
    this.springBrokerBridge = springBrokerBridge;
    this.actorScheduler = actorScheduler;
    this.cluster = cluster;
    this.brokerClient = brokerClient;
    this.shutdownHelper = shutdownHelper;
    this.meterRegistry = meterRegistry;
    this.unifiedConfiguration = unifiedConfiguration;
    this.physicalTenantResolver = physicalTenantResolver;
    this.serviceRegistry = serviceRegistry;
    this.passwordEncoder = passwordEncoder;
    this.scopedJwtDecoderFactory = scopedJwtDecoderFactory;
    this.scopedOidcClaimsProviderFactory = scopedOidcClaimsProviderFactory;
    this.searchClientsProxy = searchClientsProxy;
    rdbmsExportedPositionSupplier = exportedPositionSupplier(rdbmsMapperBundle);
    this.nodeIdProvider = nodeIdProvider;
    this.workingDirectory = workingDirectory;
  }

  @Bean(destroyMethod = "close")
  public Broker broker(
      @Autowired(required = false) final List<ExporterDescriptor> exporterDescriptors) {
    final Function<String, UserServices> userServicesForTenant =
        physicalTenantId -> serviceRegistry.userServices(physicalTenantId);
    final Function<AuthenticationConfiguration, JwtDecoder> jwtDecoderFactory =
        authentication -> scopedJwtDecoderFactory.buildIssuerAwareDecoder(authentication);
    final Function<AuthenticationConfiguration, OidcClaimsProvider> oidcClaimsProviderFactory =
        authentication -> scopedOidcClaimsProviderFactory.buildClaimsProvider(authentication);

    final SystemContext systemContext =
        new SystemContextLoader()
            .withShutdownTimeout(configuration.shutdownTimeout())
            .withRootBrokerCfg(configuration.config())
            .withRootCamunda(unifiedConfiguration.getCamunda())
            .withActorScheduler(actorScheduler)
            .withCluster(cluster)
            .withBrokerClient(brokerClient)
            .withMeterRegistry(meterRegistry)
            .withPhysicalTenantResolver(physicalTenantResolver)
            .withUserServicesForTenant(userServicesForTenant)
            .withPasswordEncoder(passwordEncoder)
            .withJwtDecoderFactory(jwtDecoderFactory)
            .withOidcClaimsProviderFactory(oidcClaimsProviderFactory)
            .withSearchClientsProxy(searchClientsProxy)
            .withNodeIdProvider(nodeIdProvider)
            .withWorkingDirectory(workingDirectory.path())
            .withExporterDescriptors(exporterDescriptors)
            .withExportedPositionSupplier(rdbmsExportedPositionSupplier)
            .createSystemContext();
    springBrokerBridge.registerShutdownHelper(shutdownHelper::initiateShutdown);
    broker = new Broker(systemContext, springBrokerBridge, Collections.emptyList());

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

  private static @Nullable IntFunction<Long> exportedPositionSupplier(
      final @Nullable RdbmsMapperBundle rdbmsMapperBundle) {
    if (rdbmsMapperBundle == null) {
      return null;
    }
    final var exporterPositionMapper = rdbmsMapperBundle.exporterPositionMapper();
    return partition -> {
      final var position = exporterPositionMapper.findOne(partition);
      return position == null ? null : position.lastExportedPosition();
    };
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
