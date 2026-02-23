/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.application.Profile;
import io.camunda.application.StandaloneCamunda;
import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.authentication.config.AuthenticationProperties;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.NodeIdProvider.Type;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.GatewayRestPropertiesOverride;
import io.camunda.configuration.beanoverrides.PrimaryStorageBackupPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineIndexPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineRetentionPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.configuration.beans.SearchEngineIndexProperties;
import io.camunda.configuration.beans.SearchEngineRetentionProperties;
import io.camunda.security.configuration.ConfiguredMappingRule;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.broker.BrokerModuleConfiguration;
import io.camunda.zeebe.broker.NodeIdProviderConfiguration;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.qa.util.actuator.BrokerHealthActuator;
import io.camunda.zeebe.qa.util.actuator.GatewayHealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.util.unit.DataSize;

/** Represents an instance of the {@link BrokerModuleConfiguration} Spring application. */
@SuppressWarnings("UnusedReturnValue")
public final class TestStandaloneBroker extends TestSpringApplication<TestStandaloneBroker>
    implements TestGateway<TestStandaloneBroker>, TestStandaloneApplication<TestStandaloneBroker> {
  public static final String DEFAULT_MAPPING_RULE_ID = "default";
  public static final String DEFAULT_MAPPING_RULE_CLAIM_NAME = "client_id";
  public static final String DEFAULT_MAPPING_RULE_CLAIM_VALUE = "default";
  public static final String RECORDING_EXPORTER_ID = "recordingExporter";
  private final Camunda unifiedConfig;
  private final CamundaSecurityProperties securityConfig;
  private boolean isGatewayEnabled = true;
  private final Map<String, Consumer<Map<String, Object>>> exporterMutators = new HashMap<>();

  public TestStandaloneBroker() {
    super(
        BrokerModuleConfiguration.class,
        CommonsModuleConfiguration.class,
        UnifiedConfigurationHelper.class,
        UnifiedConfiguration.class,
        PrimaryStorageBackupPropertiesOverride.class,
        NodeIdProviderConfiguration.class,
        BrokerBasedPropertiesOverride.class,
        GatewayBasedPropertiesOverride.class,
        GatewayRestPropertiesOverride.class,
        SearchEngineConnectPropertiesOverride.class,
        SearchEngineIndexPropertiesOverride.class,
        SearchEngineRetentionPropertiesOverride.class);

    unifiedConfig = new Camunda();

    // Initialize unified config with test-friendly defaults
    initializeUnifiedConfigDefaults();

    StandaloneCamunda.getDefaultProperties(false)
        .forEach(
            (key, value) -> {
              withProperty(key, value);
            });

    // this is required to prevent default spring boot 4.0 security setup to kick in
    withProperty(
        "spring.autoconfigure.exclude",
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,"
            + "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration");

    //noinspection resource
    withBean("camunda", unifiedConfig, Camunda.class).withAdditionalProfile(Profile.BROKER);

    securityConfig = new CamundaSecurityProperties();
    securityConfig.getAuthorizations().setEnabled(false);
    securityConfig.getAuthentication().setUnprotectedApi(true);
    securityConfig
        .getInitialization()
        .getUsers()
        .add(
            new ConfiguredUser(
                InitializationConfiguration.DEFAULT_USER_USERNAME,
                InitializationConfiguration.DEFAULT_USER_PASSWORD,
                InitializationConfiguration.DEFAULT_USER_NAME,
                InitializationConfiguration.DEFAULT_USER_EMAIL));
    securityConfig
        .getInitialization()
        .getMappingRules()
        .add(
            new ConfiguredMappingRule(
                DEFAULT_MAPPING_RULE_ID,
                DEFAULT_MAPPING_RULE_CLAIM_NAME,
                DEFAULT_MAPPING_RULE_CLAIM_VALUE));
    securityConfig
        .getInitialization()
        .getDefaultRoles()
        .put(
            "admin",
            Map.of(
                "users",
                List.of(InitializationConfiguration.DEFAULT_USER_USERNAME),
                "mappingRules",
                List.of(DEFAULT_MAPPING_RULE_ID)));

    withBean("securityConfig", securityConfig, CamundaSecurityProperties.class);
    withProperty(
        AuthenticationProperties.API_UNPROTECTED,
        securityConfig.getAuthentication().getUnprotectedApi());
    withProperty(
        "camunda.security.authorizations.enabled", securityConfig.getAuthorizations().isEnabled());
    // by default, we don't want to create the schema as ES/OS containers may not be used in the
    // current test
    withCreateSchema(false);
  }

  @Override
  public int mappedPort(final TestZeebePort port) {
    return switch (port) {
      case COMMAND -> unifiedConfig.getCluster().getNetwork().getCommandApi().getPort();
      case GATEWAY -> unifiedConfig.getApi().getGrpc().getPort();
      case CLUSTER -> unifiedConfig.getCluster().getNetwork().getInternalApi().getPort();
      default -> super.mappedPort(port);
    };
  }

  @Override
  public TestStandaloneBroker withProperty(final String key, final Object value) {
    // Since the security config is not constructed from the properties, we need to manually update
    // it when we override a property.
    AuthenticationProperties.applyToSecurityConfig(securityConfig, key, value);
    return super.withProperty(key, value);
  }

  @Override
  public TestStandaloneBroker withAuthenticationMethod(
      final AuthenticationMethod authenticationMethod) {
    // as mode is OIDC, and we have a user created by default in `TestStandaloneBroker`
    // we need to reset the list of users to empty list as having pre-configured user in
    // OIDC is not allowed
    if (authenticationMethod == AuthenticationMethod.OIDC) {
      securityConfig.getInitialization().setUsers(new ArrayList<>());
    }
    return super.withAuthenticationMethod(authenticationMethod);
  }

  @Override
  protected SpringApplicationBuilder createSpringBuilder() {
    // because @ConditionalOnRestGatewayEnabled relies on the zeebe.broker.gateway.enable property,
    // we need to hook in at the last minute and set the property
    // Gateway enable flag is set via property since gateway config isn't fully in unified config
    withProperty(
        "zeebe.broker.gateway.enable",
        property("zeebe.broker.gateway.enable", Boolean.class, isGatewayEnabled));
    return super.createSpringBuilder();
  }

  public TestStandaloneBroker withAuthorizationsEnabled() {
    // when using authorizations, api authentication needs to be enforced too
    withAuthenticatedAccess();
    withProperty("camunda.security.authorizations.enabled", true);
    return withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true));
  }

  public TestStandaloneBroker withAuthorizationsDisabled() {
    withUnauthenticatedAccess();
    return withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(false));
  }

  @Override
  public TestStandaloneBroker self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    if (unifiedConfig.getCluster().getNodeIdProvider().getType() == Type.S3) {
      // Get nodeId from BrokerBasedProperties instead of unified configuration when using S3 node
      // id provider. If the broker is not started yet, return "null" as node id
      if (isStarted()) {
        return MemberId.from(
            String.valueOf(bean(BrokerBasedProperties.class).getCluster().getNodeId()));
      } else {
        return MemberId.from("null");
      }
    }
    return MemberId.from(String.valueOf(unifiedConfig.getCluster().getNodeId()));
  }

  @Override
  public String host() {
    final var host = unifiedConfig.getCluster().getNetwork().getHost();
    return host != null ? host : "0.0.0.0";
  }

  @Override
  public HealthActuator healthActuator() {
    return brokerHealth();
  }

  @Override
  public boolean isGateway() {
    // Gateway enable flag is set via property (not fully in unified config yet)
    return isGatewayEnabled;
  }

  /**
   * Modifies the unified configuration (camunda.* properties). This is the recommended way to
   * configure test brokers going forward.
   *
   * <p>The unified configuration will be merged into BrokerBasedProperties at Spring application
   * startup via BrokerBasedPropertiesOverride, with unified config taking precedence.
   *
   * @param modifier a configuration function that accepts the Camunda configuration object
   * @return itself for chaining
   */
  @Override
  public TestStandaloneBroker withUnifiedConfig(final Consumer<Camunda> modifier) {
    modifier.accept(unifiedConfig);
    return this;
  }

  public TestStandaloneBroker withGatewayEnabled(final boolean enabled) {
    // Gateway enable flag is set via property (not fully in unified config yet)
    isGatewayEnabled = enabled;
    return this;
  }

  @Override
  public URI grpcAddress() {
    if (!isGateway()) {
      throw new IllegalStateException(
          "Expected to get the gateway address for this broker, but the embedded gateway is not enabled");
    }

    final var scheme = unifiedConfig.getApi().getGrpc().getSsl().isEnabled() ? "https" : "http";
    return uri(scheme, TestZeebePort.GATEWAY);
  }

  @Override
  public GatewayHealthActuator gatewayHealth() {
    throw new UnsupportedOperationException("Brokers do not support the gateway health indicators");
  }

  /**
   * Returns the unified configuration object. This provides access to the camunda.* configuration
   * structure and is the primary way to read/configure the test broker.
   *
   * @return the Camunda unified configuration object
   */
  @Override
  public Camunda unifiedConfig() {
    return unifiedConfig;
  }

  /** Enables multi-tenancy in the security configuration. */
  public TestStandaloneBroker withMultiTenancyEnabled() {
    withProperty("camunda.security.multiTenancy.checksEnabled", "true");
    return withSecurityConfig(cfg -> cfg.getMultiTenancy().setChecksEnabled(true));
  }

  /**
   * Returns the health actuator for this broker. You can use this to check for liveness, readiness,
   * and startup.
   */
  public BrokerHealthActuator brokerHealth() {
    return BrokerHealthActuator.of(this);
  }

  /**
   * Enables/disables usage of the recording exporter using {@link #RECORDING_EXPORTER_ID} as its
   * unique ID.
   *
   * @param useRecordingExporter if true, will enable the exporter; if false, will remove it from
   *     the config
   * @return itself for chaining
   */
  public TestStandaloneBroker withRecordingExporter(final boolean useRecordingExporter) {
    if (!useRecordingExporter) {
      unifiedConfig.getData().getExporters().remove(RECORDING_EXPORTER_ID);
      return this;
    }

    return withDataConfig(
        data -> {
          final var exporter =
              data.getExporters()
                  .computeIfAbsent(
                      RECORDING_EXPORTER_ID, ignored -> new io.camunda.configuration.Exporter());
          exporter.setClassName(RecordingExporter.class.getName());
        });
  }

  /**
   * Adds or replaces a new exporter with the given ID using unified configuration.
   *
   * <p>Note: This method accepts ExporterCfg for backward compatibility but configures the unified
   * config exporter. ExporterCfg will be converted to the unified Exporter format.
   *
   * @param id the ID of the exporter
   * @param modifier a configuration function that accepts ExporterCfg
   * @return itself for chaining
   */
  @Override
  public TestStandaloneBroker withExporter(final String id, final Consumer<ExporterCfg> modifier) {
    // Create a temporary ExporterCfg to accept the configuration
    final var tempExporterCfg = new ExporterCfg();
    modifier.accept(tempExporterCfg);

    // Transfer to unified config exporter
    return withDataConfig(
        data -> {
          final var unifiedExporter =
              data.getExporters()
                  .computeIfAbsent(id, ignored -> new io.camunda.configuration.Exporter());
          unifiedExporter.setClassName(tempExporterCfg.getClassName());
          unifiedExporter.setJarPath(tempExporterCfg.getJarPath());
          if (exporterMutators.containsKey(id)) {
            final Map<String, Object> args = new HashMap<>(tempExporterCfg.getArgs());
            exporterMutators.get(id).accept(args);
            unifiedExporter.setArgs(args);
          } else {
            unifiedExporter.setArgs(tempExporterCfg.getArgs());
          }
        });
  }

  /**
   * Convenience method for setting the secondary storage type in the unified configuration.
   * Additionally, the environment variable camunda.data.secondary-storage.type is set to ensure
   * that ConditionalOnSecondaryStorageType behaves as expected
   *
   * @param type the secondary storage type
   * @return itself for chaining
   */
  @Override
  public TestStandaloneBroker withSecondaryStorageType(final SecondaryStorageType type) {
    unifiedConfig.getData().getSecondaryStorage().setType(type);
    withProperty("camunda.data.secondary-storage.type", type.name());
    return this;
  }

  /**
   * Convenience method to modify cluster configuration using the unified configuration API.
   *
   * @param modifier a configuration function for cluster settings (membership, raft, network, etc.)
   * @return itself for chaining
   */
  @Override
  public TestStandaloneBroker withClusterConfig(
      final Consumer<io.camunda.configuration.Cluster> modifier) {
    modifier.accept(unifiedConfig.getCluster());
    return this;
  }

  /**
   * Convenience method to modify data configuration using the unified configuration API.
   *
   * @param modifier a configuration function for data settings (storage, backup, exporters, etc.)
   * @return itself for chaining
   */
  @Override
  public TestStandaloneBroker withDataConfig(
      final Consumer<io.camunda.configuration.Data> modifier) {
    modifier.accept(unifiedConfig.getData());
    return this;
  }

  /**
   * Convenience method to modify API configuration using the unified configuration API.
   *
   * @param modifier a configuration function for API settings (gRPC, REST, long-polling, etc.)
   * @return itself for chaining
   */
  @Override
  public TestStandaloneBroker withApiConfig(final Consumer<io.camunda.configuration.Api> modifier) {
    modifier.accept(unifiedConfig.getApi());
    return this;
  }

  /**
   * Convenience method to modify processing configuration using the unified configuration API.
   *
   * @param modifier a configuration function for processing settings (batches, async tasks, etc.)
   * @return itself for chaining
   */
  @Override
  public TestStandaloneBroker withProcessingConfig(
      final Consumer<io.camunda.configuration.Processing> modifier) {
    modifier.accept(unifiedConfig.getProcessing());
    return this;
  }

  /**
   * Convenience method to modify monitoring configuration using the unified configuration API.
   *
   * @param modifier a configuration function for monitoring settings (metrics, tracing, etc.)
   * @return itself for chaining
   */
  @Override
  public TestStandaloneBroker withMonitoringConfig(
      final Consumer<io.camunda.configuration.Monitoring> modifier) {
    modifier.accept(unifiedConfig.getMonitoring());
    return this;
  }

  /**
   * Modifies the security configuration. Will still mutate the configuration if the broker is
   * started, but likely has no effect until it's restarted.
   */
  @Override
  public TestStandaloneBroker withSecurityConfig(
      final Consumer<CamundaSecurityProperties> modifier) {
    modifier.accept(securityConfig);
    return this;
  }

  @Override
  public Optional<AuthenticationMethod> clientAuthenticationMethod() {
    return apiAuthenticationMethod();
  }

  /**
   * Registers a mutator function that will be applied to the exporter arguments for the exporter
   * with the given ID after the exporter is set to the application
   *
   * @param id the ID of the exporter
   * @param modifier a configuration function that accepts the exporter arguments map
   * @return itself for chaining
   */
  public TestStandaloneBroker withExporterMutator(
      final String id, final Consumer<Map<String, Object>> modifier) {
    exporterMutators.put(id, modifier);
    return this;
  }

  /**
   * Initialize unified configuration with test-friendly defaults that match the legacy
   * configuration behavior.
   */
  private void initializeUnifiedConfigDefaults() {
    // Set cluster defaults
    unifiedConfig.getCluster().setSize(1);
    unifiedConfig.getCluster().setPartitionCount(1);
    unifiedConfig.getCluster().setReplicationFactor(1);
    unifiedConfig
        .getCluster()
        .setCompressionAlgorithm(io.camunda.configuration.Cluster.CompressionAlgorithm.NONE);

    // Set membership defaults for fast test execution
    final var membership = unifiedConfig.getCluster().getMembership();
    membership.setFailureTimeout(Duration.ofSeconds(5));
    membership.setProbeInterval(Duration.ofMillis(100));
    membership.setSyncInterval(Duration.ofMillis(500));

    final var metadata = unifiedConfig.getCluster().getMetadata();
    metadata.setSyncInitializerDelay(Duration.ofMillis(500));
    metadata.setSyncDelay(Duration.ofMillis(500));

    // Set raft defaults - disable flushing for faster tests
    unifiedConfig.getCluster().getRaft().setFlushEnabled(false);
    unifiedConfig.getCluster().getRaft().setFlushDelay(Duration.ZERO);

    // Set data defaults - smaller segments for tests
    unifiedConfig.getData().setSnapshotPeriod(Duration.ofMinutes(5));
    unifiedConfig
        .getData()
        .getPrimaryStorage()
        .getLogStream()
        .setLogSegmentSize(DataSize.ofMegabytes(16));
    unifiedConfig
        .getData()
        .getPrimaryStorage()
        .getDisk()
        .getFreeSpace()
        .setProcessing(DataSize.ofMegabytes(128));
    unifiedConfig
        .getData()
        .getPrimaryStorage()
        .getDisk()
        .getFreeSpace()
        .setReplication(DataSize.ofMegabytes(64));

    // set default default size for rocks db
    unifiedConfig
        .getData()
        .getPrimaryStorage()
        .getRocksDb()
        .setMemoryAllocationStrategy(MemoryAllocationStrategy.BROKER);

    // Set processing defaults - enable consistency checks
    unifiedConfig.getProcessing().setEnablePreconditionsCheck(true);
    unifiedConfig.getProcessing().setEnableForeignKeyChecks(true);

    // Set dynamic ports via properties (these aren't in unified config yet)
    unifiedConfig
        .getCluster()
        .getNetwork()
        .getCommandApi()
        .setPort(SocketUtil.getNextAddress().getPort());
    unifiedConfig
        .getCluster()
        .getNetwork()
        .getInternalApi()
        .setPort(SocketUtil.getNextAddress().getPort());
    unifiedConfig.getApi().getGrpc().setPort(SocketUtil.getNextAddress().getPort());

    withSecondaryStorageType(SecondaryStorageType.none);
  }

  public TestStandaloneBroker withCamundaExporter(final String elasticSearchUrl) {
    return withCamundaExporter(elasticSearchUrl, null);
  }

  public TestStandaloneBroker withCamundaExporter(
      final String elasticSearchUrl, final String retentionPolicyName) {
    final var exporterConfigArgs =
        new HashMap<String, Object>(
            Map.of("connect", Map.of("url", elasticSearchUrl), "bulk", Map.of("size", 1)));
    if (retentionPolicyName != null) {
      exporterConfigArgs.put("retention", Map.of("enabled", true, "policyName", "test-policy"));
    }
    withExporter(
        "CamundaExporter",
        cfg -> {
          cfg.setClassName("io.camunda.exporter.CamundaExporter");
          cfg.setArgs(exporterConfigArgs);
        });
    final var searchEngineConnectProperties = new SearchEngineConnectProperties();
    searchEngineConnectProperties.setUrl(elasticSearchUrl);
    withBean(
        "searchEngineConnectProperties",
        searchEngineConnectProperties,
        SearchEngineConnectProperties.class);
    final var searchEngineIndexProperties = new SearchEngineIndexProperties();
    withBean(
        "searchEngineIndexProperties",
        searchEngineIndexProperties,
        SearchEngineIndexProperties.class);
    final var searchEngineRetentionProperties = new SearchEngineRetentionProperties();
    withBean(
        "searchEngineRetentionProperties",
        searchEngineRetentionProperties,
        SearchEngineRetentionProperties.class);
    // enable schema creation as ES is used in the current tests
    withCreateSchema(true);
    return this;
  }
}
