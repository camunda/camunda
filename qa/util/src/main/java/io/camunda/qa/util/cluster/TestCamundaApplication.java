/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import static io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker.DEFAULT_MAPPING_RULE_CLAIM_NAME;
import static io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker.DEFAULT_MAPPING_RULE_CLAIM_VALUE;
import static io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker.DEFAULT_MAPPING_RULE_ID;

import io.atomix.cluster.MemberId;
import io.camunda.application.Profile;
import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.application.initializers.WebappsConfigurationInitializer;
import io.camunda.authentication.config.AuthenticationProperties;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.configuration.beans.LegacyBrokerBasedProperties;
import io.camunda.identity.IdentityModuleConfiguration;
import io.camunda.operate.OperateModuleConfiguration;
import io.camunda.security.configuration.ConfiguredMappingRule;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.tasklist.TasklistModuleConfiguration;
import io.camunda.webapps.WebappsModuleConfiguration;
import io.camunda.zeebe.broker.BrokerModuleConfiguration;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.qa.util.actuator.BrokerHealthActuator;
import io.camunda.zeebe.qa.util.actuator.GatewayHealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.util.unit.DataSize;

/**
 * Represents an instance of the Camunda Application, without any extras we have in other test
 * applications (like ES containers, etc.), to keep it simple.
 */
