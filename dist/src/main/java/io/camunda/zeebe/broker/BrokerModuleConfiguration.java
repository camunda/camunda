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
import io.camunda.application.commons.pt.SchemaInitializer;
import io.camunda.application.commons.secrets.InternalConfigRefreshers;
import io.camunda.application.commons.secrets.PhysicalTenantSecretStores;
import io.camunda.application.commons.secrets.SecretStoreRegistries;
import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.physicaltenants.PhysicalTenantConfigValidator;
import io.camunda.configuration.physicaltenants.PhysicalTenantCredentialValidator;
import io.camunda.configuration.physicaltenants.PhysicalTenantKeyClassification;
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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
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
public class BrokerModuleConfiguration implements CloseableSilently {
  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;
  private static final Duration PHYSICAL_TENANT_CHANGE_TIMEOUT = Duration.ofMinutes(2);

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
  private final Map<String, IntFunction<Long>> rdbmsExportedPositionSuppliers;
  private final NodeIdProvider nodeIdProvider;
  private final WorkingDirectory workingDirectory;
  private final SecretStoreRegistries secretStoreRegistries;
  private final ResolvedDataDirectory resolvedDataDirectory;
  private final ObjectProvider<SchemaInitializer> schemaInitializer;
  private final DefaultPhysicalTenantRegistry physicalTenantRegistry;

