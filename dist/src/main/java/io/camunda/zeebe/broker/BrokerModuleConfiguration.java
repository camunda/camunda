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
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
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
import io.camunda.zeebe.broker.exporter.repo.ExporterLoadException;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.system.PhysicalTenantContext;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
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
  private final NodeIdProvider nodeIdProvider;
  private final PhysicalTenantIds physicalTenantIds;
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
      final NodeIdProvider nodeIdProvider,
      final PhysicalTenantIds physicalTenantIds,
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
    this.nodeIdProvider = nodeIdProvider;
    this.physicalTenantIds = physicalTenantIds;
    this.workingDirectory = workingDirectory;
  }

  private ExporterRepository exporterRepository(
      final List<ExporterDescriptor> exporterDescriptors) {
    if (exporterDescriptors != null && !exporterDescriptors.isEmpty()) {
      LOGGER.info("Create ExporterRepository with predefined exporter descriptors.");
      return new ExporterRepository(exporterDescriptors);
    } else {
      return new ExporterRepository();
    }
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

    final var physicalTenantEngineContexts =
        physicalTenantResolver.mapValues(
            camunda -> buildPhysicalTenantEngineContext(camunda, exporterDescriptors));

    final SystemContext systemContext =
        new SystemContext(
            configuration.shutdownTimeout(),
            configuration.config(),
            actorScheduler,
            cluster,
            brokerClient,
            meterRegistry,
            physicalTenantEngineContexts,
            userServicesForTenant,
            passwordEncoder,
            jwtDecoderFactory,
            oidcClaimsProviderFactory,
            searchClientsProxy,
            nodeIdProvider,
            physicalTenantIds);
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

  private PhysicalTenantContext buildPhysicalTenantEngineContext(
      final Camunda camunda, final List<ExporterDescriptor> exporterDescriptors) {
    final var s = camunda.getSecurity();
    final var securityConfig =
        new EngineSecurityConfig(
            s.getAuthentication(),
            s.getAuthorizations().isEnabled(),
            s.getMultiTenancy().isChecksEnabled(),
            s.getInitialization(),
            s.getCompiledIdValidationPattern(),
            s.getCompiledGroupIdValidationPattern());
    final var authorizationConverter = new BrokerRequestAuthorizationConverter(securityConfig);

    final BrokerCfg brokerConfig;
    if (camunda == unifiedConfiguration.getCamunda()) {
      // this is for default tenant, with no override
      // This special handling is required so that legacy properties defined via `zeebe.broker.*`
      // are still applied to the default tenant.
      brokerConfig = configuration.config();
    } else {
      brokerConfig = BrokerBasedPropertiesOverride.convert(camunda);

      // repeat the initialization done for the root broker config.
      final var cluster = brokerConfig.getCluster();
      final var currentInstance = nodeIdProvider.currentNodeInstance();
      cluster.setNodeId(currentInstance.id());
      cluster.setNodeVersion(currentInstance.version().version());
      brokerConfig.init(workingDirectory.path().toAbsolutePath().toString());
    }

    final var featureFlags = brokerConfig.getExperimental().getFeatures().toFeatureFlags();

    final var preDefinedExporters = exporterRepository(exporterDescriptors);
    final var exporterRepository = buildExporterRepository(preDefinedExporters, brokerConfig);

    return new PhysicalTenantContext(
        securityConfig, authorizationConverter, featureFlags, brokerConfig, exporterRepository);
  }

  private ExporterRepository buildExporterRepository(
      final ExporterRepository exporterRepository, final BrokerCfg cfg) {
    exporterRepository.setLicenseKey(cfg.getLicenseKey());
    final var exporterEntries = cfg.getExporters().entrySet();
    for (final var exporterEntry : exporterEntries) {
      final var id = exporterEntry.getKey();
      final var exporterCfg = exporterEntry.getValue();
      try {
        exporterRepository.load(id, exporterCfg);
      } catch (final ExporterLoadException | ExternalJarLoadException e) {
        throw new IllegalStateException(
            "Failed to load exporter with configuration: " + exporterCfg, e);
      }
    }

    return exporterRepository;
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
