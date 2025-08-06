/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import static io.camunda.spring.utils.DatabaseTypeUtils.PROPERTY_CAMUNDA_DATABASE_TYPE;

import io.atomix.cluster.MemberId;
import io.camunda.application.Profile;
import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineConnectProperties;
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.authentication.config.AuthenticationProperties;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beans.LegacyBrokerBasedProperties;
import io.camunda.security.configuration.ConfiguredMappingRule;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.broker.BrokerModuleConfiguration;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.qa.util.actuator.BrokerHealthActuator;
import io.camunda.zeebe.qa.util.actuator.GatewayHealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
  private static final String RECORDING_EXPORTER_ID = "recordingExporter";
  private final LegacyBrokerBasedProperties config;
  private final CamundaSecurityProperties securityConfig;

  public TestStandaloneBroker() {
    super(
        BrokerModuleConfiguration.class,
        CommonsModuleConfiguration.class,
        UnifiedConfigurationHelper.class,
        UnifiedConfiguration.class);

    config = new LegacyBrokerBasedProperties();

    config.getNetwork().getCommandApi().setPort(SocketUtil.getNextAddress().getPort());
    config.getNetwork().getInternalApi().setPort(SocketUtil.getNextAddress().getPort());
    config.getGateway().getNetwork().setPort(SocketUtil.getNextAddress().getPort());

    // set a smaller default log segment size since we pre-allocate, which might be a lot in tests
    // for local development; also lower the watermarks for local testing
    config.getData().setLogSegmentSize(DataSize.ofMegabytes(16));
    config.getData().getDisk().getFreeSpace().setProcessing(DataSize.ofMegabytes(128));
    config.getData().getDisk().getFreeSpace().setReplication(DataSize.ofMegabytes(64));

    config.getExperimental().getConsistencyChecks().setEnableForeignKeyChecks(true);
    config.getExperimental().getConsistencyChecks().setEnablePreconditions(true);

    //noinspection resource
    withBean("config", config, LegacyBrokerBasedProperties.class)
        .withAdditionalProfile(Profile.BROKER);

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
      case COMMAND -> config.getNetwork().getCommandApi().getPort();
      case GATEWAY -> config.getGateway().getNetwork().getPort();
      case CLUSTER -> config.getNetwork().getInternalApi().getPort();
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
  protected SpringApplicationBuilder createSpringBuilder() {
    // because @ConditionalOnRestGatewayEnabled relies on the zeebe.broker.gateway.enable property,
    // we need to hook in at the last minute and set the property as it won't resolve from the
    // config bean
    withProperty("zeebe.broker.gateway.enable", config.getGateway().isEnable());
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
    return MemberId.from(String.valueOf(config.getCluster().getNodeId()));
  }

  @Override
  public String host() {
    return config.getNetwork().getHost();
  }

  @Override
  public HealthActuator healthActuator() {
    return brokerHealth();
  }

  @Override
  public boolean isGateway() {
    return config.getGateway().isEnable();
  }

  @Override
  public URI grpcAddress() {
    if (!isGateway()) {
      throw new IllegalStateException(
          "Expected to get the gateway address for this broker, but the embedded gateway is not enabled");
    }

    return TestStandaloneApplication.super.grpcAddress();
  }

  @Override
  public GatewayHealthActuator gatewayHealth() {
    throw new UnsupportedOperationException("Brokers do not support the gateway health indicators");
  }

  @Override
  public TestStandaloneBroker withGatewayConfig(final Consumer<GatewayCfg> modifier) {
    modifier.accept(config.getGateway());
    return this;
  }

  @Override
  public GatewayCfg gatewayConfig() {
    return config.getGateway();
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
      config.getExporters().remove(RECORDING_EXPORTER_ID);
      return this;
    }

    return withExporter(
        RECORDING_EXPORTER_ID, cfg -> cfg.setClassName(RecordingExporter.class.getName()));
  }

  /**
   * Adds or replaces a new exporter with the given ID. If it was already existing, the existing
   * configuration is passed to the modifier. If it's new, a blank configuration is passed.
   *
   * @param id the ID of the exporter
   * @param modifier a configuration function
   * @return itself for chaining
   */
  @Override
  public TestStandaloneBroker withExporter(final String id, final Consumer<ExporterCfg> modifier) {
    final var exporterConfig =
        config.getExporters().computeIfAbsent(id, ignored -> new ExporterCfg());
    modifier.accept(exporterConfig);

    return this;
  }

  /**
   * Modifies the broker configuration. Will still mutate the configuration if the broker is
   * started, but likely has no effect until it's restarted.
   */
  @Override
  public TestStandaloneBroker withBrokerConfig(
      final Consumer<LegacyBrokerBasedProperties> modifier) {
    modifier.accept(config);
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

  /** Returns the broker configuration */
  @Override
  public LegacyBrokerBasedProperties brokerConfig() {
    return config;
  }

  @Override
  public Optional<AuthenticationMethod> clientAuthenticationMethod() {
    return apiAuthenticationMethod();
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
    // enable schema creation as ES is used in the current tests
    withCreateSchema(true);
    return this;
  }

  public TestStandaloneBroker withRdbmsExporter() {
    withProperty(PROPERTY_CAMUNDA_DATABASE_TYPE, "rdbms");
    withProperty(
        "camunda.database.url",
        "jdbc:h2:mem:testdb+" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    withProperty("camunda.database.username", "sa");
    withProperty("camunda.database.password", "");
    withProperty("logging.level.io.camunda.db.rdbms", "DEBUG");
    withProperty("logging.level.org.mybatis", "DEBUG");
    withProperty("zeebe.broker.exporters.rdbms.args.flushInterval", "PT0S");
    withProperty("zeebe.broker.exporters.rdbms.args.history.defaultHistoryTTL", "PT2S");
    withProperty(
        "zeebe.broker.exporters.rdbms.args.history.defaultBatchOperationHistoryTTL", "PT2S");
    withProperty("zeebe.broker.exporters.rdbms.args.history.minHistoryCleanupInterval", "PT2S");
    withProperty("zeebe.broker.exporters.rdbms.args.history.maxHistoryCleanupInterval", "PT5S");
    withExporter("rdbms", cfg -> cfg.setClassName("-"));
    return this;
  }
}
