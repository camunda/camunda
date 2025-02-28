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
import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.application.commons.configuration.GatewayBasedConfiguration.GatewayBasedProperties;
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.authentication.config.AuthenticationProperties;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.zeebe.gateway.GatewayModuleConfiguration;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.util.function.Consumer;

/** Encapsulates an instance of the {@link GatewayModuleConfiguration} Spring application. */
public final class TestStandaloneGateway extends TestSpringApplication<TestStandaloneGateway>
    implements TestGateway<TestStandaloneGateway> {
  private final GatewayBasedProperties config;
  private final CamundaSecurityProperties securityConfig;

  public TestStandaloneGateway() {
    super(GatewayModuleConfiguration.class, CommonsModuleConfiguration.class);
    config = new GatewayBasedProperties();

    config.getNetwork().setHost("0.0.0.0");
    config.getNetwork().setPort(SocketUtil.getNextAddress().getPort());
    config.getCluster().setPort(SocketUtil.getNextAddress().getPort());

    //noinspection resource
    withBean("config", config, GatewayBasedProperties.class).withAdditionalProfile(Profile.GATEWAY);

    securityConfig = new CamundaSecurityProperties();
    securityConfig
        .getInitialization()
        .getUsers()
        .add(
            new ConfiguredUser(
                InitializationConfiguration.DEFAULT_USER_USERNAME,
                InitializationConfiguration.DEFAULT_USER_PASSWORD,
                InitializationConfiguration.DEFAULT_USER_NAME,
                InitializationConfiguration.DEFAULT_USER_EMAIL));
    withBean("securityConfig", securityConfig, CamundaSecurityProperties.class);
    // by default, we don't want to create the schema as ES/OS containers may not be used in the
    // current test
    withCreateSchema(false);
  }

  @Override
  public TestStandaloneGateway self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    return MemberId.from(config.getCluster().getMemberId());
  }

  @Override
  public String host() {
    return config.getNetwork().getHost();
  }

  @Override
  public boolean isGateway() {
    return true;
  }

  @Override
  public int mappedPort(final TestZeebePort port) {
    return switch (port) {
      case GATEWAY -> config.getNetwork().getPort();
      case CLUSTER -> config.getCluster().getPort();
      default -> super.mappedPort(port);
    };
  }

  @Override
  public TestStandaloneGateway withProperty(final String key, final Object value) {
    // Since the security config is not constructed from the properties, we need to manually update
    // it when we override a property.
    AuthenticationProperties.applyToSecurityConfig(securityConfig, key, value);
    return super.withProperty(key, value);
  }

  /**
   * Modifies the security configuration. Will still mutate the configuration if the gateway is
   * started, but likely has no effect until it's restarted.
   */
  public TestStandaloneGateway withSecurityConfig(
      final Consumer<CamundaSecurityProperties> modifier) {
    modifier.accept(securityConfig);
    return this;
  }

  public TestStandaloneGateway withAuthorizationsEnabled() {
    // when using authorizations, api authentication needs to be enforced too
    withAuthenticatedAccess();
    return withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true));
  }

  @Override
  public TestStandaloneGateway withGatewayConfig(final Consumer<GatewayCfg> modifier) {
    modifier.accept(config);
    return self();
  }

  @Override
  public GatewayCfg gatewayConfig() {
    return config;
  }
}
