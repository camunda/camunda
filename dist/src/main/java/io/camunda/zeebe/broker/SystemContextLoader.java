/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.atomix.cluster.AtomixCluster;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterLoadException;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.system.PhysicalTenantContext;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Builds a {@link SystemContext} from raw broker collaborators, translating the per-physical-tenant
 * {@link Camunda} configuration into a {@link PhysicalTenantContext} for every tenant.
 *
 * <p>This logic used to live inline in {@link BrokerModuleConfiguration}, which — being a Spring
 * {@code @Configuration} with a large constructor — was impractical to unit test. Extracting it
 * into a plain builder lets the per-tenant configuration translation (security config, broker
 * config selection, exporter loading and validation) be exercised directly. {@link
 * BrokerModuleConfiguration} now only wires its injected beans into the {@code withXxx} setters and
 * calls {@link #createSystemContext()}.
 */
public final class SystemContextLoader {

  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;

  private Duration shutdownTimeout;
  private BrokerCfg rootBrokerCfg;
  private Camunda rootCamunda;
  private ActorScheduler actorScheduler;
  private AtomixCluster cluster;
  private BrokerClient brokerClient;
  private MeterRegistry meterRegistry;
  private PhysicalTenantResolver physicalTenantResolver;
  private Function<String, UserServices> userServicesForTenant;
  private PasswordEncoder passwordEncoder;
  private Function<AuthenticationConfiguration, JwtDecoder> jwtDecoderFactory;
  private Function<AuthenticationConfiguration, OidcClaimsProvider> oidcClaimsProviderFactory;
  private SearchClientsProxy searchClientsProxy;
  private NodeIdProvider nodeIdProvider;
  private Path workingDirectory;
  private List<ExporterDescriptor> exporterDescriptors;
  private @Nullable IntFunction<Long> exportedPositionSupplier;

  public SystemContextLoader withShutdownTimeout(final Duration shutdownTimeout) {
    this.shutdownTimeout = shutdownTimeout;
    return this;
  }

  /**
   * The broker configuration for the default tenant. Also carries the broker-wide configuration
   * consumed by {@link SystemContext} directly. This is the fully-initialized root {@code
   * BrokerCfg} (node id stamped, {@code init()} already applied), which preserves legacy {@code
   * zeebe.broker.*} property support for the default tenant.
   */
  public SystemContextLoader withRootBrokerCfg(final BrokerCfg rootBrokerCfg) {
    this.rootBrokerCfg = rootBrokerCfg;
    return this;
  }

  /**
   * The root {@link Camunda} configuration. Used to detect, by identity, which resolved tenant is
   * the default tenant so that it reuses {@link #rootBrokerCfg} instead of being converted again.
   */
  public SystemContextLoader withRootCamunda(final Camunda rootCamunda) {
    this.rootCamunda = rootCamunda;
    return this;
  }

  public SystemContextLoader withActorScheduler(final ActorScheduler actorScheduler) {
    this.actorScheduler = actorScheduler;
    return this;
  }

  public SystemContextLoader withCluster(final AtomixCluster cluster) {
    this.cluster = cluster;
    return this;
  }

  public SystemContextLoader withBrokerClient(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
    return this;
  }

  public SystemContextLoader withMeterRegistry(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    return this;
  }

  /**
   * The resolver holding the per-tenant {@link Camunda} configuration, keyed by physical tenant id.
   * Also passed to {@link SystemContext} as the {@link io.camunda.cluster.PhysicalTenantIds} of
   * known tenants, since {@link PhysicalTenantResolver} implements that interface.
   */
  public SystemContextLoader withPhysicalTenantResolver(
      final PhysicalTenantResolver physicalTenantResolver) {
    this.physicalTenantResolver = physicalTenantResolver;
    return this;
  }

  public SystemContextLoader withUserServicesForTenant(
      final Function<String, UserServices> userServicesForTenant) {
    this.userServicesForTenant = userServicesForTenant;
    return this;
  }

  public SystemContextLoader withPasswordEncoder(final PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
    return this;
  }

  public SystemContextLoader withJwtDecoderFactory(
      final Function<AuthenticationConfiguration, JwtDecoder> jwtDecoderFactory) {
    this.jwtDecoderFactory = jwtDecoderFactory;
    return this;
  }

  public SystemContextLoader withOidcClaimsProviderFactory(
      final Function<AuthenticationConfiguration, OidcClaimsProvider> oidcClaimsProviderFactory) {
    this.oidcClaimsProviderFactory = oidcClaimsProviderFactory;
    return this;
  }

  public SystemContextLoader withSearchClientsProxy(final SearchClientsProxy searchClientsProxy) {
    this.searchClientsProxy = searchClientsProxy;
    return this;
  }

  public SystemContextLoader withNodeIdProvider(final NodeIdProvider nodeIdProvider) {
    this.nodeIdProvider = nodeIdProvider;
    return this;
  }

  /** The broker working directory, used to resolve relative paths in overridden tenant configs. */
  public SystemContextLoader withWorkingDirectory(final Path workingDirectory) {
    this.workingDirectory = workingDirectory;
    return this;
  }

  public SystemContextLoader withExporterDescriptors(
      final List<ExporterDescriptor> exporterDescriptors) {
    this.exporterDescriptors = exporterDescriptors;
    return this;
  }

  public SystemContextLoader withExportedPositionSupplier(
      final @Nullable IntFunction<Long> exportedPositionSupplier) {
    this.exportedPositionSupplier = exportedPositionSupplier;
    return this;
  }

  public SystemContext createSystemContext() {
    final Map<String, PhysicalTenantContext> physicalTenantContexts =
        physicalTenantResolver.mapValues(this::buildPhysicalTenantContext);

    return new SystemContext(
        shutdownTimeout,
        rootBrokerCfg,
        actorScheduler,
        cluster,
        brokerClient,
        meterRegistry,
        physicalTenantContexts,
        userServicesForTenant,
        passwordEncoder,
        jwtDecoderFactory,
        oidcClaimsProviderFactory,
        searchClientsProxy,
        exportedPositionSupplier,
        nodeIdProvider,
        physicalTenantResolver);
  }

  private PhysicalTenantContext buildPhysicalTenantContext(final Camunda camunda) {
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
    if (camunda == rootCamunda) {
      // this is for default tenant, with no override
      // This special handling is required so that legacy properties defined via `zeebe.broker.*`
      // are still applied to the default tenant.
      brokerConfig = rootBrokerCfg;
    } else {
      brokerConfig = BrokerBasedPropertiesOverride.convert(camunda);

      // repeat the initialization done for the root broker config.
      final var clusterCfg = brokerConfig.getCluster();
      final var currentInstance = nodeIdProvider.currentNodeInstance();
      clusterCfg.setNodeId(currentInstance.id());
      clusterCfg.setNodeVersion(currentInstance.version().version());
      brokerConfig.init(workingDirectory.toAbsolutePath().toString());
    }

    final var featureFlags = brokerConfig.getExperimental().getFeatures().toFeatureFlags();

    final var exporterRepository =
        buildExporterRepository(predefinedExporterRepository(), brokerConfig);

    return new PhysicalTenantContext(
        securityConfig, authorizationConverter, featureFlags, brokerConfig, exporterRepository);
  }

  private ExporterRepository predefinedExporterRepository() {
    if (exporterDescriptors != null && !exporterDescriptors.isEmpty()) {
      LOGGER.info("Create ExporterRepository with predefined exporter descriptors.");
      return new ExporterRepository(exporterDescriptors);
    } else {
      return new ExporterRepository();
    }
  }

  private ExporterRepository buildExporterRepository(
      final ExporterRepository exporterRepository, final BrokerCfg cfg) {
    validateExporters(cfg.getExporters());
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

  private static void validateExporters(final Map<String, ExporterCfg> exporters) {
    final Set<Entry<String, ExporterCfg>> entries = exporters.entrySet();
    final var badExportersNames =
        entries.stream()
            .filter(entry -> entry.getValue().getClassName() == null)
            .map(Entry::getKey)
            .toList();

    if (!badExportersNames.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected to find a 'className' configured for the exporter. Couldn't find a valid one for the following exporters "
              + badExportersNames);
    }
  }
}
