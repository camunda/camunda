/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system;

import io.atomix.cluster.AtomixCluster;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.DiskCfg.FreeSpaceCfg;
import io.camunda.zeebe.broker.system.configuration.ExperimentalCfg;
import io.camunda.zeebe.broker.system.configuration.SecurityCfg;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.FeatureFlags;
import io.camunda.zeebe.util.TlsConfigUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.slf4j.Logger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public final class SystemContext {

  public static final Logger LOG = Loggers.SYSTEM_LOGGER;
  public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
  public static final String LEGACY_INITIAL_CONTACT_POINTS_PROPERTY =
      "zeebe.broker.cluster.initialContactPoints";
  public static final String UNIFIED_INITIAL_CONTACT_POINTS_PROPERTY =
      "camunda.cluster.initial-contact-points";
  private static final String BROKER_ID_LOG_PROPERTY = "broker-id";
  private static final String MAX_BATCH_SIZE_ERROR_MSG =
      "Expected to have an append batch size maximum which is non negative and smaller then '%d', but was '%s'.";
  private static final String INITIAL_CONTACT_POINTS_ERROR_MSG =
      "Initial contact points must be configured when cluster size is greater than 1. "
          + "Please configure '"
          + UNIFIED_INITIAL_CONTACT_POINTS_PROPERTY
          + "' or the legacy property '"
          + LEGACY_INITIAL_CONTACT_POINTS_PROPERTY
          + "'.";

  private final Duration shutdownTimeout;
  private final BrokerCfg brokerCfg;
  private Map<String, String> diagnosticContext;
  private final ActorScheduler scheduler;
  private final AtomixCluster cluster;
  private final BrokerClient brokerClient;
  private final MeterRegistry meterRegistry;
  private final Map<String, PhysicalTenantContext> physicalTenantContexts;
  private final Function<String, UserServices> userServicesForTenant;
  private final PasswordEncoder passwordEncoder;
  private final Function<AuthenticationConfiguration, JwtDecoder> jwtDecoderFactory;
  private final Function<AuthenticationConfiguration, OidcClaimsProvider> oidcClaimsProviderFactory;
  private final SearchClientsProxy searchClientsProxy;
  private final Map<String, IntFunction<Long>> exportedPositionSuppliers;
  private final NodeIdProvider nodeIdProvider;
  private final PhysicalTenantIds physicalTenantIds;
  private final PhysicalTenantConfigurationValidator physicalTenantConfigurationValidator;

  /**
   * Creates a {@code SystemContext} where each physical tenant carries its own {@link
   * EngineSecurityConfig}, {@link BrokerRequestAuthorizationConverter}, and {@link FeatureFlags}.
   */
  public SystemContext(
      final Duration shutdownTimeout,
      final BrokerCfg brokerCfg,
      final ActorScheduler scheduler,
      final AtomixCluster cluster,
      final BrokerClient brokerClient,
      final MeterRegistry meterRegistry,
      final Map<String, PhysicalTenantContext> physicalTenantContexts,
      final Function<String, UserServices> userServicesForTenant,
      final PasswordEncoder passwordEncoder,
      final Function<AuthenticationConfiguration, JwtDecoder> jwtDecoderFactory,
      final Function<AuthenticationConfiguration, OidcClaimsProvider> oidcClaimsProviderFactory,
      final SearchClientsProxy searchClientsProxy,
      final Map<String, IntFunction<Long>> exportedPositionSuppliers,
      final NodeIdProvider nodeIdProvider,
      final PhysicalTenantIds physicalTenantIds) {
    this.shutdownTimeout = shutdownTimeout;
    this.brokerCfg = brokerCfg;
    this.scheduler = scheduler;
    this.cluster = cluster;
    this.brokerClient = brokerClient;
    this.meterRegistry = meterRegistry;

    this.physicalTenantContexts = Map.copyOf(physicalTenantContexts);
    this.userServicesForTenant = userServicesForTenant;
    this.passwordEncoder = passwordEncoder;
    this.jwtDecoderFactory = jwtDecoderFactory;
    this.oidcClaimsProviderFactory =
        Objects.requireNonNull(
            oidcClaimsProviderFactory, "oidcClaimsProviderFactory must not be null");
    this.searchClientsProxy = searchClientsProxy;
    this.exportedPositionSuppliers = Map.copyOf(exportedPositionSuppliers);
    this.nodeIdProvider = nodeIdProvider;
    this.physicalTenantIds = physicalTenantIds;
    physicalTenantConfigurationValidator = new PhysicalTenantConfigurationValidator(brokerCfg);
    initSystemContext();
  }

  private void initSystemContext() {
    validateConfiguration();

    final var cluster = brokerCfg.getCluster();
    final String brokerId = String.format("Broker-%d", cluster.getNodeId());

    diagnosticContext = Collections.singletonMap(BROKER_ID_LOG_PROPERTY, brokerId);
  }

  private void validateConfiguration() {
    final ClusterCfg cluster = brokerCfg.getCluster();

    validateDiskConfig(brokerCfg.getData());

    validClusterConfigs(cluster);
    validateMaxAppendBatchSize(brokerCfg.getExperimental());

    // Exporter configuration is validated by SystemContextLoader before the SystemContext is
    // constructed, so that per-physical-tenant exporter configs are checked as they are loaded.

    final var security = brokerCfg.getNetwork().getSecurity();
    if (security.isEnabled()) {
      validateNetworkSecurityConfig(security);
    }

    physicalTenantContexts.forEach(physicalTenantConfigurationValidator::validate);
  }

  private void validClusterConfigs(final ClusterCfg cluster) {
    final var gossiper = cluster.getConfigManager().gossip();

    final var errors = new ArrayList<String>(0);

    if (cluster.getClusterSize() > 1
        && (cluster.getInitialContactPoints() == null
            || cluster.getInitialContactPoints().isEmpty())) {
      errors.add(INITIAL_CONTACT_POINTS_ERROR_MSG);
    }

    if (!gossiper.syncDelay().isPositive()) {
      errors.add(
          String.format(
              "syncDelay must be positive: configured value = %d ms",
              gossiper.syncDelay().toMillis()));
    }
    if (!gossiper.syncRequestTimeout().isPositive()) {
      errors.add(
          String.format(
              "syncRequestTimeout must be positive: configured value = %d ms",
              gossiper.syncRequestTimeout().toMillis()));
    }
    if (gossiper.gossipFanout() < 2) {
      errors.add(
          String.format(
              "gossipFanout must be greater than 1: configured value = %d",
              gossiper.gossipFanout()));
    }

    if (!errors.isEmpty()) {
      throw new InvalidConfigurationException(
          "Invalid ConfigManager configuration: " + String.join(", ", errors), null);
    }
  }

  private void validateMaxAppendBatchSize(final ExperimentalCfg experimental) {
    final var maxAppendBatchSize = experimental.getMaxAppendBatchSize();
    if (maxAppendBatchSize.isNegative() || maxAppendBatchSize.toBytes() >= Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          String.format(MAX_BATCH_SIZE_ERROR_MSG, Integer.MAX_VALUE, maxAppendBatchSize));
    }
  }

  private void validateDiskConfig(final DataCfg dataCfg) {
    if (dataCfg.getDisk().isEnableMonitoring()) {
      try {
        final FreeSpaceCfg freeSpaceCfg = dataCfg.getDisk().getFreeSpace();
        final var processingFreeSpace = freeSpaceCfg.getProcessing().toBytes();
        final var replicationFreeSpace = freeSpaceCfg.getReplication().toBytes();
        if (processingFreeSpace <= replicationFreeSpace) {
          throw new IllegalArgumentException(
              "Minimum free space for processing (%d) must be greater than minimum free space for replication (%d). Configured values are %s"
                  .formatted(processingFreeSpace, replicationFreeSpace, freeSpaceCfg));
        }
      } catch (final Exception e) {
        throw new InvalidConfigurationException("Failed to parse disk monitoring configuration", e);
      }
    }
  }

  private void validateNetworkSecurityConfig(final SecurityCfg security) {
    TlsConfigUtil.validateTlsConfig(
        security.getCertificateChainPath(),
        security.getPrivateKeyPath(),
        security.getKeyStore().getFilePath());
  }

  public ActorScheduler getScheduler() {
    return scheduler;
  }

  public BrokerCfg getBrokerConfiguration() {
    return brokerCfg;
  }

  public AtomixCluster getCluster() {
    return cluster;
  }

  public Map<String, String> getDiagnosticContext() {
    return diagnosticContext;
  }

  public BrokerClient getBrokerClient() {
    return brokerClient;
  }

  public Duration getShutdownTimeout() {
    return shutdownTimeout;
  }

  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  /** Returns the per-physical-tenant engine context map (unmodifiable). */
  public Map<String, PhysicalTenantContext> getPhysicalTenantContexts() {
    return physicalTenantContexts;
  }

  /**
   * Returns the engine context for the given physical tenant.
   *
   * @throws IllegalArgumentException if the physical tenant id is unknown
   */
  public PhysicalTenantContext getPhysicalTenantContext(final String physicalTenantId) {
    if (!physicalTenantContexts.containsKey(physicalTenantId)) {
      throw new IllegalArgumentException("Unknown physical tenant id '" + physicalTenantId + "'");
    }
    return physicalTenantContexts.get(physicalTenantId);
  }

  /**
   * Returns the security configuration for the {@code default} physical tenant. Requires a {@value
   * io.camunda.cluster.PhysicalTenantIds#DEFAULT_PHYSICAL_TENANT_ID} entry to be registered.
   *
   * @throws IllegalStateException if no security configuration is registered for the default
   *     physical tenant
   */
  public EngineSecurityConfig getSecurityConfiguration() {
    final var ctx = physicalTenantContexts.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    if (ctx == null) {
      throw new IllegalStateException(
          "No security configuration registered for the default physical tenant");
    }
    return ctx.securityConfig();
  }

  public Function<String, UserServices> getUserServicesForTenant() {
    return userServicesForTenant;
  }

  public PasswordEncoder getPasswordEncoder() {
    return passwordEncoder;
  }

  public Function<AuthenticationConfiguration, OidcClaimsProvider> getOidcClaimsProviderFactory() {
    return oidcClaimsProviderFactory;
  }

  public Function<AuthenticationConfiguration, JwtDecoder> getJwtDecoderFactory() {
    return jwtDecoderFactory;
  }

  public SearchClientsProxy getSearchClientsProxy() {
    return searchClientsProxy;
  }

  /** Returns the per-physical-tenant RDBMS exported-position supplier map (unmodifiable). */
  public Map<String, IntFunction<Long>> getExportedPositionSuppliers() {
    return exportedPositionSuppliers;
  }

  public NodeIdProvider getNodeIdProvider() {
    return nodeIdProvider;
  }

  public PhysicalTenantIds getPhysicalTenantIds() {
    return physicalTenantIds;
  }

  public PhysicalTenantConfigurationValidator getPhysicalTenantConfigurationValidator() {
    return physicalTenantConfigurationValidator;
  }
}