@SuppressWarnings("UnusedReturnValue")
public final class TestCamundaApplication extends TestSpringApplication<TestCamundaApplication>
    implements TestGateway<TestCamundaApplication>,
        TestStandaloneApplication<TestCamundaApplication> {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestCamundaApplication.class);
  private final LegacyBrokerBasedProperties brokerProperties;
  private final CamundaSecurityProperties securityConfig;

  public TestCamundaApplication() {
    super(
        // Unified Configuration classes
        UnifiedConfiguration.class,
        TasklistPropertiesOverride.class,
        OperatePropertiesOverride.class,
        UnifiedConfigurationHelper.class,
        // ---
        CommonsModuleConfiguration.class,
        OperateModuleConfiguration.class,
        TasklistModuleConfiguration.class,
        IdentityModuleConfiguration.class,
        WebappsModuleConfiguration.class,
        BrokerModuleConfiguration.class,
        IndexTemplateDescriptorsConfigurator.class);

    brokerProperties = new LegacyBrokerBasedProperties();

    brokerProperties.getNetwork().getCommandApi().setPort(SocketUtil.getNextAddress().getPort());
    brokerProperties.getNetwork().getInternalApi().setPort(SocketUtil.getNextAddress().getPort());
    brokerProperties.getGateway().getNetwork().setPort(SocketUtil.getNextAddress().getPort());

    // set a smaller default log segment size since we pre-allocate, which might be a lot in tests
    // for local development; also lower the watermarks for local testing
    brokerProperties.getData().setLogSegmentSize(DataSize.ofMegabytes(16));
    brokerProperties.getData().getDisk().getFreeSpace().setProcessing(DataSize.ofMegabytes(128));
    brokerProperties.getData().getDisk().getFreeSpace().setReplication(DataSize.ofMegabytes(64));

    brokerProperties.getExperimental().getConsistencyChecks().setEnableForeignKeyChecks(true);
    brokerProperties.getExperimental().getConsistencyChecks().setEnablePreconditions(true);

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

    //noinspection resource
    withBean("config", brokerProperties, LegacyBrokerBasedProperties.class)
        .withBean("security-config", securityConfig, CamundaSecurityProperties.class)
        .withProperty(
            AuthenticationProperties.API_UNPROTECTED,
            securityConfig.getAuthentication().getUnprotectedApi())
        .withProperty(
            "camunda.security.authorizations.enabled",
            securityConfig.getAuthorizations().isEnabled())
        .withAdditionalProfile(Profile.BROKER)
        .withAdditionalProfile(Profile.OPERATE)
        .withAdditionalProfile(Profile.TASKLIST)
        .withAdditionalProfile(Profile.IDENTITY)
        .withAdditionalInitializer(new WebappsConfigurationInitializer());
  }

  @Override
  public TestCamundaApplication stop() {
    // clean up ES/OS indices
    LOGGER.info("Stopping standalone camunda test...");
    return super.stop();
  }

  @Override
  public int mappedPort(final TestZeebePort port) {
    return switch (port) {
      case COMMAND -> brokerProperties.getNetwork().getCommandApi().getPort();
      case GATEWAY -> brokerProperties.getGateway().getNetwork().getPort();
      case CLUSTER -> brokerProperties.getNetwork().getInternalApi().getPort();
      default -> super.mappedPort(port);
    };
  }

  @Override
  public TestCamundaApplication withProperty(final String key, final Object value) {
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
    withProperty("zeebe.broker.gateway.enable", brokerProperties.getGateway().isEnable());
    return super.createSpringBuilder();
  }

  public TestCamundaApplication withAuthorizationsEnabled() {
    // when using authorizations, api authentication needs to be enforced too
    withAuthenticatedAccess();
    withProperty("camunda.security.authorizations.enabled", true);
    return withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true));
  }

  public TestCamundaApplication withAuthorizationsDisabled() {
    withUnauthenticatedAccess();
    return withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(false));
  }

  public TestCamundaApplication withMultiTenancyEnabled() {
    withAuthenticatedAccess();
    withProperty("camunda.security.multiTenancy.checksEnabled", true);
    return withSecurityConfig(cfg -> cfg.getMultiTenancy().setChecksEnabled(true));
  }

  public TestCamundaApplication withMultiTenancyDisabled() {
    withAuthenticatedAccess();
    withProperty("camunda.security.multiTenancy.checksEnabled", false);
    return withSecurityConfig(cfg -> cfg.getMultiTenancy().setChecksEnabled(false));
  }

  @Override
  public TestCamundaApplication self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    return MemberId.from(String.valueOf(brokerProperties.getCluster().getNodeId()));
  }

  @Override
  public String host() {
    return brokerProperties.getNetwork().getHost();
  }

  @Override
  public HealthActuator healthActuator() {
    return BrokerHealthActuator.of(monitoringUri().toString());
  }

  @Override
  public boolean isGateway() {
    return brokerProperties.getGateway().isEnable();
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
  public TestCamundaApplication withGatewayConfig(final Consumer<GatewayCfg> modifier) {
    modifier.accept(brokerProperties.getGateway());
    return this;
  }

  @Override
  public GatewayCfg gatewayConfig() {
    return brokerProperties.getGateway();
  }

  /**
   * Registers or replaces a new exporter with the given ID. If it was already registered, the
   * existing configuration is passed to the modifier.
   *
   * @param id the ID of the exporter
   * @param modifier a configuration function
   * @return itself for chaining
   */
  @Override
  public TestCamundaApplication withExporter(
      final String id, final Consumer<ExporterCfg> modifier) {
    final var cfg = new ExporterCfg();
    modifier.accept(cfg);
    brokerProperties.getExporters().put(id, cfg);
    return this;
  }

  /**
   * Modifies the broker configuration. Will still mutate the configuration if the broker is
   * started, but likely has no effect until it's restarted.
   */
  @Override
  public TestCamundaApplication withBrokerConfig(
      final Consumer<LegacyBrokerBasedProperties> modifier) {
    modifier.accept(brokerProperties);
    return this;
  }

  /**
   * Modifies the security configuration. Will still mutate the configuration if the broker is
   * started, but likely has no effect until it's restarted.
   */
  @Override
  public TestCamundaApplication withSecurityConfig(
      final Consumer<CamundaSecurityProperties> modifier) {
    modifier.accept(securityConfig);
    return this;
  }

  @Override
  public LegacyBrokerBasedProperties brokerConfig() {
    return brokerProperties;
  }

  @Override
  public Optional<AuthenticationMethod> clientAuthenticationMethod() {
    return apiAuthenticationMethod();
  }

  public TestCamundaApplication updateExporterArgs(
      final String id, final Consumer<Map<String, Object>> modifier) {
    final var exporterCfg = brokerProperties.getExporters().get(id);
    final var argsCopy = deepCopy(exporterCfg.getArgs());
    modifier.accept(argsCopy);
    exporterCfg.setArgs(argsCopy);
    return this;
  }

  private Map<String, Object> deepCopy(final Map<String, Object> map) {
    final Map<String, Object> copy = new HashMap<>();
    for (final Map.Entry<String, Object> entry : map.entrySet()) {
      if (entry.getValue() instanceof final Map m) {
        copy.put(entry.getKey(), deepCopy(m));
      } else {
        copy.put(entry.getKey(), entry.getValue());
      }
    }
    return copy;
  }

  public TestRestOperateClient newOperateClient() {
    return new TestRestOperateClient(restAddress());
  }

  public TestRestOperateClient newOperateClient(final String username, final String password) {
    return new TestRestOperateClient(restAddress(), username, password);
  }

  public TestRestTasklistClient newTasklistClient() {
    return new TestRestTasklistClient(restAddress());
  }

  public TestWebappClient newWebappClient() {
    return new TestWebappClient(restAddress());
  }
}