  private Broker broker;
  private SystemContextLoader systemContextLoader;

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
      @Autowired(required = false) final Map<String, RdbmsMapperBundle> rdbmsMapperBundles,
      final NodeIdProvider nodeIdProvider,
      final WorkingDirectory workingDirectory,
      final SecretStoreRegistries secretStoreRegistries,
      final ResolvedDataDirectory resolvedDataDirectory,
      final ObjectProvider<SchemaInitializer> schemaInitializer,
      final DefaultPhysicalTenantRegistry physicalTenantRegistry) {
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
    rdbmsExportedPositionSuppliers = exportedPositionSuppliers(rdbmsMapperBundles);
    this.nodeIdProvider = nodeIdProvider;
    this.workingDirectory = workingDirectory;
    this.secretStoreRegistries = secretStoreRegistries;
    this.resolvedDataDirectory = resolvedDataDirectory;
    this.schemaInitializer = schemaInitializer;
    this.physicalTenantRegistry = physicalTenantRegistry;
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

    systemContextLoader =
        new SystemContextLoader()
            .withShutdownTimeout(configuration.shutdownTimeout())
            .withRootBrokerCfg(configuration.config())
            .withRootCamunda(unifiedConfiguration.getCamunda())
            .withActorScheduler(actorScheduler)
            .withCluster(cluster)
            .withBrokerClient(brokerClient)
            .withMeterRegistry(meterRegistry)
            .withPhysicalTenantResolver(physicalTenantResolver)
            .withPhysicalTenantIds(physicalTenantRegistry)
            .withUserServicesForTenant(userServicesForTenant)
            .withPasswordEncoder(passwordEncoder)
            .withJwtDecoderFactory(jwtDecoderFactory)
            .withOidcClaimsProviderFactory(oidcClaimsProviderFactory)
            .withSearchClientsProxy(searchClientsProxy)
            .withNodeIdProvider(nodeIdProvider)
            .withWorkingDirectory(workingDirectory.path())
            .withExporterDescriptors(exporterDescriptors)
            .withExportedPositionSupplier(rdbmsExportedPositionSuppliers)
            .withSecretStoreRegistries(secretStoreRegistries)
            .withResolvedDataDirectory(resolvedDataDirectory);
    final SystemContext systemContext = systemContextLoader.createSystemContext();
    springBrokerBridge.registerShutdownHelper(shutdownHelper::initiateShutdown);
    springBrokerBridge.registerSchemaInitializerLookup(schemaInitializerLookupSupplier());
    broker = new Broker(systemContext, springBrokerBridge, Collections.emptyList());

    // already initiate starting the broker
    // to ensure that the necessary ports
    // get opened and other apps like
    // Operate can connect
    startBroker();

    return broker;
  }

  @Bean
  public PhysicalTenantLifecycleService physicalTenantLifecycleService(
      final Broker broker,
      final ConfigurableEnvironment environment,
      final ObjectProvider<PhysicalTenantResources> resources,
      final PhysicalTenantSecretStores secretStores) {
    return new PhysicalTenantLifecycleService(
        broker,
        systemContextLoader,
        environment,
        unifiedConfiguration.getCamunda(),
        physicalTenantResolver,
        physicalTenantRegistry,
        PHYSICAL_TENANT_CHANGE_TIMEOUT,
        resources.orderedStream().toList(),
        secretStores);
  }

  /**
   * Starts the secret refresher of a tenant added at runtime, re-points it to the store and
   * interval of a replaced tenant's configuration, and stops it with a removed tenant.
   */
  @Bean
  @Order(0)
  public PhysicalTenantResources physicalTenantSecretRefresh(
      final InternalConfigRefreshers refreshers, final PhysicalTenantSecretStores secretStores) {
    return new PhysicalTenantResources() {
      @Override
      public void apply(final String physicalTenantId, final Camunda configuration) {
        final var secrets = configuration.getSecrets();
        refreshers.start(
            physicalTenantId,
            secretStores.internalConfig(physicalTenantId, secrets),
            secrets.getRefreshInterval());
      }

      @Override
      public void release(final String physicalTenantId) {
        refreshers.stop(physicalTenantId);
      }
    };
  }

  @Bean
  public PhysicalTenantKeyClassification physicalTenantKeyClassification() {
    return PhysicalTenantKeyClassification.load();
  }

  @Bean
  public PhysicalTenantCredentialValidator physicalTenantCredentialValidator(
      final PhysicalTenantLifecycleService lifecycle) {
    return new PhysicalTenantCredentialValidator(
        (physicalTenantId, subtree) ->
            lifecycle.effectiveConfiguration(
                physicalTenantId, PhysicalTenantConfigCodec.asProperties(subtree)));
  }

  @Bean(destroyMethod = "close")
  public PhysicalTenantConfigExecutor physicalTenantConfigExecutor(
      final PhysicalTenantLifecycleService lifecycle) {
    return new PhysicalTenantConfigExecutor(lifecycle, physicalTenantRegistry, meterRegistry);
  }

  @Bean(destroyMethod = "close")
  public PhysicalTenantConfigService physicalTenantConfigService(
      final Broker broker,
      final PhysicalTenantLifecycleService lifecycle,
      final PhysicalTenantConfigExecutor executor,
      final ClusterConfigurationManagementRequestSender clusterManagementRequestSender,
      final ObjectProvider<PhysicalTenantConfigValidator> validators,
      final ConfigurableEnvironment environment) {
    return new PhysicalTenantConfigService(
        broker,
        lifecycle,
        executor,
        clusterManagementRequestSender,
        validators.orderedStream().toList(),
        environment,
        physicalTenantResolver.known(),
        cluster.getMembershipService().getLocalMember().id());
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

  private static Map<String, IntFunction<Long>> exportedPositionSuppliers(
      final @Nullable Map<String, RdbmsMapperBundle> rdbmsMapperBundles) {
    if (rdbmsMapperBundles == null) {
      return Map.of();
    }
    final Map<String, IntFunction<Long>> suppliers = new HashMap<>();
    rdbmsMapperBundles.forEach(
        (physicalTenantId, rdbmsMapperBundle) ->
            suppliers.put(physicalTenantId, exportedPositionSupplier(rdbmsMapperBundle)));
    return Map.copyOf(suppliers);
  }

  private static IntFunction<Long> exportedPositionSupplier(
      final RdbmsMapperBundle rdbmsMapperBundle) {
    final var exporterPositionMapper = rdbmsMapperBundle.exporterPositionMapper();
    return partition -> {
      final var position = exporterPositionMapper.findOne(partition);
      return position == null ? null : position.lastExportedPosition();
    };
  }

  private @NonNull Function<String, Runnable> schemaInitializerLookupSupplier() {
    return tenantId -> {
      final var initializer = schemaInitializer.getIfAvailable();
      return initializer == null ? null : () -> initializer.initializeNow(tenantId);
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
